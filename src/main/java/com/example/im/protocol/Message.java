package com.example.im.protocol;

import lombok.Data;

import java.util.List;

@Data
public class Message {
    private String messageId;
    private Type type;
    private String from;
    private String to;
    private String content;
    private long timestamp;
    private Status status;
    private boolean needAck;
    private String ackMessageId;
    private List<String> batchAckMessageIds;
    private List<String> users;

    public enum Type {
        CHAT,
        ACK,
        BATCH_ACK,
        LOGIN,
        LOGOUT,
        USER_LIST,
        READ_RECEIPT,
        HEARTBEAT
    }

    public enum Status {
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED
    }
} 