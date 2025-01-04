package com.example.im.config;

import com.example.im.service.ChatMessageService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.im.channel.IMChannel;
import com.example.im.message.MessageManager;
import com.example.im.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class WebSocketHandler extends TextWebSocketHandler {
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    
    @Autowired
    private ChatMessageService chatMessageService;

    // 简化的会话管理
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = getUsername(session);
        
        // 处理已存在的会话
        WebSocketSession existingSession = sessions.get(username);
        if (existingSession != null && existingSession.isOpen()) {
            try {
                existingSession.close();
            } catch (IOException e) {
                logger.error("Error closing existing session", e);
            }
        }
        
        // 保存新会话
        sessions.put(username, session);
        
        // 发送用户列表
        Message userListMessage = new Message();
        userListMessage.setType(Message.MessageType.USER_LIST);
        userListMessage.setUsers(new ArrayList<>(sessions.keySet()));
        sendMessage(session, userListMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            String username = getUsername(session);
            sessions.remove(username);

            // 广播用户离线消息
            Message logoutMessage = new Message();
            logoutMessage.setType(Message.MessageType.LOGOUT);
            logoutMessage.setFrom(username);
            broadcastMessage(logoutMessage);
        } catch (Exception e) {
            logger.error("Error in afterConnectionClosed", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            String payload = textMessage.getPayload();
            logger.debug("Received message: {}", payload);

            String username = getUsername(session);
            Message msg = gson.fromJson(payload, Message.class);
            
            if (msg == null) {
                logger.error("Failed to parse message: {}", payload);
                return;
            }

            msg.setFrom(username);
            logger.debug("Processed message: {}", gson.toJson(msg));

            switch (msg.getType()) {
                case CHAT:
                    handleChatMessage(session, msg);
                    break;
                case LOGIN:
                    handleLoginMessage(username, msg);
                    break;
                case LOGOUT:
                    handleLogoutMessage(username);
                    break;
                case READ_RECEIPT:
                    handleReadReceipt(msg);
                    break;
                case ACK:
                    handleAckMessage(msg);
                    break;
                default:
                    logger.warn("Unknown message type: {}", msg.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling message: {}", e.getMessage(), e);
        }
    }

    private void handleChatMessage(WebSocketSession senderSession, Message message) {
        try {
            WebSocketSession recipientSession = sessions.get(message.getTo());
            if (recipientSession != null && recipientSession.isOpen()) {
                message.setTimestamp(System.currentTimeMillis());
                
                sendMessage(recipientSession, message);
                
                Message ack = new Message();
                ack.setType(Message.MessageType.ACK);
                ack.setAckMessageId(message.getMessageId());
                ack.setFrom(message.getTo());
                ack.setTo(message.getFrom());
                ack.setTimestamp(System.currentTimeMillis());
                sendMessage(senderSession, ack);
                
                try {
                    chatMessageService.saveMessage(message);
                } catch (Exception e) {
                    logger.error("Error saving message: {}", e.getMessage(), e);
                }
            } else {
                Message offline = new Message();
                offline.setType(Message.MessageType.ERROR);
                offline.setContent("User is offline");
                offline.setTimestamp(System.currentTimeMillis());
                sendMessage(senderSession, offline);
            }
        } catch (Exception e) {
            logger.error("Error handling chat message: {}", e.getMessage(), e);
            
            Message error = new Message();
            error.setType(Message.MessageType.ERROR);
            error.setContent("Failed to send message: " + e.getMessage());
            error.setTimestamp(System.currentTimeMillis());
            sendMessage(senderSession, error);
        }
    }

    private void handleLoginMessage(String username, Message message) {
        message.setFrom(username);
        broadcastMessage(message);
    }

    private void handleLogoutMessage(String username) {
        sessions.remove(username);
        Message logoutMessage = new Message();
        logoutMessage.setType(Message.MessageType.LOGOUT);
        logoutMessage.setFrom(username);
        broadcastMessage(logoutMessage);
    }

    private void handleReadReceipt(Message message) {
        try {
            WebSocketSession senderSession = sessions.get(message.getTo());
            if (senderSession != null && senderSession.isOpen()) {
                sendMessage(senderSession, message);
            }
        } catch (Exception e) {
            logger.error("Error handling read receipt", e);
        }
    }

    private void handleAckMessage(Message message) {
        try {
            WebSocketSession senderSession = sessions.get(message.getTo());
            if (senderSession != null && senderSession.isOpen()) {
                sendMessage(senderSession, message);
            }
        } catch (Exception e) {
            logger.error("Error handling ack message", e);
        }
    }

    private void sendMessage(WebSocketSession session, Message message) {
        if (session != null && session.isOpen()) {
            try {
                String messageJson = gson.toJson(message);
                logger.debug("Sending message: {}", messageJson);

                synchronized (session) {
                    TextMessage textMessage = new TextMessage(messageJson);
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                logger.error("Error sending message: {}", e.getMessage(), e);
            }
        }
    }

    private void broadcastMessage(Message message) {
        String messageJson = gson.toJson(message);
        TextMessage textMessage = new TextMessage(messageJson);
        
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    logger.error("Error broadcasting message", e);
                }
            }
        });
    }

    private String getUsername(WebSocketSession session) {
        try {
            String username = session.getUri().getQuery().split("=")[1];
            return URLDecoder.decode(username, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException("Error getting username from session", e);
        }
    }
} 