package com.example.im.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class IMClient {
    private final String host;
    private final int port;
    private Channel channel;

    public IMClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());
                            pipeline.addLast(new IMClientHandler());
                        }
                    });

            channel = bootstrap.connect(host, port).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public static void main(String[] args) throws Exception {
        new IMClient("localhost", 8080).start();
    }
} 