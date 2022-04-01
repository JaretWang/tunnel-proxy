package com.dataeye.proxy.tunnel.initializer;

import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.tunnel.handler.TunnelProxyRelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

/**
 * @author jaret
 * @date 2022/3/25 18:21
 * @description
 */
public class TunnelClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final Channel uaChannel;
    private final ProxySslContextFactory proxySslContextFactory;
    private final TunnelAllocateResult tunnelAllocateResult;

    public TunnelClientChannelInitializer(TunnelAllocateResult tunnelAllocateResult,
                                          Channel uaChannel, ProxySslContextFactory proxySslContextFactory) {
        this.tunnelAllocateResult = tunnelAllocateResult;
        this.uaChannel = uaChannel;
        this.proxySslContextFactory = proxySslContextFactory;
    }

    /**
     * @see io.netty.channel.ChannelInitializer#initChannel(io.netty.channel.Channel)
     */
    @Override
    protected void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();

        if (tunnelAllocateResult.getTunnelProxyListenType() == TunnelProxyListenType.SSL) {
            String ip = tunnelAllocateResult.getIp();
            int port = tunnelAllocateResult.getPort();
            SSLEngine engine = proxySslContextFactory.createClientSslEnginForRemoteAddress(ip, port);
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        if (tunnelAllocateResult.getTunnelProxyListenType() == TunnelProxyListenType.PLAIN) {
            // TODO nothing to do
        }

        String remoteAddress = tunnelAllocateResult.getRemote();
        pipeline.addLast(new TunnelProxyRelayHandler(remoteAddress + " -----> UA", uaChannel));

    }
}
