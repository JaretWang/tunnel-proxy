package com.dataeye.proxy.tunnel.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jaret
 * @date 2022/3/25 19:10
 * @description
 */
@Slf4j
public class TunnelProxySchemaHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_schema";

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            log.info("TunnelProxySchemaHandler 接收到请求内容: {}", httpRequest.toString());

            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(TunnelCacheFindHandler.HANDLER_NAME);
                ctx.pipeline().remove(TunnelProxyForwardHandler.HANDLER_NAME);
            }

        }

        ctx.fireChannelRead(msg);
    }
}
