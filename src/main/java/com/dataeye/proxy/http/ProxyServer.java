package com.dataeye.proxy.http;

import com.dataeye.proxy.config.ProxyServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
public class ProxyServer {

    @Autowired private ThreadForwardInitializer threadForwardInitializer;
    @Autowired private ProxyServerConfig proxyServerConfig;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * 启动一个代理服务器
     */
    public ProxyServer start() {
        String host = proxyServerConfig.getHost();
        int port = proxyServerConfig.getPort();
        bossGroup = new NioEventLoopGroup(proxyServerConfig.getBossThreadCount());
        workerGroup = new NioEventLoopGroup(proxyServerConfig.getWorkerThreadCount());
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(threadForwardInitializer)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        try {
            ChannelFuture future = serverBootstrap.bind(host, port).sync();
            log.info("代理服务器启动成功, ip: {}, port: {}", host, port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("启动代理服务器时，出现异常：{}", e.getMessage());
            e.printStackTrace();
        } finally {
            log.warn("关闭代理服务器");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        return this;
    }

    public void shutdown() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }


}
