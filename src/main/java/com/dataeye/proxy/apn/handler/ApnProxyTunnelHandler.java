package com.dataeye.proxy.apn.handler;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:41
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.tunnel";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyTunnelHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        logger.info("tunnel channelRead");
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
                logger.debug("tunnel 接收请求, 请求行和请求头: {}", fullHttpRequest.toString());
                ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
                if (Objects.isNull(cacheIpResult)) {
                    throw new RuntimeException("tunnel 获取缓存ip为空");
                }
                logger.info("转发 CONNECT 请求 to {} for {}", cacheIpResult.getRemote(), fullHttpRequest.uri());
                TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
                RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
                // send proxy request
                requestMonitor.getRequestSize().addAndGet(fullHttpRequest.content().readableBytes());
                //requestMonitor.getRequestSize().addAndGet(fullHttpRequest.toString().getBytes().length);
                requestDistributeService.forwardConnectReq(requestMonitor, ctx, fullHttpRequest, cacheIpResult, tunnelInstance);
            }
        } finally {
            // 这里的 msg 释放引用就是对 fullHttpRequest 释放引用
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("tunnel channelInactive");
        super.channelInactive(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("tunnel exceptionCaught：{}", cause.getMessage());
        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        IpMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        ctx.close();
    }

}
