package com.example.im.client;

import com.example.im.protocol.Message;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class IMClientHandler extends SimpleChannelInboundHandler<Message> {
    private static final Logger logger = LoggerFactory.getLogger(IMClientHandler.class);
    private static final Gson gson = new Gson();
    private ChannelHandlerContext context;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.context = ctx;
        logger.info("Connected to server");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        log.debug("Received message: {}", msg);
        
        switch (msg.getType()) {
            case HEARTBEAT:
                handleHeartbeat(ctx, msg);
                break;
            case CHAT:
                handleChatMessage(msg);
                break;
            case ACK:
                handleAck(msg);
                break;
            case READ_RECEIPT:
                handleReadReceipt(msg);
                break;
            default:
                logger.info("Received message: {}", msg);
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, Message msg) {
        Message response = new Message();
        response.setType(Message.Type.HEARTBEAT);
        response.setFrom(msg.getTo());
        response.setTo(msg.getFrom());
        response.setTimestamp(System.currentTimeMillis());
        
        ctx.writeAndFlush(response);
    }

    private void handleChatMessage(Message message) {
        logger.info("Received chat message from {}: {}", message.getFrom(), message.getContent());
        
        // 发送确认消息
        if (message.isNeedAck()) {
            Message ack = new Message();
            ack.setType(Message.Type.ACK);
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