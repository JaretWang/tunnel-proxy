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


import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyForwardHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");

    public static final String HANDLER_NAME = "apnproxy.forward";
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyForwardHandler 接收请求, 请求内容: {}", httpRequest.toString());
            requestDistributeService.sendRequestByForward(apnHandlerParams, httpRequest, httpContentBuffer, ctx, msg);
            ReferenceCountUtil.release(msg);
        } else {
            logger.info("缓存 HttpContent");
            HttpContent hc = ((HttpContent) msg);
            httpContentBuffer.add(hc);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("UA channel inactive");
        ctx.close();
//        SocksServerUtils.closeOnFlush(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

}