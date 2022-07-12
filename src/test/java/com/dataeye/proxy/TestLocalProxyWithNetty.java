package com.dataeye.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author jaret
 * @date 2022/3/18 18:24
 * @description 使用 netty client 测试 proxy server
 */
@Slf4j
@SpringBootTest
public class TestLocalProxyWithNetty {

    @Test
    public void test(String host, String path) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new HttpClientCodec());
                            pipeline.addLast(new HttpObjectAggregator(65535));
                            pipeline.addLast(new MyHttpHandler());
                        }
                    });
            // Make the connection attempt.
            ChannelFuture connect = b.connect("127.0.0.1", 8123);
            Channel channel = connect.sync().channel();
            connect.addListener(new ChannelFutureListener() {
                //            b.connect(session.getHost(), session.getPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Prepare the HTTP request.
                        Channel outChannel = future.channel();
                        log.info("outChannel hash : {}",outChannel.hashCode());
                        log.info("channel hash : {}",channel.hashCode());

                        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://www.baidu.com");
                        request.headers().set("Connection", "close");
                        request.headers().set("Host", "www.baidu.com:80");
                        // Send the HTTP request.
                        outChannel.writeAndFlush(request);
                    } else {
                        // Close the connection if the connection attempt has failed.
                        log.error("错误");
                    }
                }
            });

            // Wait for the server to close the connection.
            channel.closeFuture().sync();

        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        } finally {
            // Shut down executor threads to exit.
            group.shutdownGracefully();
        }
    }

    @Test
    public void testBaidu() {
        test("www.baidu.com", "/");
    }

    @Test
    public void testYoutube() {
        test("www.youtube.com", "/a.html");
    }

    @Test
    public void testFake() {
        test("www.nosuchhost.com", "/a.html");
    }

    @Test
    public void testGithub() {
        test("www.github.com", "/");
    }



}

class MyHttpHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpResponse) {

        }
    }
}