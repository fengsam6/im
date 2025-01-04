package com.example.im.server;

import com.google.gson.Gson;
import com.example.im.protocol.Message;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IMServerHandler extends SimpleChannelInboundHandler<String> {
    private static final Map<String, Channel> channelMap = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        Message message = gson.fromJson(msg, Message.class);
        
        switch (message.getType()) {
            case LOGIN:
                handleLogin(ctx, message);
                break;
            case CHAT:
                handleChat(message);
                break;
            case LOGOUT:
                handleLogout(message);
                break;
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, Message message) {
        channelMap.put(message.getFrom(), ctx.channel());
        System.out.println("User logged in: " + message.getFrom());
    }

    private void handleChat(Message message) {
        Channel targetChannel = channelMap.get(message.getTo());
        if (targetChannel != null && targetChannel.isActive()) {
            targetChannel.writeAndFlush(gson.toJson(message));
        }
    }

    private void handleLogout(Message message) {
        channelMap.remove(message.getFrom());
        System.out.println("User logged out: " + message.getFrom());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
} 