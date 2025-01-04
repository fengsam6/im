package com.example.im.message;

import com.example.im.channel.IMChannel;
import com.example.im.protocol.Message;
import com.example.im.service.ChatMessageService;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MessageManager {
    private static final Logger logger = LoggerFactory.getLogger(MessageManager.class);
    private static final Gson gson = new Gson();
    
    // 待确认的消息队列
    private final Map<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    
    // 重传调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // 消息持久化队列
    private final BlockingQueue<Message> persistQueue = new LinkedBlockingQueue<>();
    
    // 消息持久化线程
    private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor();

    // 修改重传任务的配置
    private static final int RETRY_DELAY = 3; // 3秒后开始重传
    private static final int MAX_RETRIES = 3; // 最大重试次数
    private static final int BATCH_SIZE = 10;  // 批量确认大小
    private static final long BATCH_TIMEOUT = 5000;  // 批量确认超时时间(ms)
    private final Map<String, List<String>> pendingAcks = new ConcurrentHashMap<>(); // 用户待确认消息队列

    @Autowired
    private ChatMessageService chatMessageService;

    public MessageManager() {
        // 启动消息持久化线程
        startPersistThread();
    }

    // 发送消息
    public void sendMessage(IMChannel channel, Message message) {
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(System.currentTimeMillis());
        message.setNeedAck(true);

        PendingMessage pendingMessage = new PendingMessage(message, channel);
        pendingMessages.put(message.getMessageId(), pendingMessage);

        // 添加到用户的待确认队列
        pendingAcks.computeIfAbsent(message.getTo(), k -> new CopyOnWriteArrayList<>())
                  .add(message.getMessageId());

        channel.writeAndFlush(gson.toJson(message));
        scheduleRetry(pendingMessage);
        persistQueue.offer(message);

        // 保存消息到数据库
        try {
            chatMessageService.saveMessage(message);
        } catch (Exception e) {
            logger.error("Error saving message to database", e);
        }

        // 检查是否需要触发批量确认
        checkBatchAck(message.getTo());
    }

    // 检查是否需要触发批量确认
    private void checkBatchAck(String username) {
        List<String> userPendingAcks = pendingAcks.get(username);
        if (userPendingAcks != null && userPendingAcks.size() >= BATCH_SIZE) {
            triggerBatchAck(username);
        }
    }

    // 触发批量确认
    private void triggerBatchAck(String username) {
        List<String> userPendingAcks = pendingAcks.get(username);
        if (userPendingAcks == null || userPendingAcks.isEmpty()) {
            return;
        }

        List<String> batchAcks = new ArrayList<>(userPendingAcks);
        userPendingAcks.clear();

        Message batchAckMessage = new Message();
        batchAckMessage.setType(Message.MessageType.BATCH_ACK);
        batchAckMessage.setBatchAckMessageIds(batchAcks);
        batchAckMessage.setTimestamp(System.currentTimeMillis());

        // 通知消息发送者
        for (String messageId : batchAcks) {
            PendingMessage pendingMessage = pendingMessages.get(messageId);
            if (pendingMessage != null) {
                pendingMessage.getMessage().setStatus(Message.MessageStatus.DELIVERED);
                pendingMessage.getChannel().writeAndFlush(gson.toJson(batchAckMessage));
            }
        }
    }

    // 处理批量确认
    public void handleBatchAck(Message batchAckMessage) {
        List<String> messageIds = batchAckMessage.getBatchAckMessageIds();
        if (messageIds != null) {
            for (String messageId : messageIds) {
                PendingMessage pendingMessage = pendingMessages.remove(messageId);
                if (pendingMessage != null) {
                    pendingMessage.cancelRetry();
                    logger.info("Message {} has been acknowledged in batch", messageId);
                }
            }
        }
    }

    // 启动定时批量确认任务
    @PostConstruct
    public void startBatchAckScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            pendingAcks.forEach((username, acks) -> {
                if (!acks.isEmpty()) {
                    triggerBatchAck(username);
                }
            });
        }, BATCH_TIMEOUT, BATCH_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    // 处理消息确认
    public void handleAck(Message ackMessage) {
        String messageId = ackMessage.getAckMessageId();
        PendingMessage pendingMessage = pendingMessages.remove(messageId);
        if (pendingMessage != null) {
            // 取消重传任务
            pendingMessage.cancelRetry();
            logger.info("Message {} has been acknowledged", messageId);
        }
    }

    // 安排重传任务
    private void scheduleRetry(PendingMessage pendingMessage) {
        AtomicInteger retryCount = new AtomicInteger(0);
        
        ScheduledFuture<?> retryFuture = scheduler.scheduleWithFixedDelay(() -> {
            if (pendingMessages.containsKey(pendingMessage.getMessage().getMessageId())) {
                if (retryCount.incrementAndGet() > MAX_RETRIES) {
                    // 超过最大重试次数
                    logger.error("Message {} failed after {} retries", 
                        pendingMessage.getMessage().getMessageId(), MAX_RETRIES);
                    pendingMessages.remove(pendingMessage.getMessage().getMessageId());
                    pendingMessage.cancelRetry();
                    return;
                }

                IMChannel channel = pendingMessage.getChannel();
                if (channel != null && channel.isActive()) {
                    logger.info("Retrying message: {} (attempt: {})", 
                        pendingMessage.getMessage().getMessageId(), retryCount.get());
                    channel.writeAndFlush(gson.toJson(pendingMessage.getMessage()));
                } else {
                    // 通道已关闭，取消重试
                    logger.warn("Channel is inactive, canceling retry for message: {}", 
                        pendingMessage.getMessage().getMessageId());
                    pendingMessages.remove(pendingMessage.getMessage().getMessageId());
                    pendingMessage.cancelRetry();
                }
            }
        }, RETRY_DELAY, RETRY_DELAY, TimeUnit.SECONDS);

        pendingMessage.setRetryFuture(retryFuture);
    }

    // 启动消息持久化线程
    private void startPersistThread() {
        persistExecutor.submit(() -> {
            while (true) {
                try {
                    Message message = persistQueue.take();
                    // TODO: 实现具体的消息持久化逻辑，例如写入数据库
                    logger.info("Persisting message: {}", message.getMessageId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error persisting message", e);
                }
            }
        });
    }

    // 关闭资源
    public void shutdown() {
        scheduler.shutdown();
        persistExecutor.shutdown();
    }

    // 待确认消息的封装类
    private static class PendingMessage {
        private final Message message;
        private final IMChannel channel;
        private ScheduledFuture<?> retryFuture;

        public PendingMessage(Message message, IMChannel channel) {
            this.message = message;
            this.channel = channel;
        }

        public Message getMessage() {
            return message;
        }

        public IMChannel getChannel() {
            return channel;
        }

        public void setRetryFuture(ScheduledFuture<?> retryFuture) {
            this.retryFuture = retryFuture;
        }

        public void cancelRetry() {
            if (retryFuture != null) {
                retryFuture.cancel(false);
            }
        }
    }
} 