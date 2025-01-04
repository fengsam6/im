package com.example.im.entity;

import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    private String id;

    @Column(name = "message_id", unique = true)
    private String messageId;

    @Column(name = "from_user")
    private String from;

    @Column(name = "to_user")
    private String to;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private Long timestamp;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }

    public enum Status {
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED
    }
} 