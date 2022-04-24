package com.dataeye.proxy.apn.initializer;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.handler.ApnProxyForwardHandler;
import com.dataeye.proxy.apn.handler.ApnProxySchemaHandler;
import com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler;
import com.dataeye.proxy.apn.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyServerChannelInitializer(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

//        pipeline.addLast("idlestate", new IdleStateHandler(0, 0, 3, TimeUnit.MINUTES));
//        pipeline.addLast("idlehandler", new IdleHandler());
//        pipeline.addLast("datalog", new LoggingHandler("PRE_BYTE_LOGGER", LogLevel.DEBUG));
//        if (ApnProxyConfig.getConfig().getListenType() == ApnProxyListenType.SSL) {
//            SSLEngine engine = ApnProxySSLContextFactory.createServerSSLSSLEngine();
//            pipeline.addLast("apnproxy.encrypt", new SslHandler(engine));
//        }
//        pipeline.addLast("log", new LoggingHandler("BYTE_LOGGER", LogLevel.INFO));
        pipeline.addLast("codec", new HttpServerCodec());
//        pipeline.addLast("request_object_agg", new HttpObjectAggregator(1024*1024));
//        pipeline.addLast("chunked_write", new ChunkedWriteHandler());
//        pipeline.addLast(ApnProxyPreHandler.HANDLER_NAME, new ApnProxyPreHandler());

        // 频率监控
        pipeline.addLast(ConcurrentLimitHandler.HANDLER_NAME, apnHandlerParams.getConcurrentLimitHandler());
        // 带宽监控
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        int maxNetBandwidth = tunnelInstance.getMaxNetBandwidth();
        // 单位：byte
        long writeGlobalLimit = maxNetBandwidth * 1024 * 1024;
        // 单位：byte
        long readGlobalLimit = maxNetBandwidth * 1024 * 1024;
        // 单位：byte
        long writeChannelLimit = 400 * 1024;
        // 单位：byte
        long readChannelLimit = 400 * 1024;
        // 单位：毫秒
        long checkInterval = 1000;
        // 单位：毫秒
        long maxTime = 15000;

        pipeline.addLast("bandwidth.monitor", new GlobalChannelTrafficShapingHandler(apnHandlerParams.getTrafficScheduledThreadPool(),
                writeGlobalLimit, readGlobalLimit, writeChannelLimit, readChannelLimit, checkInterval, maxTime));
        // 请求转发
        pipeline.addLast(ApnProxySchemaHandler.HANDLER_NAME, new ApnProxySchemaHandler(apnHandlerParams));
        pipeline.addLast(ApnProxyForwardHandler.HANDLER_NAME, new ApnProxyForwardHandler(apnHandlerParams));
        pipeline.addLast(ApnProxyTunnelHandler.HANDLER_NAME, new ApnProxyTunnelHandler(apnHandlerParams));
    }

}
