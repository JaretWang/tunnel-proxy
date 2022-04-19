package com.dataeye.proxy.arloor.handler;

import com.dataeye.proxy.arloor.bean.ArloorHandlerParams;
import com.dataeye.proxy.arloor.monitor.ChannelTrafficMonitor;
import com.dataeye.proxy.arloor.monitor.GlobalTrafficMonitor;
import com.dataeye.proxy.arloor.trace.TraceConstant;
import com.dataeye.proxy.arloor.trace.Tracer;
import com.dataeye.proxy.arloor.vo.HttpConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyServerInitializer.class);

    private final ArloorHandlerParams arloorHandlerParams;

    public HttpProxyServerInitializer(ArloorHandlerParams arloorHandlerParams) throws IOException, GeneralSecurityException {
        this.arloorHandlerParams = arloorHandlerParams;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(GlobalTrafficMonitor.getInstance());
        Span streamSpan = Tracer.spanBuilder(TraceConstant.stream.name())
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(TraceConstant.client.name(), ch.remoteAddress().getHostName())
                .startSpan();
        p.addLast(new HeartbeatIdleStateHandler(5, 0, 0, TimeUnit.MINUTES));
        p.addLast(new ChannelTrafficMonitor(1000, streamSpan));
        p.addLast(new HttpRequestDecoder());
        p.addLast(new HttpResponseEncoder());
        p.addLast(new HttpServerExpectContinueHandler());
        p.addLast(SessionHandShakeHandler.NAME, new SessionHandShakeHandler(streamSpan, arloorHandlerParams));

    }
}
