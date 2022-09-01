package com.dataeye.proxy.server.handler;


import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.cons.GlobalParams;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxySchemaHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.schema";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final ApnHandlerParams apnHandlerParams;
    private final RequestMonitor requestMonitor = new RequestMonitor();

    public ApnProxySchemaHandler(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("schema channelActive");
        getProxyIp(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        logger.debug("schema channelRead");
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest httpRequest = (FullHttpRequest) msg;
            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            } else {
                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
            }
            String name = httpRequest.method().name();
            String uri = httpRequest.uri();
            apnHandlerParams.getRequestMonitor().setRequestType(name);
            apnHandlerParams.getRequestMonitor().setTargetAddr(uri);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("schema异常", cause);
        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }

    void getProxyIp(ChannelHandlerContext ctx) throws InterruptedException {
        // 随时更新隧道配置
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInitService().getDefaultTunnel();
        if (Objects.nonNull(tunnelInstance)) {
            apnHandlerParams.setTunnelInstance(tunnelInstance);
        }
        // 分配ip
        ProxyIp proxyIp = apnHandlerParams.getCommonIpSelector().getOne();
        if (Objects.isNull(proxyIp)) {
            logger.error("分配ip失败, proxyIp is null");
            apnHandlerParams.getRequestDistributeService().handleProxyIpIsEmpty(ctx);
        } else {
            logger.debug("分配ip：{}", proxyIp.toString());
            ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).set(proxyIp);
            // ip, 请求监控
            requestMonitor.setTunnelName(tunnelInstance.getAlias());
            requestMonitor.setBegin(System.currentTimeMillis());
            requestMonitor.setProxyAddr(proxyIp.getIpAddr());
            requestMonitor.setExpireTime(proxyIp.getExpireTime());
            requestMonitor.setSuccess(true);
            apnHandlerParams.setRequestMonitor(requestMonitor);
            IpMonitorUtils.invoke(true, requestMonitor, true, HANDLER_NAME);
        }
    }

}
