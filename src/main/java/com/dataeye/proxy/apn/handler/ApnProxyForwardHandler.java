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

//    @Override
//    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        logger.info("forward channelActive");
//        super.channelActive(ctx);
//    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {
        logger.info("forward channelRead");
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
                logger.debug("forward 接收请求, 请求行和请求头: {}", fullHttpRequest.toString());
                ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
                if (Objects.isNull(cacheIpResult)) {
                    throw new RuntimeException("forward 获取缓存ip为空");
                }
                final Channel uaChannel = ctx.channel();
                logger.info("转发普通请求 to {} for {}", cacheIpResult.getRemote(), fullHttpRequest.uri());
                // send proxy request
//            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
//            requestDistributeService.forwardCommonReq(uaChannel, apnHandlerParams, cacheIpResult, tunnelInstance, httpContentBuffer, msg);
                apnHandlerParams.getRequestMonitor().getRequestSize().addAndGet(fullHttpRequest.content().readableBytes());
//                apnHandlerParams.getRequestMonitor().getRequestSize().addAndGet(fullHttpRequest.toString().getBytes().length);
                requestDistributeService.sendReqByOkHttp(uaChannel, cacheIpResult, apnHandlerParams, fullHttpRequest, "forward");
            } else {
                logger.warn("forward 未识别类型: {}", msg.getClass());
            }
        } catch (Throwable e) {
            logger.error("forward channelRead exception: {}", e.getMessage());
        } finally {
            boolean release = ReferenceCountUtil.release(msg);
//            System.out.println("forward release=" + release);
        }
    }

//    @Override
//    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        logger.info("forward channelReadComplete");
//        super.channelReadComplete(ctx);
//    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("forward channelInactive");
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("forward exceptionCaught：{}", cause.getMessage());
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(cause.getMessage());
        ReqMonitorUtils.cost(requestMonitor, HANDLER_NAME);
        IpMonitorUtils.invoke(requestMonitor, false, HANDLER_NAME);
        ctx.close();
    }

}