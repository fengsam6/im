package com.example.im.repository;

import com.example.im.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("SELECT m FROM ChatMessage m WHERE (m.fromUser = :user1 AND m.toUser = :user2) OR (m.fromUser = :user2 AND m.toUser = :user1) ORDER BY m.timestamp DESC")
    List<ChatMessage> findChatHistory(
            @Param("user1") String user1, 
            @Param("user2") String user2, 
            Pageable pageable);
} 