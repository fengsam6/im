package com.example.im.controller;

import com.example.im.entity.ChatMessage;
import com.example.im.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/history")
    public List<ChatMessage> getChatHistory(
            @RequestParam String user1,
            @RequestParam String user2,
            @RequestParam(defaultValue = "50") int limit) {
        return chatMessageService.getChatHistory(user1, user2, limit);
    }

    @GetMapping("/messages")
    public List<ChatMessage> getUserMessages(@RequestParam String username) {
        return chatMessageService.getUserMessages(username);
    }

    @GetMapping("/unread")
    public List<ChatMessage> getUnreadMessages(@RequestParam String username) {
        return chatMessageService.getUnreadMessages(username);
    }
} 