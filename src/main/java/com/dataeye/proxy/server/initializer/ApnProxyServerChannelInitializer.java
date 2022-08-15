package com.dataeye.proxy.server.initializer;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.server.handler.*;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/5/9 14:45
 */
public class ApnProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    public static final String SERVER_CODEC_NAME = "server.codec";
    public static final String SERVER_REQUEST_AGG_NAME = "server.request.agg";
    public static final String SERVER_REQUEST_DECOMPRESSOR_NAME = "server.request.decompressor";
    public static final String SERVER_BANDWIDTH_MONITOR_NAME = "server.bandwidth.monitor";
    public static final String SERVER_IDLE_STATE_NAME = "idlestate";
    public static final String SERVER_IDLE_HANDLER_NAME = "idlehandler";
    public static final long SERVER_READ_IDLE_TIME = 5, SERVER_WRITE_IDLE_TIME = 5, SERVER_ALL_IDLE_TIME = 10;
    public static final long CLIENT_READ_IDLE_TIME = 3, CLIENT_WRITE_IDLE_TIME = 3, CLIENT_ALL_IDLE_TIME = 6;
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyServerChannelInitializer(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

//        pipeline.addLast("idlestate", new ReadTimeoutHandler(readerIdleTime, TimeUnit.SECONDS));
//        pipeline.addLast("idlestate", new WriteTimeoutHandler(writerIdleTime, TimeUnit.SECONDS));
        pipeline.addLast("idlestate", new IdleStateHandler(SERVER_READ_IDLE_TIME, SERVER_WRITE_IDLE_TIME, SERVER_ALL_IDLE_TIME, TimeUnit.SECONDS));
        pipeline.addLast("idlehandler", new IdleHandler("server"));
//        pipeline.addLast("server.datalog", new LoggingHandler("PRE_BYTE_LOGGER", LogLevel.DEBUG));
//        if (ApnProxyConfig.getConfig().getListenType() == ApnProxyListenType.SSL) {
//            SSLEngine engine = ApnProxySSLContextFactory.createServerSSLSSLEngine();
//            pipeline.addLast("apnproxy.encrypt", new SslHandler(engine));
//        }
//        pipeline.addLast("log", new LoggingHandler("BYTE_LOGGER", LogLevel.INFO));
        pipeline.addLast(SERVER_CODEC_NAME, new HttpServerCodec());
        pipeline.addLast(SERVER_REQUEST_AGG_NAME, new HttpObjectAggregator(1024 * 1024));
        pipeline.addLast(SERVER_REQUEST_DECOMPRESSOR_NAME, new HttpContentDecompressor());
//        pipeline.addLast("chunked_write", new ChunkedWriteHandler());
//        pipeline.addLast(ApnProxyPreHandler.HANDLER_NAME, new ApnProxyPreHandler());

        // 频率监控
        pipeline.addLast(ConcurrentLimitHandler.HANDLER_NAME, apnHandlerParams.getConcurrentLimitHandler());
        // 带宽监控
//        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
//        int maxNetBandwidth = tunnelInstance.getMaxNetBandwidth();
//        // 单位：byte
//        long writeGlobalLimit = maxNetBandwidth * 1024 * 1024;
//        // 单位：byte
//        long readGlobalLimit = maxNetBandwidth * 1024 * 1024;
//        // 单位：byte
//        long writeChannelLimit = 400 * 1024;
//        // 单位：byte
//        long readChannelLimit = 400 * 1024;
//        // 单位：毫秒
//        long checkInterval = 1000;
//        // 单位：毫秒
//        long maxTime = 15000;
//        pipeline.addLast(SERVER_BANDWIDTH_MONITOR_NAME, new GlobalChannelTrafficShapingHandler(apnHandlerParams.getTrafficScheduledThreadPool(),
//                writeGlobalLimit, readGlobalLimit, writeChannelLimit, readChannelLimit, checkInterval, maxTime));

        // 请求转发
        pipeline.addLast(ApnProxySchemaHandler.HANDLER_NAME, new ApnProxySchemaHandler(apnHandlerParams));
        pipeline.addLast(ApnProxyForwardHandler.HANDLER_NAME, new ApnProxyForwardHandler(apnHandlerParams));
        pipeline.addLast(ApnProxyTunnelHandler.HANDLER_NAME, new ApnProxyTunnelHandler(apnHandlerParams));
    }

}
