package com.dataeye.proxy.apn.handler;

import com.dataeye.proxy.apn.utils.HttpErrorUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author jaret
 * @date 2022/4/14 13:15
 * @description 每秒并发连接数控制
 */
@ChannelHandler.Sharable
public class ConcurrentLimitHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.concurrent.limit";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ConcurrentLimitHandler");
    private final int maxConcurrency;
    private final AtomicLong connections = new AtomicLong(0);
    private final LongAdder droppedConnections = new LongAdder();
    private final AtomicLong successConn = new AtomicLong(0);
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
                successConn.incrementAndGet();
            });
            super.channelActive(ctx);
        } else {
            logger.info("触发限流，当前连接数 {}, 超出每秒最大并发数 {}", connections.get(), maxConcurrency);
            connections.decrementAndGet();
            // 设置 linger 选项为 0，是为了 server 不会获取到太多的 TIME_WAIT 状态
            channel.config().setOption(ChannelOption.SO_LINGER, 0);

            // remove
            channel.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
            channel.pipeline().remove("bandwidth.monitor");
            channel.pipeline().remove(ApnProxySchemaHandler.HANDLER_NAME);
            channel.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            channel.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

            // send error response
            String errorMsg = "超出每秒最大并发数 " + maxConcurrency + ", 请稍后重试";
            HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
            channel.writeAndFlush(errorResponseMsg);
            channel.unsafe().closeForcibly();

            // 记录关闭的连接
            droppedConnections.increment();
            if (loggingScheduled.compareAndSet(false, true)) {
                // 定时打印关闭连接数量的日志
                ctx.executor().schedule(this::writeNumDroppedConnectionsLog, 1, TimeUnit.SECONDS);
            }
        }
    }

    private void writeNumDroppedConnectionsLog() {
        loggingScheduled.set(false);
        final long dropped = droppedConnections.sumThenReset();
        if (dropped > 0) {
            logger.warn("正在处理：[{}], 断开连接数 [{}], 已成功处理：[{}], 每秒最大并发数限制 [{}]", connections.get(), dropped, successConn.get(), maxConcurrency);
        }
    }


}
