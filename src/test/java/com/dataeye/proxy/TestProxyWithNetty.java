//package com.dataeye.proxy;
//
//import com.dataeye.proxy.config.ProxyServerConfig;
//import com.dataeye.proxy.server.ProxyServerChannelInitializer;
//import io.netty.bootstrap.Bootstrap;
//import io.netty.channel.Channel;
//import io.netty.channel.EventLoopGroup;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.nio.NioSocketChannel;
//import io.netty.handler.codec.http.*;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import javax.annotation.Resource;
//
///**
// * @author jaret
// * @date 2022/3/18 18:24
// * @description
// */
//@Slf4j
//@SpringBootTest
//public class TestProxyWithNetty {
//
//    @Resource
//    private ProxyServerConfig proxyServerConfig;
//
//    @Test
//    public void test(String host, String path) {
//        EventLoopGroup group = new NioEventLoopGroup();
//        try {
//            Bootstrap b = new Bootstrap();
//            b.group(group).channel(NioSocketChannel.class)
//                    .handler(new ProxyServerChannelInitializer());
//
//            // Make the connection attempt.
//            Channel ch = b.connect(proxyServerConfig.getHost(), proxyServerConfig.getPort()).sync().channel();
//
//            // Prepare the HTTP request.
//            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
//                    "http://" + host + path);
//            request.headers().set(HttpHeaders.Names.HOST, host);
//            request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
//            //request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
//
//            // Send the HTTP request.
//            ch.writeAndFlush(request);
//            // Wait for the server to close the connection.
//            ch.closeFuture().sync();
//
//        } catch (InterruptedException e) {
//            log.error(e.getMessage(), e);
//        } finally {
//            // Shut down executor threads to exit.
//            group.shutdownGracefully();
//        }
//    }
//
//    @Test
//    public void testBaidu() {
//        test("www.baidu.com", "/");
//    }
//
//    @Test
//    public void testYoutube() {
//        test("www.youtube.com", "/a.html");
//    }
//
//    @Test
//    public void testFake() {
//        test("www.nosuchhost.com", "/a.html");
//    }
//
//    @Test
//    public void testGithub() {
//        test("www.github.com", "/");
//    }
//
//}
