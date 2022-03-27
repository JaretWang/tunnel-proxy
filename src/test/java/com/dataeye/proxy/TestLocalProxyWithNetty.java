package com.dataeye.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
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
            b.group(group).channel(NioSocketChannel.class)
//                    .handler(new ProxyServerChannelInitializer());
                    .handler(new HttpClientCodec());

            // Make the connection attempt.
//            Channel ch = b.connect(proxyServerConfig.getHost(), proxyServerConfig.getPort()).sync().channel();
            Channel ch = b.connect("127.0.0.1", 8123).sync().channel();

            // Prepare the HTTP request.
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "http://www.baidu.com");
            request.headers().set("Connection", "close");
            request.headers().set("Host", "http://www.baidu.com:80");

            // Send the HTTP request.
            ch.writeAndFlush(request);
            // Wait for the server to close the connection.
            ch.closeFuture().sync();

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
