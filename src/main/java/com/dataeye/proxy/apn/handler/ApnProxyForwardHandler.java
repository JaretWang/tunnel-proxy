package com.dataeye.proxy.apn.handler;


import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.forward";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws IOException {
        logger.debug("forward channelRead");
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
                logger.debug("forward 接收请求, 请求行和请求头: {}", fullHttpRequest.toString());
                ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
                if (Objects.isNull(cacheIpResult)) {
                    throw new RuntimeException("forward 获取缓存ip为空");
                }
                final Channel uaChannel = ctx.channel();
                logger.debug("转发普通请求 to {} for {}", cacheIpResult.getRemote(), fullHttpRequest.uri());
                // send proxy request
                apnHandlerParams.getRequestMonitor().getRequestSize().addAndGet(fullHttpRequest.toString().getBytes().length);
                requestDistributeService.sendReqByOkHttp(uaChannel, cacheIpResult, apnHandlerParams, fullHttpRequest, "forward");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("forward channelInactive");
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("forward exceptionCaught：{}", cause.getMessage());
        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        IpMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        ctx.close();
    }

}