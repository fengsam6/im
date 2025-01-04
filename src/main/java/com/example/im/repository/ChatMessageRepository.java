package com.example.im.repository;

import com.example.im.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    Optional<ChatMessage> findByMessageId(String messageId);

    @Query(value = "SELECT m FROM ChatMessage m WHERE " +
           "(m.from = :user1 AND m.to = :user2) OR " +
           "(m.from = :user2 AND m.to = :user1) " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessage> findChatHistory(
        @Param("user1") String user1,
        @Param("user2") String user2);

    List<ChatMessage> findByToOrderByTimestampDesc(String to);

    List<ChatMessage> findByToAndStatusOrderByTimestampDesc(String to, ChatMessage.Status status);
} 