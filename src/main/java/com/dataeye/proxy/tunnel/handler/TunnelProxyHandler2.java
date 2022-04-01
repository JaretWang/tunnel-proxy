package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.service.ProxyService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jaret
 * @date 2022/3/25 18:14
 * @description
 */
@Deprecated
@Slf4j
public class TunnelProxyHandler2 extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy";
    private final ITunnelDistributeService tunnelDistributeService;
    private final TunnelInstance tunnelInstance;
    private final ProxyService proxyService;

    public TunnelProxyHandler2(ITunnelDistributeService tunnelDistributeService,
                              TunnelInstance tunnelInstance, ProxyService proxyService) {
        this.tunnelDistributeService = tunnelDistributeService;
        this.tunnelInstance = tunnelInstance;
        this.proxyService = proxyService;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
//        log.info("隧道转发请求");

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            log.debug("TunnelProxyHandler 接收到请求内容: {}", httpRequest.toString());
            tunnelDistributeService.sendTunnelProxyRequest(ctx, httpRequest, tunnelInstance, proxyService);
        }
        ReferenceCountUtil.release(msg);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(cause.getMessage(), cause);
        ctx.close();
    }
}
