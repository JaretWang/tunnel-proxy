package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jaret
 * @date 2022/3/25 18:29
 * @description
 */
@Slf4j
public class TunnelProxyRelayHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_relay";
    private final Channel relayChannel;
    private final String tag;

    public TunnelProxyRelayHandler(String tag, Channel relayChannel) {
        this.tag = tag;
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("{} channel active", tag);
        if (!ctx.channel().config().getOption(ChannelOption.AUTO_READ)) {
            ctx.read();
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        log.info("{} : {}", tag, msg);

        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Boolean option = ctx.channel().config().getOption(ChannelOption.AUTO_READ);
                    if (!option) {
                        ctx.read();
                    }
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug(tag + " channel inactive");
        }
        if (relayChannel != null && relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
                    ChannelFutureListener.CLOSE);
        }
//        SocksServerUtils.closeOnFlush(relayChannel);
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(tag, cause);
        ctx.close();
    }

}
