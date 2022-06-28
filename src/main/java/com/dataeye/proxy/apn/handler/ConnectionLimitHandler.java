package com.dataeye.proxy.apn.handler;

import com.dataeye.proxy.apn.utils.HttpErrorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
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
@ChannelHandler.Sharable
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.connect.limit";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ConnectionLimitHandler");
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
        synchronized (this) {
            Channel channel = ctx.channel();
            long conn = numConnections.incrementAndGet();
            if (conn > 0 && conn <= maxConcurrency) {
                childChannelSet.add(channel);
                channel.closeFuture().addListener(future -> {
                    // 通道关闭了，就移除channel，并减 1
                    childChannelSet.remove(channel);
                    numConnections.decrementAndGet();
                });
                super.channelRead(ctx, msg);
                ctx.fireChannelRead(msg);
                logger.debug("当前连接数 {}, 最大并发数 {}", conn, maxConcurrency);
            } else {
                logger.debug("当前连接数 {}, 最大并发数 {}", conn, maxConcurrency);
                numConnections.decrementAndGet();
                // 设置 linger 选项为 0，是为了 server 不会获取到太多的 TIME_WAIT 状态
                channel.config().setOption(ChannelOption.SO_LINGER, 0);
                // send error response
                String errorMsg = "超出每秒最大并发数 " + maxConcurrency + ", 请稍后重试";
                logger.error(errorMsg);
                HttpMessage errorResponseMsg = HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                channel.writeAndFlush(errorResponseMsg);
                channel.pipeline().remove(HANDLER_NAME);
                channel.pipeline().remove("bandwidth.monitor");
                channel.pipeline().remove(ApnProxySchemaHandler.HANDLER_NAME);
                channel.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
                channel.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
//                SocksServerUtils.closeOnFlush(channel);
                channel.unsafe().closeForcibly();
//                ReferenceCountUtil.release(msg);

                // 记录关闭的连接
                numDroppedConnections.increment();
                if (loggingScheduled.compareAndSet(false, true)) {
                    // 定时打印关闭连接数量的日志
                    ctx.executor().schedule(this::writeNumDroppedConnectionsLog, 1, TimeUnit.SECONDS);
                }
            }
//            ctx.fireChannelRead(msg);
//        ReferenceCountUtil.release(msg);
        }
    }

    private void writeNumDroppedConnectionsLog() {
        loggingScheduled.set(false);
        // 计算总和，然后重置
        final long dropped = numDroppedConnections.sumThenReset();
        if (dropped > 0) {
            logger.debug("断开连接数 [{}], 最大并发数 [{}]", dropped, maxConcurrency);
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
