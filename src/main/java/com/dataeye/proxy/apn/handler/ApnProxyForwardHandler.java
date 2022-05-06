package com.dataeye.proxy.apn.handler;


import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.forward";
    //    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;
    private final NioEventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(1);

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("forward channelActive");

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {
        logger.info("forward channelRead");

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.debug("forward 接收请求, 请求内容: {}", httpRequest.toString());
            ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            if (Objects.nonNull(cacheIpResult)) {
                logger.debug("forward 检测到缓存的ip: {}", JSON.toJSONString(cacheIpResult));
                requestDistributeService.sendRequestByForward(clientEventLoopGroup, cacheIpResult, apnHandlerParams, httpRequest, httpContentBuffer, ctx, msg);
            } else {
                throw new RuntimeException("forward 获取缓存ip为空");
            }
            ReferenceCountUtil.release(msg);
        } else {
            HttpContent hc = ((HttpContent) msg);
            httpContentBuffer.add(hc);
            logger.info("缓存HttpContent, size: {}, msg 类型：{}", httpContentBuffer.size(), msg.getClass());
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("forward channelReadComplete");

        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("forward channelInactive");
        // todo 为了测试 too many files
//        ctx.channel().closeFuture().sync();
//        ctx.channel().eventLoop().shutdownGracefully();
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
        // todo 为了测试 too many files
//        ctx.channel().closeFuture().sync();
//        ctx.channel().eventLoop().shutdownGracefully();
    }

}