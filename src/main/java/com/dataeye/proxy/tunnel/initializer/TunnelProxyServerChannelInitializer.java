package com.dataeye.proxy.tunnel.initializer;

import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.service.ProxyService;
import com.dataeye.proxy.tunnel.handler.TunnelProxyForwardHandler;
import com.dataeye.proxy.tunnel.handler.TunnelProxyHandler;
import com.dataeye.proxy.tunnel.handler.TunnelProxySchemaHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLEngine;

/**
 * @author jaret
 * @date 2022/3/25 17:41
 * @description 初始化通道处理器链
 */
@Slf4j
public class TunnelProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;
    private final ITunnelDistributeService tunnelDistributeService;
    private final TunnelInstance tunnelInstance;
    private final IpSelector ipSelector;
    private final ProxyService proxyService;

    public TunnelProxyServerChannelInitializer(ProxyServerConfig proxyServerConfig,
                                               ProxySslContextFactory proxySslContextFactory,
                                               ITunnelDistributeService tunnelDistributeService,
                                               TunnelInstance tunnelInstance, IpSelector ipSelector, ProxyService proxyService) {
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
        this.tunnelDistributeService = tunnelDistributeService;
        this.tunnelInstance = tunnelInstance;
        this.ipSelector = ipSelector;
        this.proxyService = proxyService;
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

//        // 服务端，对响应编码。属于ChannelOutboundHandler，逆序执行
//        pipeline.addLast("encoder", new HttpResponseEncoder());
//        // 服务端，对请求解码。属于ChannelIntboundHandler，按照顺序执行
//        pipeline.addLast("decoder", new HttpRequestDecoder());
        // 编码解码 http: HttpRequestDecoder 和 HttpResponseEncoder 的组合
        pipeline.addLast("codec", new HttpServerCodec());
//        pipeline.addLast("object_agg", new HttpObjectAggregator(1024 * 64));
        pipeline.addLast("object_agg", new HttpObjectAggregator(Integer.MAX_VALUE));
//        pipeline.addLast("chunked_write", new ChunkedWriteHandler());
        // 前置处理
//        pipeline.addLast(TunnelProxyPreHandler.HANDLER_NAME, new TunnelProxyPreHandler(proxyServerConfig));
        // 检查认证信息
        pipeline.addLast(TunnelProxySchemaHandler.HANDLER_NAME, new TunnelProxySchemaHandler(tunnelInstance, ipSelector));
        // 缓存检查
//        pipeline.addLast(TunnelCacheFindHandler.HANDLER_NAME, new TunnelCacheFindHandler());
        // 普通请求, 直接转发
        pipeline.addLast(TunnelProxyForwardHandler.HANDLER_NAME, new TunnelProxyForwardHandler(tunnelInstance, proxyServerConfig,
                proxySslContextFactory, tunnelDistributeService,proxyService));
        // CONNECT请求, 使用代理转发
//        pipeline.addLast(TunnelProxyHandler.HANDLER_NAME, new TunnelProxyHandler(tunnelDistributeService, tunnelInstance));
        pipeline.addLast(TunnelProxyHandler.HANDLER_NAME, new TunnelProxyHandler(tunnelDistributeService, tunnelInstance, proxyService));
    }
}
