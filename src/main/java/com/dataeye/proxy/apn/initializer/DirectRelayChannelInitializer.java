package com.dataeye.proxy.apn.initializer;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.handler.DirectRelayHandler;
import com.dataeye.proxy.apn.handler.DirectRelayHandler.RemoteChannelInactiveCallback;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.utils.ApnProxySSLContextFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

/**
 * @author jaret
 * @date 2022/5/9 15:41
 */
public class DirectRelayChannelInitializer extends ChannelInitializer<SocketChannel> {

    public static final String CLIENT_CODEC_NAME = "client.codec";
    public static final String CLIENT_REQUEST_AGG_NAME = "client.request.agg";
    /**
     * 代理ip
     */
    private final ApnProxyRemote apnProxyRemote;
    /**
     * 跟被代理的请求建立的channel
     */
    private final Channel uaChannel;
    /**
     * 代理ip地址
     */
    private final String remoteAddr;
    /**
     * proxy client 跟远程代理IP建立的通道关闭后的回调策略
     */
    private final RemoteChannelInactiveCallback remoteChannelInactiveCallback;
    private final ApnHandlerParams apnHandlerParams;

    public DirectRelayChannelInitializer(ApnHandlerParams apnHandlerParams, ApnProxyRemote apnProxyRemote, Channel uaChannel,
                                         String remtoeAddr, RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.apnHandlerParams = apnHandlerParams;
        this.apnProxyRemote = apnProxyRemote;
        this.uaChannel = uaChannel;
        this.remoteAddr = remtoeAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();
        if (apnProxyRemote.getRemoteListenType() == ApnProxyListenType.SSL) {
            SSLEngine engine = ApnProxySSLContextFactory.createSslEngine(
                    apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort());
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }
        pipeline.addLast(CLIENT_CODEC_NAME, new HttpClientCodec());
        pipeline.addLast(CLIENT_REQUEST_AGG_NAME, new HttpObjectAggregator(1024 * 1204));
        pipeline.addLast(DirectRelayHandler.HANDLER_NAME, new DirectRelayHandler(apnHandlerParams, uaChannel, remoteAddr, remoteChannelInactiveCallback));
    }
}
