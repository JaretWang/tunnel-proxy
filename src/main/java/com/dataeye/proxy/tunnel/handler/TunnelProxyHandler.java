package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.TunnelManageConfig;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.tunnel.initializer.TunnelClientChannelInitializer;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.UnsupportedEncodingException;
import java.util.Set;

/**
 * @author jaret
 * @date 2022/3/25 18:14
 * @description
 */
@Slf4j
public class TunnelProxyHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy";

    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;
    private final ThreadPoolTaskExecutor ioThreadPool;
    private final ITunnelDistributeService tunnelDistributeService;
    private final TunnelManageConfig tunnelManageConfig;
    private final TunnelInstance tunnelInstance;
//    private HttpRequest cacheRequest;

    public TunnelProxyHandler(ProxyServerConfig proxyServerConfig,
                              ProxySslContextFactory proxySslContextFactory,
                              ThreadPoolTaskExecutor ioThreadPool,
                              ITunnelDistributeService tunnelDistributeService,
                              TunnelManageConfig tunnelManageConfig,
                              TunnelInstance tunnelInstance) {
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
        this.ioThreadPool = ioThreadPool;
        this.tunnelDistributeService = tunnelDistributeService;
        this.tunnelManageConfig = tunnelManageConfig;
        this.tunnelInstance = tunnelInstance;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            log.debug("测试 connect 转发..............");
//            final HttpRequest httpRequest = (HttpRequest) msg;
            final FullHttpRequest httpRequest = (FullHttpRequest) msg;
            log.debug("TunnelProxyHandler 接收到请求内容: {}", httpRequest.toString());
//            // 分发隧道的参数
//            TunnelAllocateResult allocateResult = tunnelDistributeService.getDistributeParams(httpRequest);
//            // 提交代理请求任务
//            ProxyRequestTask proxyRequestTask = new ProxyRequestTask(ctx, httpRequest, allocateResult);
//            ioThreadPool.submit(proxyRequestTask);
//            log.info("提交一个任务");
            tunnelDistributeService.sendProxyRequest(ctx, httpRequest, tunnelInstance);
        }
        ReferenceCountUtil.release(msg);
//        if (msg instanceof HttpRequest) {
//            log.warn("读取请求头, 消息类型：{}", msg.getClass());
//            final HttpRequest httpRequest = (HttpRequest) msg;
//            cacheRequest = (HttpRequest) msg;
//            log.info("TunnelProxyHandler 接收到请求内容: {}", httpRequest.toString());
//        } else if (msg instanceof HttpContent) {
//            log.warn("读取body...");
//            // 提交代理请求任务
//            ProxyRequestTask proxyRequestTask = new ProxyRequestTask(ctx, cacheRequest);
//            ioThreadPool.submit(proxyRequestTask);
//        } else {
//            ReferenceCountUtil.release(msg);
//        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(cause.getMessage(), cause);
        ctx.close();
    }
}
