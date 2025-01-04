package com.example.im.protocol;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Message {
    @SerializedName("messageId")
    private String messageId;  // 消息唯一ID
    @SerializedName("from")
    private String from;
    @SerializedName("to")
    private String to;
    @SerializedName("content")
    private String content;
    @SerializedName("type")
    private MessageType type;
    @SerializedName("messageType")
    private String messageType;
    @SerializedName("timestamp")
    private long timestamp;    // 消息时间戳
    @SerializedName("needAck")
    private boolean needAck;   // 是否需要确认
    @SerializedName("ackMessageId")
    private String ackMessageId; // 确认的消息ID
    @SerializedName("status")
    private MessageStatus status;  // 消息状态
    @SerializedName("batchAckMessageIds")
    private List<String> batchAckMessageIds; // 批量确认的消息ID列表
    @SerializedName("maxMessageId")
    private long maxMessageId;  // 确认所有小于此ID的消息
    @SerializedName("users")
    private List<String> users;  // 添加用户列表字段

    public enum MessageType {
        CHAT,
        LOGIN,
        LOGOUT,
        ACK,
        BATCH_ACK,  // 批量确认类型
        HEARTBEAT,
        HEARTBEAT_RESPONSE,
        READ_RECEIPT,
        USER_LIST,
        ERROR  // 添加错误消息类型
    }

    public enum MessageStatus {
        SENDING,    // 发送中
        SENT,      // 已发送
        DELIVERED, // 已送达
        READ       // 已读
    }

    // Getters and Setters
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isNeedAck() {
        return needAck;
    }

    public void setNeedAck(boolean needAck) {
        this.needAck = needAck;
    }

    public String getAckMessageId() {
        return ackMessageId;
    }

    public void setAckMessageId(String ackMessageId) {
        this.ackMessageId = ackMessageId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public List<String> getBatchAckMessageIds() {
        return batchAckMessageIds;
    }

    public void setBatchAckMessageIds(List<String> batchAckMessageIds) {
        this.batchAckMessageIds = batchAckMessageIds;
    }

    public long getMaxMessageId() {
        return maxMessageId;
    }

    public void setMaxMessageId(long maxMessageId) {
        this.maxMessageId = maxMessageId;
    }

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }
} 