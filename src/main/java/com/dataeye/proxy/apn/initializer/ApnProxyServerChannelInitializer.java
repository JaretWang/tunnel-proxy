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

package com.dataeye.proxy.apn.initializer;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.config.ApnProxyConfig;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.handler.*;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.apn.utils.ApnProxySSLContextFactory;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyServerChannelInitializer(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

//        pipeline.addLast("idlestate", new IdleStateHandler(0, 0, 3, TimeUnit.MINUTES));
//        pipeline.addLast("idlehandler", new IdleHandler());
//        pipeline.addLast("datalog", new LoggingHandler("PRE_BYTE_LOGGER", LogLevel.DEBUG));
//        if (ApnProxyConfig.getConfig().getListenType() == ApnProxyListenType.SSL) {
//            SSLEngine engine = ApnProxySSLContextFactory.createServerSSLSSLEngine();
//            pipeline.addLast("apnproxy.encrypt", new SslHandler(engine));
//        }
//        pipeline.addLast("log", new LoggingHandler("BYTE_LOGGER", LogLevel.INFO));
        pipeline.addLast("codec", new HttpServerCodec());
//        pipeline.addLast("request_object_agg", new HttpObjectAggregator(1024*1024));
//        pipeline.addLast("chunked_write", new ChunkedWriteHandler());
//        pipeline.addLast(ApnProxyPreHandler.HANDLER_NAME, new ApnProxyPreHandler());
        pipeline.addLast(ApnProxySchemaHandler.HANDLER_NAME, new ApnProxySchemaHandler(apnHandlerParams));
        pipeline.addLast(ApnProxyForwardHandler.HANDLER_NAME, new ApnProxyForwardHandler(apnHandlerParams));
        pipeline.addLast(ApnProxyTunnelHandler.HANDLER_NAME, new ApnProxyTunnelHandler(apnHandlerParams));
    }
}
