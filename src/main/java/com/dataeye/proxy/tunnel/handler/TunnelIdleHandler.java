package com.dataeye.proxy.tunnel.handler;

import com.dataeye.logback.LogbackRollingFileUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/3/25 17:43
 * @description
 */

public class TunnelIdleHandler extends ChannelDuplexHandler {

    private static final Logger log = LogbackRollingFileUtil.getLogger("TunnelIdleHandler");
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof IdleStateEvent) {
            log.debug("idle event fired!");
            ctx.channel().close();
        }
    }

}
