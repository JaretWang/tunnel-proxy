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
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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

//    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    public static final String HANDLER_NAME = "apnproxy.forward";
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;

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
                requestDistributeService.sendRequestByForward(cacheIpResult, apnHandlerParams, httpRequest, httpContentBuffer, ctx, msg);
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

        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("forward exceptionCaught");

        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

}