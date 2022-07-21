package com.dataeye.proxy.apn.handler;

import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/4/14 10:41
 */
public class IdleHandler extends ChannelDuplexHandler {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("IdleHandler");

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof IdleStateEvent) {
            logger.info("idle event is fired, type: {}", evt.getClass().getSimpleName());
            // 读写空闲，关闭通道
            SocksServerUtils.errorHttpResp(ctx.channel(), "read or write free in a period of time");
            ctx.channel().close();
        }
    }

}
