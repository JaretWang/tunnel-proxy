package com.dataeye.proxy.server.handler;


import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.cons.GlobalParams;
import com.dataeye.proxy.server.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.server.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.server.service.RequestDistributeService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.ReqMonitorUtils;
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
        getZhiMaIp(ctx);
        IpMonitorUtils.invoke(true, requestMonitor, true, HANDLER_NAME);
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
            apnHandlerParams.getRequestMonitor().setRequestType(httpRequest.method().name());
            apnHandlerParams.getRequestMonitor().setTargetAddr(httpRequest.uri());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("schema异常", cause);
        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    /**
     * 获取芝麻ip
     *
     * @param ctx
     * @throws InterruptedException
     */
    void getZhiMaIp(ChannelHandlerContext ctx) throws InterruptedException {
        // 随时更新 tunnelInstance
        TunnelInitService tunnelInitService = apnHandlerParams.getTunnelInitService();
        if (Objects.nonNull(tunnelInitService)) {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            if (Objects.nonNull(defaultTunnel)) {
                apnHandlerParams.setTunnelInstance(defaultTunnel);
            }
        }
        // 分配ip
        ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
        RequestDistributeService requestDistributeService = apnHandlerParams.getRequestDistributeService();
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
        if (Objects.isNull(apnProxyRemote)) {
            requestDistributeService.handleProxyIpIsEmpty(ctx);
        }
        logger.debug("分配ip结果：{}", apnProxyRemote);
        ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);

        // ip, 请求监控
        requestMonitor.setTunnelName(tunnelInstance.getAlias());
        requestMonitor.setBegin(System.currentTimeMillis());
        requestMonitor.setProxyAddr(apnProxyRemote.getIpAddr());
        requestMonitor.setExpireTime(apnProxyRemote.getExpireTime());
        requestMonitor.setSuccess(true);
        apnHandlerParams.setRequestMonitor(requestMonitor);
    }

}
