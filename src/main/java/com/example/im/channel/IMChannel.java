package com.example.im.channel;

public interface IMChannel {
    void writeAndFlush(String message);
    boolean isActive();
} 