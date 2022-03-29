package com.dataeye.proxy.tunnel.initializer;

import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.TunnelManageConfig;
import com.dataeye.proxy.service.TunnelDistributeService;
import com.dataeye.proxy.tunnel.handler.*;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/3/25 17:41
 * @description 初始化通道处理器链
 */
@Slf4j
public class TunnelProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;
    private final ThreadPoolTaskExecutor ioThreadPool;
    private final TunnelDistributeService tunnelDistributeService;
    private final TunnelManageConfig tunnelManageConfig;

    public TunnelProxyServerChannelInitializer(ProxyServerConfig proxyServerConfig,
                                               ProxySslContextFactory proxySslContextFactory,
                                               ThreadPoolTaskExecutor ioThreadPool,
                                               TunnelDistributeService tunnelDistributeService,
                                               TunnelManageConfig tunnelManageConfig) {
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
        this.ioThreadPool = ioThreadPool;
        this.tunnelDistributeService = tunnelDistributeService;
        this.tunnelManageConfig = tunnelManageConfig;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

//        // 心跳检测
//        pipeline.addLast("idlestate", new IdleStateHandler(0, 0, 3, TimeUnit.MINUTES));
//        pipeline.addLast("idlehandler", new TunnelIdleHandler());
//        // 日志级别
//        pipeline.addLast("datalog", new LoggingHandler("PRE_BYTE_LOGGER", LogLevel.DEBUG));
        if (proxyServerConfig.getTunnelProxyListenType() == TunnelProxyListenType.SSL) {
            SSLEngine engine = proxySslContextFactory.createServerSslEngine();
            pipeline.addLast("proxy.encrypt", new SslHandler(engine));
        }
//        pipeline.addLast("log", new LoggingHandler("BYTE_LOGGER", LogLevel.INFO));
        // 编码解码 http
        pipeline.addLast("codec", new HttpServerCodec());
        // 前置处理
        pipeline.addLast(TunnelProxyPreHandler.HANDLER_NAME, new TunnelProxyPreHandler(proxyServerConfig));
        // 请求模式选择处理
        pipeline.addLast(TunnelProxySchemaHandler.HANDLER_NAME, new TunnelProxySchemaHandler(proxyServerConfig));
        // 缓存检查
        pipeline.addLast(TunnelCacheFindHandler.HANDLER_NAME, new TunnelCacheFindHandler());
        // 普通请求, 直接转发
        pipeline.addLast(TunnelProxyForwardHandler.HANDLER_NAME, new TunnelProxyForwardHandler(proxyServerConfig, proxySslContextFactory));
        // CONNECT请求, 使用代理转发
        pipeline.addLast(TunnelProxyHandler.HANDLER_NAME, new TunnelProxyHandler(proxyServerConfig, proxySslContextFactory, ioThreadPool, tunnelDistributeService, tunnelManageConfig));
    }
}
