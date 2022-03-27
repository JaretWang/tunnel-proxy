package com.dataeye.proxy.http;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.http.handler.ThreadForwardHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author jaret
 * @date 2022/3/23 17:06
 * @description
 */
@Slf4j
@Component
public class ThreadForwardInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Resource(name = "ioThreadPool")
    private ThreadPoolTaskExecutor ioThreadPool;

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        // http 请求解码
        p.addLast(new HttpRequestDecoder());
        // http 请求编码
        p.addLast(new HttpRequestEncoder());
        // http 异常处理
        p.addLast(new HttpServerExpectContinueHandler());

//        // 解码成 HttpRequest
//        p.addLast(new HttpServerCodec());
//        /*
//        解码成 FullHttpRequest
//        当我们用POST方式请求服务器的时候,如果只是单纯的用HttpServerCodec是无法完全的解析Http POST请求的，因为HttpServerCodec只能获取uri中参数，所以需要加上HttpObjectAggregator。
//        HttpObjectAggregator这个netty的处理器就是为了解决这个问题而来的.它把HttpMessage和HttpContent聚合成为一个FullHttpRquest或者FullHttpRsponse'
//         */
//        p.addLast(new HttpObjectAggregator(HandlerCons.HTTP_OBJECT_AGGREGATOR_SIZE));

        p.addLast(new ThreadForwardHandler(proxyServerConfig, ioThreadPool));
//        p.addLast(new HttpResponseDecoder());
//        p.addLast(new HttpResponseEncoder());

    }
}
