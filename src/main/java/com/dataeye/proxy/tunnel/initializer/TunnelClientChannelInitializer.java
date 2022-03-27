package com.dataeye.proxy.tunnel.initializer;

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
    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;

    public TunnelClientChannelInitializer(ProxyServerConfig proxyServerConfig,
                                          Channel uaChannel,
                                          ProxySslContextFactory proxySslContextFactory) {
        this.proxyServerConfig = proxyServerConfig;
        this.uaChannel = uaChannel;
        this.proxySslContextFactory = proxySslContextFactory;
    }

    /**
     * @see io.netty.channel.ChannelInitializer#initChannel(io.netty.channel.Channel)
     */
    @Override
    protected void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();


        if (proxyServerConfig.getRemoteListenType() == TunnelProxyListenType.SSL) {
            SSLEngine engine = proxySslContextFactory.createClientSslEnginForRemoteAddress(
                    proxyServerConfig.getRemoteHost(), proxyServerConfig.getRemotePort());
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        if (proxyServerConfig.getRemoteListenType() == TunnelProxyListenType.PLAIN) {
            // TODO nothing to do
        }

        String remoteAddress = proxyServerConfig.getRemote();
        pipeline.addLast(new TunnelProxyRelayHandler(remoteAddress + " -----> UA", uaChannel));

    }
}
