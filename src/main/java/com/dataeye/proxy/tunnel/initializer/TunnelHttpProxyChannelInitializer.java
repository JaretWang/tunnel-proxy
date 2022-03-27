package com.dataeye.proxy.tunnel.initializer;

import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.tunnel.handler.TunnelCacheSaveHandler;
import com.dataeye.proxy.tunnel.handler.TunnelHttpProxyHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

/**
 * @author jaret
 * @date 2022/3/27 16:18
 * @description
 */
public class TunnelHttpProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private ProxyServerConfig proxyServerConfig;
    private Channel uaChannel;
    private String remoteAddr;
    private ProxySslContextFactory proxySslContextFactory;
    private TunnelHttpProxyHandler.RemoteChannelInactiveCallback remoteChannelInactiveCallback;

    public TunnelHttpProxyChannelInitializer(ProxyServerConfig proxyServerConfig, Channel uaChannel,
                                             String remtoeAddr,ProxySslContextFactory proxySslContextFactory,
                                             TunnelHttpProxyHandler.RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.proxyServerConfig = proxyServerConfig;
        this.uaChannel = uaChannel;
        this.remoteAddr = remtoeAddr;
        this.proxySslContextFactory = proxySslContextFactory;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();

        String remoteHost = proxyServerConfig.getRemoteHost();
        int remotePort = proxyServerConfig.getRemotePort();
        if (proxyServerConfig.getRemoteListenType() == TunnelProxyListenType.SSL) {
            SSLEngine engine = proxySslContextFactory.createClientSslEnginForRemoteAddress(remoteHost, remotePort);
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        if (proxyServerConfig.getRemoteListenType() == TunnelProxyListenType.SSL) {
            SSLEngine engine = proxySslContextFactory.createClientSslEnginForRemoteAddress(remoteHost, remotePort);
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        pipeline.addLast("codec", new HttpClientCodec());
        pipeline.addLast(TunnelHttpProxyHandler.HANDLER_NAME, new TunnelHttpProxyHandler(uaChannel, remoteAddr, remoteChannelInactiveCallback));
        pipeline.addLast(TunnelCacheSaveHandler.HANDLER_NAME, new TunnelCacheSaveHandler());
    }
}

