package com.dataeye.proxy.apn.handler;

import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 并发连接限制
 *
 * @author jaret
 * @date 2022/4/14 11:46
 */
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("ConnectionLimitHandler");

    private final int maxConcurrency;
    private final AtomicLong numConnections = new AtomicLong(0);
    private final LongAdder numDroppedConnections = new LongAdder();
    private final AtomicBoolean loggingScheduled = new AtomicBoolean(false);
    private final Set<Channel> childChannelSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ConnectionLimitHandler(TunnelInstance tunnelInstance) {
        this.maxConcurrency = tunnelInstance.getConcurrency();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = (Channel) msg;
        long conn = numConnections.incrementAndGet();
        if (conn > 0 && conn <= maxConcurrency) {
            this.childChannelSet.add(channel);
            channel.closeFuture().addListener(future -> {
                // 通道关闭了，就移除channel，并减 1
                childChannelSet.remove(channel);
                numConnections.decrementAndGet();
            });
            super.channelRead(ctx, msg);
            logger.info("当前连接数：{}, ");
        } else {
            numConnections.decrementAndGet();
            // 设置 linger 选项为 0，是为了 server 不会获取到太多的 TIME_WAIT 状态
            channel.config().setOption(ChannelOption.SO_LINGER, 0);
            channel.unsafe().closeForcibly();
            // 记录关闭的连接
            numDroppedConnections.increment();
            if (loggingScheduled.compareAndSet(false, true)) {
                // 定时打印关闭连接数量的日志
                ctx.executor().schedule(this::writeNumDroppedConnectionsLog, 1, TimeUnit.SECONDS);
            }
        }
    }

    private void writeNumDroppedConnectionsLog() {
        loggingScheduled.set(false);
        final long dropped = numDroppedConnections.sumThenReset();
        if (dropped > 0) {
            logger.warn("断开连接数 [{}], 总并发数限制 [{}]", dropped, maxConcurrency);
        }
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public AtomicLong getNumConnections() {
        return numConnections;
    }

    public LongAdder getNumDroppedConnections() {
        return numDroppedConnections;
    }

    public Set<Channel> getChildChannels() {
        return childChannelSet;
    }

}
