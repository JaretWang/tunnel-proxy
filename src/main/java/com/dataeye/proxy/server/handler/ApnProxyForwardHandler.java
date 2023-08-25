package com.dataeye.proxy.server.handler;


import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.cons.GlobalParams;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.server.service.RequestDistributeService;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
@Slf4j
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.forward";
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("forward channelActive");
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws IOException {
        log.debug("forward channelRead");
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
                log.debug("forward 接收请求, 请求行和请求头: {}", fullHttpRequest.toString());
//                ProxyIp proxyIp = ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).get();
                ProxyIp proxyIp = GlobalParams.getProxyIp(ctx);
                if (Objects.isNull(proxyIp)) {
                    throw new RuntimeException("forward 获取缓存ip为空");
                }
                log.debug("转发普通请求, proxy={}, srcIp={}, method={}, uri={}",
                        proxyIp.getRemote(), SocksServerUtils.getReqSrcIp(ctx), requestMonitor.getMethod(), requestMonitor.getUri());
                final Channel uaChannel = ctx.channel();
                requestDistributeService.sendReqByOkHttp(uaChannel, proxyIp, apnHandlerParams, fullHttpRequest, "forward");
            }
        } catch (Exception e) {
            log.error("forward异常, 关闭通道, srcIp={}, method={}, uri={}, cause={}",
                    SocksServerUtils.getReqSrcIp(ctx), requestMonitor.getMethod(), requestMonitor.getUri(), e.getMessage(), e);
            SocksServerUtils.errorHttpResp(ctx.channel(), e.getMessage());
            SocksServerUtils.closeOnFlush(ctx.channel());
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("forward channelInactive");
        ctx.close();
        ProxyIp.removeConnect(GlobalParams.getProxyIp(ctx));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        log.error("forward exceptionCaught, srcIp={}, method={}, uri={}, targetAddr={}, cause={}",
                SocksServerUtils.getReqSrcIp(ctx), requestMonitor.getMethod(), requestMonitor.getUri(), requestMonitor.getUri(), cause.getMessage(), cause);
        ReqMonitorUtils.error(requestMonitor, HANDLER_NAME, cause.getMessage());
        IpMonitorUtils.error(requestMonitor, HANDLER_NAME, cause.getMessage());
        ctx.close();
        ProxyIp.removeConnect(GlobalParams.getProxyIp(ctx));
    }

}