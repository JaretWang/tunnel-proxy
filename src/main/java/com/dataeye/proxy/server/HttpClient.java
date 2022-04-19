package com.dataeye.proxy.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

public class HttpClient {
    public static void start(String host, int port) {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        try {
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel channel)
                                throws Exception {
                            channel.pipeline().addLast(new HttpClientCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(65536));
                            channel.pipeline().addLast(new HttpContentDecompressor());
                            channel.pipeline().addLast(new HttpClientHandler());
                        }
                    });
            ChannelFuture connect = bootstrap.connect(host, port);
            Channel channel = connect.channel();
            connect.addListener(new ChannelFutureListener() {
                        //            b.connect(session.getHost(), session.getPort()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {

								System.out.println("成功:");

								Channel outChannel = future.channel();
								System.out.println("outChannel hash : "+outChannel.hashCode());
								System.out.println("channel hash : "+ channel.hashCode());

								HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://www.baidu.com");
								request.headers().set("Connection", "close");
								request.headers().set("Host", "www.baidu.com:80");
								// Send the HTTP request.
								outChannel.writeAndFlush(request);

                            } else {
                                // Close the connection if the connection attempt has failed.
								System.out.println("错误 "+ future.cause().getMessage());
                            }
                        }
                    });
            connect.channel().closeFuture().sync();
        } catch (Exception e) {
			System.out.println("Eeeeeeee");
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        //   110.80.160.174:4231(2022-04-17T18:28:14)
        start("110.80.160.174", 4231);
    }
}
