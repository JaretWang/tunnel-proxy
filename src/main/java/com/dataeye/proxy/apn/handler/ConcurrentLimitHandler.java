package com.dataeye.proxy.apn.handler;

import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.*;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author jaret
 * @date 2022/4/14 13:15
 * @description
 */
@ChannelHandler.Sharable
public class ConcurrentLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ConnectionLimitHandler");

    private final int maxConcurrency;
    private final AtomicLong connections = new AtomicLong(0);
    private final LongAdder droppedConnections = new LongAdder();
    private final AtomicBoolean loggingScheduled = new AtomicBoolean(false);

    public ConcurrentLimitHandler(TunnelInstance tunnelInstance) {
        this.maxConcurrency = tunnelInstance.getConcurrency();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        long conn = connections.incrementAndGet();
        if (conn > 0 && conn <= maxConcurrency) {
            // 监听通道关闭动作
            channel.closeFuture().addListener(future -> {
                // 通道关闭了，就移除channel，并减 1
                connections.decrementAndGet();
            });
            super.channelActive(ctx);
        } else {
            connections.decrementAndGet();
            // 设置 linger 选项为 0，是为了 server 不会获取到太多的 TIME_WAIT 状态
            channel.config().setOption(ChannelOption.SO_LINGER, 0);
            channel.unsafe().closeForcibly();
            // 记录关闭的连接
            droppedConnections.increment();
            if (loggingScheduled.compareAndSet(false, true)) {
                // 定时打印关闭连接数量的日志
                ctx.executor().schedule(this::writeNumDroppedConnectionsLog, 1, TimeUnit.SECONDS);
            }
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        connections.decrementAndGet();
    }

    private void writeNumDroppedConnectionsLog() {
        loggingScheduled.set(false);
        final long dropped = droppedConnections.sumThenReset();
        if (dropped > 0) {
            logger.warn("断开连接数 [{}], 最大并发数限制 [{}]", dropped, maxConcurrency);
        }
    }


}
