package com.dataeye.proxy.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jaret
 * @date 2022/3/18 15:16
 * @description 转发请求
 */
@Slf4j
public class ProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "forward";

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("ProxyForwardHandler: {}",cause.getMessage());
        ctx.close();
    }
}
