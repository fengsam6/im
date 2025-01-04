package com.example.im.service;

import com.example.im.entity.ChatMessage;
import com.example.im.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ChatMessageService {
    @Autowired(required = false)
    private ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        if (chatMessageRepository != null) {
            try {
                return chatMessageRepository.save(message);
            } catch (Exception e) {
                log.error("Error saving message", e);
            }
        }
        return message;
    }

    @Transactional
    public void updateMessageStatus(String messageId, ChatMessage.Status status) {
        if (chatMessageRepository != null) {
            try {
                Optional<ChatMessage> messageOpt = chatMessageRepository.findByMessageId(messageId);
                messageOpt.ifPresent(message -> {
                    message.setStatus(status);
                    chatMessageRepository.save(message);
                });
            } catch (Exception e) {
                log.error("Error updating message status", e);
            }
        }
    }

    public List<ChatMessage> getChatHistory(String user1, String user2, int limit) {
        if (chatMessageRepository != null) {
            try {
                List<ChatMessage> messages = chatMessageRepository.findChatHistory(user1, user2);
                return limit > 0 && messages.size() > limit ? 
                    messages.subList(0, limit) : messages;
            } catch (Exception e) {
                log.error("Error getting chat history", e);
            }
        }
        return Collections.emptyList();
    }

    public List<ChatMessage> getUserMessages(String username) {
        if (chatMessageRepository != null) {
            try {
                return chatMessageRepository.findByToOrderByTimestampDesc(username);
            } catch (Exception e) {
                log.error("Error getting user messages", e);
            }
        }
        return Collections.emptyList();
    }

    public List<ChatMessage> getUnreadMessages(String username) {
        if (chatMessageRepository != null) {
            try {
                return chatMessageRepository.findByToAndStatusOrderByTimestampDesc(
                    username, ChatMessage.Status.SENT);
            } catch (Exception e) {
                log.error("Error getting unread messages", e);
            }
        }
        return Collections.emptyList();
    }
} 