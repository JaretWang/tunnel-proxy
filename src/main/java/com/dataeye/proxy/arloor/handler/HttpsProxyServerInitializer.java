package com.dataeye.proxy.arloor.handler;

import com.dataeye.proxy.arloor.bean.ArloorHandlerParams;
import com.dataeye.proxy.arloor.monitor.ChannelTrafficMonitor;
import com.dataeye.proxy.arloor.monitor.GlobalTrafficMonitor;
import com.dataeye.proxy.arloor.ssl.SslContextFactory;
import com.dataeye.proxy.arloor.trace.TraceConstant;
import com.dataeye.proxy.arloor.trace.Tracer;
import com.dataeye.proxy.arloor.vo.SslConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.ssl.SslContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class HttpsProxyServerInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger log = LoggerFactory.getLogger(HttpsProxyServerInitializer.class);

    private final SslConfig sslConfig;

    private SslContext sslCtx;
    private final ArloorHandlerParams arloorHandlerParams;

    public HttpsProxyServerInitializer(ArloorHandlerParams arloorHandlerParams) {
        this.arloorHandlerParams = arloorHandlerParams;
        this.sslConfig = arloorHandlerParams.getSslConfig();
    }

//    public HttpsProxyServerInitializer(SslConfig sslConfig) {
//        this.sslConfig = sslConfig;
//        loadSslContext();
//    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(GlobalTrafficMonitor.getInstance());
        p.addLast(new HeartbeatIdleStateHandler(5, 0, 0, TimeUnit.MINUTES));
        Span streamSpan = Tracer.spanBuilder(TraceConstant.stream.name())
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(TraceConstant.client.name(), ch.remoteAddress().getHostName())
                .startSpan();
        p.addLast(new ChannelTrafficMonitor(1000, streamSpan));
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }
        p.addLast(new HttpRequestDecoder());
        p.addLast(new HttpResponseEncoder());
        p.addLast(new HttpServerExpectContinueHandler());
//        p.addLast(SessionHandShakeHandler.NAME, new SessionHandShakeHandler(sslConfig.getAuthMap(), streamSpan, new HashSet<>()));
        p.addLast(SessionHandShakeHandler.NAME, new SessionHandShakeHandler(streamSpan, arloorHandlerParams));

    }

    public void loadSslContext() {
        try {
            this.sslCtx = SslContextFactory.getSSLContext(sslConfig.getFullchain(), sslConfig.getPrivkey());
        } catch (Throwable e) {
            log.error("init ssl context error!", e);
        }
    }
}
