package com.dataeye.proxy.tunnel.initializer;

import com.dataeye.proxy.bean.TunnelAllocateResult;
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

    private final Channel uaChannel;
    private final TunnelHttpProxyHandler.RemoteChannelInactiveCallback remoteChannelInactiveCallback;
    private final ProxySslContextFactory proxySslContextFactory;
    private final TunnelAllocateResult tunnelAllocateResult;

    public TunnelHttpProxyChannelInitializer(TunnelAllocateResult tunnelAllocateResult, Channel uaChannel, ProxySslContextFactory proxySslContextFactory,
                                             TunnelHttpProxyHandler.RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.tunnelAllocateResult = tunnelAllocateResult;
        this.uaChannel = uaChannel;
        this.proxySslContextFactory = proxySslContextFactory;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void initChannel(SocketChannel channel) {

        ChannelPipeline pipeline = channel.pipeline();

        String remoteAddr = tunnelAllocateResult.getRemote();
        String remoteHost = tunnelAllocateResult.getIp();
        int remotePort = tunnelAllocateResult.getPort();
        if (tunnelAllocateResult.getTunnelProxyListenType() == TunnelProxyListenType.SSL) {
            SSLEngine engine = proxySslContextFactory.createClientSslEnginForRemoteAddress(remoteHost, remotePort);
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        pipeline.addLast("codec", new HttpClientCodec());
        pipeline.addLast(TunnelHttpProxyHandler.HANDLER_NAME, new TunnelHttpProxyHandler(uaChannel, remoteAddr, remoteChannelInactiveCallback));
        // todo 后续仔细看看这个缓存
//        pipeline.addLast(TunnelCacheSaveHandler.HANDLER_NAME, new TunnelCacheSaveHandler());
    }
}

