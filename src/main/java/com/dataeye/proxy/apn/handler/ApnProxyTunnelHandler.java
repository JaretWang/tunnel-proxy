/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

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
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:41
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.tunnel";
    //    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyTunnelHandler");
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;
    private final NioEventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(1);

    public ApnProxyTunnelHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("tunnel channelActive");
    }


    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        logger.info("tunnel channelRead");

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            logger.debug("tunnel 接收请求, 请求内容: {}", httpRequest.toString());
            ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            if (Objects.nonNull(cacheIpResult)) {
                logger.info("tunnel 检测到缓存ip: {}", JSON.toJSONString(cacheIpResult));
                requestDistributeService.sendRequestByTunnel(clientEventLoopGroup, cacheIpResult, apnHandlerParams, ctx, httpRequest);
            } else {
                throw new RuntimeException("tunnel 获取缓存ip为空");
            }
        } else {
            logger.info("tunnel: 非HttpRequest类型，具体：{}", msg.getClass());
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("tunnel channelReadComplete");

        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("tunnel channelInactive");

        super.channelInactive(ctx);
//        ctx.fireChannelInactive();
        // todo 为了测试 too many files
//        ctx.channel().closeFuture().sync();
//        ctx.channel().eventLoop().shutdownGracefully();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("tunnel exceptionCaught：{}", cause.getMessage());

        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(cause.getMessage());
        ReqMonitorUtils.cost(requestMonitor, HANDLER_NAME);
        IpMonitorUtils.invoke(requestMonitor, false, HANDLER_NAME);

        // todo 为了测试 too many files
//        ctx.channel().closeFuture().sync();
//        ctx.channel().eventLoop().shutdownGracefully();
        ctx.close();
    }

}
