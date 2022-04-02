package com.dataeye.proxy.tunnel.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jaret
 * @date 2022/3/25 19:28
 * @description
 */
@Slf4j
public class TunnelHttpProxyHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_http_proxy";

    private final Channel uaChannel;

    private final String remoteAddr;

    private final RemoteChannelInactiveCallback remoteChannelInactiveCallback;

    public TunnelHttpProxyHandler(Channel uaChannel, String remoteAddr,
                            RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.uaChannel = uaChannel;
        this.remoteAddr = remoteAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Remote channel: " + remoteAddr + " active");
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        HttpObject ho = (HttpObject) msg;
        log.debug("接收数据 From: " + remoteAddr + ", " + ho.getClass().getName());

        if (ho instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) ho;
            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);
        }

        if (ho instanceof HttpContent) {
            ((HttpContent) ho).retain();
        }


        if (uaChannel.isActive()) {
            uaChannel.writeAndFlush(ho).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.read();
                        ctx.fireChannelRead(msg);
                    } else {
                        ReferenceCountUtil.release(msg);
                        ctx.close();
                    }
                }
            });
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        log.debug("Remote channel: " + remoteAddr + " inactive");

        uaChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                remoteChannelInactiveCallback.remoteChannelInactiveCallback(ctx, remoteAddr);
            }
        });
        ctx.fireChannelInactive();

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(cause.getMessage(), cause);
        ctx.close();
    }

    public interface RemoteChannelInactiveCallback {
        void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                           String remoeAddr) throws Exception;
    }

}
