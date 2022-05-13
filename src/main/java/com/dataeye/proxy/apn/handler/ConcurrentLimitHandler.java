package com.dataeye.proxy.apn.handler;

import com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.apn.utils.HttpErrorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/14 13:15
 * @description 每秒并发连接数控制
 */
@ChannelHandler.Sharable
public class ConcurrentLimitHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.concurrent.limit";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ConcurrentLimitHandler");
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("concurrent-limit-"), new ThreadPoolExecutor.AbortPolicy());
    /**
     * 每10秒,检查一次累计的连接数
     */
    private final int cycle = 10;
    /**
     * 每5秒,限制数量2500, 得出的频率控制: 500个连接/s
     */
    private final int frequnce;
    /**
     * 每秒最大并发数
     */
    private final int maxConcurrency;
    /**
     * 正在处理的连接数
     */
    private final AtomicLong connections = new AtomicLong(0);

    public ConcurrentLimitHandler(TunnelInstance tunnelInstance) {
        this.maxConcurrency = tunnelInstance.getConcurrency();
        this.frequnce = maxConcurrency * cycle;
        Runnable runnable = () -> {
            logger.info("{}s 以内, connections={}", cycle, connections.get());
            connections.set(0);
        };
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(runnable, 0, cycle, TimeUnit.SECONDS);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        boolean control = isControl();
        if (!control) {
            super.channelActive(ctx);
        } else {
            logger.info("触发限流，当前连接数={}, 每秒最大并发数={}", connections.get(), maxConcurrency);
            // 设置 linger 选项为 0，是为了 server 不会获取到太多的 TIME_WAIT 状态
            channel.config().setOption(ChannelOption.SO_LINGER, 0);

            // remove
            channel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_AGG_NAME);
            channel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME);
            channel.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
//            channel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_BANDWIDTH_MONITOR_NAME);
            channel.pipeline().remove(ApnProxySchemaHandler.HANDLER_NAME);
            channel.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            channel.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

            // send error response
            String errorMsg = "超出每秒最大并发数 " + maxConcurrency + ", 请稍后重试";
            HttpMessage errorResponseMsg = HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN, errorMsg);
            channel.writeAndFlush(errorResponseMsg);
            channel.unsafe().closeForcibly();
        }
    }

    public boolean isControl() {
        return connections.incrementAndGet() > frequnce;
    }

}