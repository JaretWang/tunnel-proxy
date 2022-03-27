package com.dataeye.proxy.tunnel.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jaret
 * @date 2022/3/25 17:43
 * @description
 */
@Slf4j
public class TunnelIdleHandler extends ChannelDuplexHandler {


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof IdleStateEvent) {
            log.debug("idle event fired!");
            ctx.channel().close();
        }
    }

}
