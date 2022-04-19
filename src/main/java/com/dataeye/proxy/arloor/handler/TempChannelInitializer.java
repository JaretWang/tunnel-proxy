package com.dataeye.proxy.arloor.handler;

import com.dataeye.proxy.apn.handler.DirectRelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;

/**
 * @author jaret
 * @date 2022/4/17 17:35
 * @description
 */
public class TempChannelInitializer extends ChannelInitializer<SocketChannel> {

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
    private final TempHandler.RemoteChannelInactiveCallback remoteChannelInactiveCallback;

    public TempChannelInitializer(Channel uaChannel,
                                  String remtoeAddr, TempHandler.RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.uaChannel = uaChannel;
        this.remoteAddr = remtoeAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("codec", new HttpClientCodec());
//        pipeline.addLast("http_proxy_agg", new HttpObjectAggregator(1024*1204));
        pipeline.addLast(DirectRelayHandler.HANDLER_NAME, new TempHandler(uaChannel, remoteAddr, remoteChannelInactiveCallback));
    }
}