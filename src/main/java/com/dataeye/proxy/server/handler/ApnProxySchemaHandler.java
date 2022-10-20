package com.dataeye.proxy.server.handler;


import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.cons.GlobalParams;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxySchemaHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.schema";
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxySchemaHandler(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        GlobalParams.LOGGER.debug("schema channelActive");
        setProxyIp(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        GlobalParams.LOGGER.debug("schema channelRead");
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest httpRequest = (FullHttpRequest) msg;
            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            } else {
                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
            }
            String name = httpRequest.method().name();
            String uri = httpRequest.uri();
            int reqSize = SocksServerUtils.getReqSize(httpRequest);
            String reqSrcIp = SocksServerUtils.getReqSrcIp(ctx);
            apnHandlerParams.getRequestMonitor().setSrcIp(reqSrcIp);
            apnHandlerParams.getRequestMonitor().setMethod(name);
            apnHandlerParams.getRequestMonitor().setUri(uri);
            apnHandlerParams.getRequestMonitor().getRequestSize().addAndGet(reqSize);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        GlobalParams.LOGGER.debug("schema channelInactive");
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        GlobalParams.LOGGER.error("schema exceptionCaught", cause);
        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }

    void setProxyIp(ChannelHandlerContext ctx) throws InterruptedException {
        // 随时更新隧道配置
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInitService().getDefaultTunnel();
        if (Objects.nonNull(tunnelInstance)) {
            apnHandlerParams.setTunnelInstance(tunnelInstance);
        }
        // 分配ip
        ProxyIp proxyIp = apnHandlerParams.getCommonIpSelector().getOne();
        if (Objects.isNull(proxyIp)) {
            GlobalParams.LOGGER.error("分配ip失败, proxyIp is null");
            apnHandlerParams.getRequestDistributeService().handleProxyIpIsEmpty(ctx);
        } else {
            ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).set(proxyIp);
            // ip, 请求监控
            RequestMonitor requestMonitor = new RequestMonitor();
            requestMonitor.setTunnelName(tunnelInstance.getAlias());
            requestMonitor.setProxyAddr(proxyIp.getIpAddr());
            requestMonitor.setExpireTime(proxyIp.getExpireTime());
            requestMonitor.setSuccess(true);
            requestMonitor.setBegin(System.currentTimeMillis());
            apnHandlerParams.setRequestMonitor(requestMonitor);
            IpMonitorUtils.invoke(true, requestMonitor, true, HANDLER_NAME);
        }
    }

}
