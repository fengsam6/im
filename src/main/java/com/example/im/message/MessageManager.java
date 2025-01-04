package com.example.im.message;

import com.example.im.protocol.Message;
import com.example.im.config.WebSocketHandler;
import com.example.im.service.ChatMessageService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageManager {
    private final Map<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    private final List<String> messageQueue = new ArrayList<>();
    private final int retryCount = 3;
    private final long retryInterval = 3000;
    private final int batchSize = 10;
    private final List<String> pendingAcks = new ArrayList<>();

    @Autowired
    @Lazy
    private WebSocketHandler webSocketHandler;

    @Autowired
    private ChatMessageService chatMessageService;

    public String sendMessage(Message message) {
        if (message.getMessageId() == null) {
            message.setMessageId(generateMessageId());
        }
        message.setTimestamp(System.currentTimeMillis());
        message.setNeedAck(true);
        message.setStatus(Message.Status.SENDING);

        log.debug("Sending message: {}", message);

        // 存储消息到待确认列表
        pendingMessages.put(message.getMessageId(), new PendingMessage(
                message,
                0,
                System.currentTimeMillis()
        ));

        try {
            // 发送消息
            webSocketHandler.sendMessage(message);

            // 启动重试计时器
            scheduleRetry(message.getMessageId());

            return message.getMessageId();
        } catch (Exception e) {
            log.error("Failed to send message: {}", message.getMessageId(), e);
            pendingMessages.remove(message.getMessageId());
            webSocketHandler.updateMessageStatus(message.getMessageId(), Message.Status.FAILED);
            return null;
        }
    }

    public void handleAck(Message ackMessage) {
        log.debug("Handling ACK message: {}", ackMessage);

        if (ackMessage.getBatchAckMessageIds() != null) {
            // 处理批量确认
            ackMessage.getBatchAckMessageIds().forEach(this::confirmMessage);
        } else if (ackMessage.getAckMessageId() != null) {
            // 处理单条确认
            confirmMessage(ackMessage.getAckMessageId());
        }
    }

    private void confirmMessage(String messageId) {
        log.debug("Confirming message: {}", messageId);

        PendingMessage pendingMessage = pendingMessages.remove(messageId);
        if (pendingMessage != null) {
            // 更新消息状态
            webSocketHandler.updateMessageStatus(messageId, Message.Status.DELIVERED);
        }
    }

    public void handleIncomingMessage(Message message) {
        log.debug("Handling incoming message: {}", message);

        // 显示接收到的消息
        webSocketHandler.handleIncomingMessage(message);

        if (message.isNeedAck()) {
            // 将确认消息添加到队列
            pendingAcks.add(message.getMessageId());

            // 如果队列达到批量大小或者是第一个确认，启动批量发送
            if (pendingAcks.size() >= batchSize) {
                sendBatchAck();
            } else if (pendingAcks.size() == 1) {
                scheduleAckSend();
            }
        }
    }

    private void scheduleRetry(String messageId) {
        PendingMessage pendingMessage = pendingMessages.get(messageId);
        if (pendingMessage == null) return;

        new Thread(() -> {
            try {
                Thread.sleep(retryInterval);
                if (pendingMessages.containsKey(messageId)) {
                    if (pendingMessage.getRetries() < retryCount) {
                        pendingMessage.setRetries(pendingMessage.getRetries() + 1);
                        log.debug("Retrying message: {} (attempt {})", messageId, pendingMessage.getRetries());
                        webSocketHandler.sendMessage(pendingMessage.getMessage());
                        scheduleRetry(messageId);
                        webSocketHandler.updateMessageStatus(messageId, Message.Status.SENDING);
                    } else {
                        log.warn("Message {} failed after {} retries", messageId, retryCount);
                        pendingMessages.remove(messageId);
                        webSocketHandler.updateMessageStatus(messageId, Message.Status.FAILED);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Retry interrupted for message: {}", messageId, e);
            } catch (Exception e) {
                log.error("Error retrying message: {}", messageId, e);
                pendingMessages.remove(messageId);
                webSocketHandler.updateMessageStatus(messageId, Message.Status.FAILED);
            }
        }).start();
    }

    private void scheduleAckSend() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                sendBatchAck();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("ACK send interrupted", e);
            }
        }).start();
    }

    private void sendBatchAck() {
        if (pendingAcks.isEmpty()) return;

        Message ackMessage = new Message();
        ackMessage.setType(Message.Type.BATCH_ACK);
        ackMessage.setBatchAckMessageIds(new ArrayList<>(pendingAcks));
        ackMessage.setTimestamp(System.currentTimeMillis());

        try {
            webSocketHandler.sendMessage(ackMessage);
            pendingAcks.clear();
        } catch (Exception e) {
            log.error("Failed to send batch ACK", e);
        }
    }

    private String generateMessageId() {
        return System.currentTimeMillis() + "-" + Math.random();
    }

    @Data
    @AllArgsConstructor
    private static class PendingMessage {
        private Message message;
        private int retries;
        private long timestamp;
    }
} 