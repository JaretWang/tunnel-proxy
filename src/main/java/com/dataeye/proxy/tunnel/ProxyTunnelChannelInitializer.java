package com.dataeye.proxy.tunnel;

import com.dataeye.proxy.bean.ProxyListenType;
import com.dataeye.proxy.bean.ProxyRemote;
import com.dataeye.proxy.bean.ProxySslRemote;
import com.dataeye.proxy.utils.ProxySslContextUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLEngine;

/**
 * @author jaret
 * @date 2022/3/18 16:18
 * @description
 */
@Slf4j
public class ProxyTunnelChannelInitializer extends ChannelInitializer<SocketChannel>  {

    private final Channel uaChannel;
    private final ProxyRemote proxyRemote;

    public ProxyTunnelChannelInitializer(ProxyRemote proxyRemote, Channel uaChannel) {
        this.proxyRemote = proxyRemote;
        this.uaChannel = uaChannel;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        log.info("代理请求处理器...");

//        ChannelPipeline pipeline = socketChannel.pipeline();
//        // ssl
//        if (proxyRemote.getRemoteListenType() == ProxyListenType.SSL) {
//            ProxySslRemote sslRemote = (ProxySslRemote) proxyRemote;
//            SSLEngine engine = ProxySslContextUtils.createClientSslEnginForRemoteAddress(
//                    sslRemote.getRemoteHost(), sslRemote.getRemotePort());
//            assert engine != null;
//            engine.setUseClientMode(true);
//            pipeline.addLast("ssl", new SslHandler(engine));
//        }
        // 中转处理器
//        pipeline.addLast(new ProxyRelayHandler(proxyRemote.getRemote() + " --> UA", uaChannel));
    }

}
