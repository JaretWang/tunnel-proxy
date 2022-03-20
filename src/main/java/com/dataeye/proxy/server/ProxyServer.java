package com.dataeye.proxy.server;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.TunnelCons;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description
 */
@Slf4j
@Component
public class ProxyServer {

    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Autowired
    private ProxyServerChannelInitializer proxyServerChannelInitializer;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start2() {
        int bossThreadCount = proxyServerConfig.getBossThreadCount();
        int workerThreadCount = proxyServerConfig.getWorkerThreadCount();
        bossGroup = new NioEventLoopGroup(bossThreadCount);
        workerGroup = new NioEventLoopGroup(workerThreadCount);
        String host = proxyServerConfig.getHost();
        int port = proxyServerConfig.getPort();
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(proxyServerChannelInitializer)
                .childOption(ChannelOption.SO_KEEPALIVE, false)
                .childOption(ChannelOption.TCP_NODELAY, false);
        try {
            ChannelFuture future = serverBootstrap.bind(host, port).sync();
            log.info("代理服务器启动成功, ip: {}, port: {}", host, port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("proxy server 出现异常：{}", e.getMessage());
            e.printStackTrace();
        } finally {
            log.warn("被动关闭 proxy server");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    //    @PostConstruct
    public void start() {
        Runnable task = () -> {
            log.info("HttpProxyServer started on port: {}", proxyServerConfig.getPort());
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
//                        .handler(new LoggingHandler(LogLevel.DEBUG))
                        .childHandler(proxyServerChannelInitializer)
                        .bind(proxyServerConfig.getPort())
                        .sync().channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("捕获异常：{}", e.getMessage());
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        };

        new Thread(task).start();
    }

    public void shutdown() {
        log.warn("主动关闭 proxy server");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

}
