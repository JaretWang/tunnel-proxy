package com.dataeye.proxy.server.handler;

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
    private String source = "unknown";

    public IdleHandler() {
    }

    public IdleHandler(String source) {
        this.source = source;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            String name = event.state().name();
            logger.info("idle event is fired, source:{}, type: {}, class={}", source, name, evt.getClass().getSimpleName());
            // 读写空闲，关闭通道
            SocksServerUtils.errorHttpResp(ctx.channel(), "read or write free in a period of time");
            ctx.channel().close();
        }
    }

}
