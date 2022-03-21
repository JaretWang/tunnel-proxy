package com.dataeye.proxy.server;

import com.dataeye.proxy.server.handler.ProxyServerHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description
 */
@Slf4j
@Component
public class ProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final AtomicLong taskCounter = new AtomicLong(0);
    @Autowired
    private ApplicationContext appCtx;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // 添加IdleStateHandler心跳检测处理器，并添加自定义处理Handler类实现userEventTriggered()方法作为超时事件的逻辑处理；
        pipeline.addLast("idle_state", new IdleStateHandler(0, 0, 3, TimeUnit.MINUTES));
        // 设置日志打印级别
        pipeline.addLast("log", new LoggingHandler(LogLevel.DEBUG));
//        pipeline.addLast("codec", new HttpClientCodec());
//        pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
//        // 自动解压内容
//        pipeline.addLast("decompress", new HttpContentDecompressor());
//        pipeline.addLast("handler", new HttpClientHandler());
//        pipeline.addLast(ProxyForwardHandler.HANDLER_NAME, new ProxyForwardHandler());
//        pipeline.addLast(ProxyTunnelHandler.HANDLER_NAME, new ProxyTunnelHandler());

        ProxyServerHandler httpProxyClientHandler = appCtx.getBean(ProxyServerHandler.class, "task-" + taskCounter.getAndIncrement());
        pipeline.addLast("http_proxy", httpProxyClientHandler);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error(cause.getMessage(), cause);
        ctx.close();
    }

}

