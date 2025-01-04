package com.example.im.config;

import com.example.im.service.ChatMessageService;
import com.example.im.message.MessageManager;
import com.example.im.protocol.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;

@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private MessageManager messageManager;
    
    @Autowired
    private ChatMessageService chatMessageService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = getUsername(session);
        sessions.put(username, session);
        
        // 广播用户上线消息
        broadcastUserStatus(username, true);
        
        // 发送当前在线用户列表
        sendUserList();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = getUsername(session);
        sessions.remove(username);
        
        // 广播用户下线消息
        broadcastUserStatus(username, false);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            Message message = objectMapper.readValue(textMessage.getPayload(), Message.class);
            message.setFrom(getUsername(session));
            
            switch (message.getType()) {
                case CHAT:
                    handleChatMessage(session, message);
                    break;
                case ACK:
                    handleAckMessage(message);
                    break;
                case BATCH_ACK:
                    handleBatchAckMessage(message);
                    break;
                case HEARTBEAT:
                    handleHeartbeat(session);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

    public void sendMessage(Message message) {
        try {
            WebSocketSession recipientSession = sessions.get(message.getTo());
            if (recipientSession != null && recipientSession.isOpen()) {
                String messageJson = objectMapper.writeValueAsString(message);
                synchronized (recipientSession) {
                    recipientSession.sendMessage(new TextMessage(messageJson));
                }
            }
        } catch (IOException e) {
            log.error("Error sending message", e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    public void handleIncomingMessage(Message message) {
        messageManager.handleIncomingMessage(message);
    }

    public void updateMessageStatus(String messageId, Message.Status status) {
        try {
            Message statusMessage = new Message();
            statusMessage.setType(Message.Type.ACK);
            statusMessage.setAckMessageId(messageId);
            statusMessage.setStatus(status);
            
            if (statusMessage.getTo() == null) {
                log.warn("Cannot update message status: recipient is null");
                return;
            }
            
            WebSocketSession senderSession = sessions.get(statusMessage.getTo());
            if (senderSession != null && senderSession.isOpen()) {
                String messageJson = objectMapper.writeValueAsString(statusMessage);
                synchronized (senderSession) {
                    senderSession.sendMessage(new TextMessage(messageJson));
                }
            }
        } catch (IOException e) {
            log.error("Error updating message status", e);
        }
    }

    private void handleChatMessage(WebSocketSession session, Message message) {
        try {
            // 设置消息状态为发送中
            message.setStatus(Message.Status.SENDING);
            
            // 验证消息的必要字段
            if (message.getTo() == null || message.getFrom() == null) {
                log.warn("Invalid chat message: missing to/from fields");
                return;
            }
            
            // 发送消息给接收者
            WebSocketSession recipientSession = sessions.get(message.getTo());
            if (recipientSession != null && recipientSession.isOpen()) {
                String messageJson = objectMapper.writeValueAsString(message);
                synchronized (recipientSession) {
                    recipientSession.sendMessage(new TextMessage(messageJson));
                }
                
                // 更新消息状态为已发送，并通知发送者
                message.setStatus(Message.Status.SENT);
                sendAckToSender(message.getFrom(), message.getMessageId(), Message.Status.SENT);
            } else {
                // 接收者不在线，标记消息为发送失败
                message.setStatus(Message.Status.FAILED);
                sendAckToSender(message.getFrom(), message.getMessageId(), Message.Status.FAILED);
            }
        } catch (IOException e) {
            log.error("Error handling chat message", e);
            message.setStatus(Message.Status.FAILED);
            if (message.getFrom() != null) {
                sendAckToSender(message.getFrom(), message.getMessageId(), Message.Status.FAILED);
            }
        }
    }

    private void handleAckMessage(Message message) {
        try {
            // 验证消息的必要字段
            if (message.getTo() == null) {
                log.warn("Invalid ACK message: missing recipient");
                return;
            }
            
            // 转发确认消息给原始发送者
            WebSocketSession senderSession = sessions.get(message.getTo());
            if (senderSession != null && senderSession.isOpen()) {
                String messageJson = objectMapper.writeValueAsString(message);
                synchronized (senderSession) {
                    senderSession.sendMessage(new TextMessage(messageJson));
                }
            }
        } catch (IOException e) {
            log.error("Error handling ACK message", e);
        }
    }

    private void sendAckToSender(String to, String messageId, Message.Status status) {
        try {
            // 验证必要参数
            if (to == null || messageId == null) {
                log.warn("Cannot send ACK: missing to/messageId");
                return;
            }

            Message ackMessage = new Message();
            ackMessage.setType(Message.Type.ACK);
            ackMessage.setAckMessageId(messageId);
            ackMessage.setStatus(status);
            ackMessage.setTimestamp(System.currentTimeMillis());
            ackMessage.setTo(to);

            WebSocketSession senderSession = sessions.get(to);
            if (senderSession != null && senderSession.isOpen()) {
                String messageJson = objectMapper.writeValueAsString(ackMessage);
                synchronized (senderSession) {
                    senderSession.sendMessage(new TextMessage(messageJson));
                }
            }
        } catch (IOException e) {
            log.error("Error sending ACK to sender", e);
        }
    }

    private void handleBatchAckMessage(Message message) {
        try {
            // 转发批量确认消息给原始发送者
            WebSocketSession senderSession = sessions.get(message.getTo());
            if (senderSession != null && senderSession.isOpen()) {
                String messageJson = objectMapper.writeValueAsString(message);
                synchronized (senderSession) {
                    senderSession.sendMessage(new TextMessage(messageJson));
                }
            }
        } catch (IOException e) {
            log.error("Error handling batch ACK message", e);
        }
    }

    private void handleHeartbeat(WebSocketSession session) {
        try {
            Message heartbeatResponse = new Message();
            heartbeatResponse.setType(Message.Type.HEARTBEAT);
            heartbeatResponse.setTimestamp(System.currentTimeMillis());
            
            String messageJson = objectMapper.writeValueAsString(heartbeatResponse);
            synchronized (session) {
                session.sendMessage(new TextMessage(messageJson));
            }
        } catch (IOException e) {
            log.error("Error sending heartbeat response", e);
        }
    }

    private void broadcastUserStatus(String username, boolean isOnline) {
        Message statusMessage = new Message();
        statusMessage.setType(isOnline ? Message.Type.LOGIN : Message.Type.LOGOUT);
        statusMessage.setFrom(username);
        statusMessage.setTimestamp(System.currentTimeMillis());
        
        broadcast(statusMessage);
    }

    private void sendUserList() {
        Message userListMessage = new Message();
        userListMessage.setType(Message.Type.USER_LIST);
        userListMessage.setUsers(new ArrayList<>(sessions.keySet()));
        userListMessage.setTimestamp(System.currentTimeMillis());
        
        broadcast(userListMessage);
    }

    private void broadcast(Message message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(messageJson);
            
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error broadcasting message", e);
        }
    }

    private String getUsername(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.startsWith("username=")) {
                String username = query.substring("username=".length());
                return URLDecoder.decode(username, StandardCharsets.UTF_8.name());
            }
            throw new IllegalArgumentException("Username parameter not found in WebSocket URL");
        } catch (Exception e) {
            throw new RuntimeException("Error getting username from session", e);
        }
    }
} 