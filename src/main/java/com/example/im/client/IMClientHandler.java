package com.example.im.client;

import com.example.im.protocol.Message;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IMClientHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(IMClientHandler.class);
    private static final Gson gson = new Gson();
    private ChannelHandlerContext context;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.context = ctx;
        logger.info("Connected to server");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        Message message = gson.fromJson(msg, Message.class);
        
        switch (message.getType()) {
            case HEARTBEAT:
                handleHeartbeat(ctx, message);
                break;
            case CHAT:
                handleChatMessage(message);
                break;
            case ACK:
                handleAck(message);
                break;
            case READ_RECEIPT:
                handleReadReceipt(message);
                break;
            default:
                logger.info("Received message: {}", msg);
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, Message message) {
        Message response = new Message();
        response.setType(Message.MessageType.HEARTBEAT_RESPONSE);
        response.setTimestamp(System.currentTimeMillis());
        ctx.writeAndFlush(gson.toJson(response));
    }

    private void handleChatMessage(Message message) {
        logger.info("Received chat message from {}: {}", message.getFrom(), message.getContent());
        
        // 发送确认消息
        if (message.isNeedAck()) {
            Message ack = new Message();
            ack.setType(Message.MessageType.ACK);
            ack.setAckMessageId(message.getMessageId());
            ack.setFrom(message.getTo());
            ack.setTo(message.getFrom());
            ack.setTimestamp(System.currentTimeMillis());
            context.writeAndFlush(gson.toJson(ack));
        }
    }

    private void handleAck(Message message) {
        logger.info("Message {} has been acknowledged", message.getAckMessageId());
    }

    private void handleReadReceipt(Message message) {
        logger.info("Message {} has been read by {}", message.getMessageId(), message.getFrom());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        this.context = null;
        logger.info("Disconnected from server");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught:", cause);
        ctx.close();
    }
} 