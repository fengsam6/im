package com.example.im.service;

import com.example.im.entity.ChatMessage;
import com.example.im.protocol.Message;
import com.example.im.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ChatMessageService {
    @Autowired
    private ChatMessageRepository messageRepository;

    public void saveMessage(Message message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setMessageId(message.getMessageId());
        chatMessage.setFromUser(message.getFrom());
        chatMessage.setToUser(message.getTo());
        chatMessage.setContent(message.getContent());
        chatMessage.setMessageType(message.getMessageType());
        chatMessage.setTimestamp(new Date(message.getTimestamp()));
        chatMessage.setStatus(message.getStatus().toString());
        messageRepository.save(chatMessage);
    }

    public List<ChatMessage> getChatHistory(String user1, String user2, int limit) {
        return messageRepository.findChatHistory(
            user1, 
            user2, 
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
    }
} 