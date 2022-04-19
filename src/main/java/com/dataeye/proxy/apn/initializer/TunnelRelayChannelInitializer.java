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

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.handler.TunnelRelayHandler;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxySslRemote;
import com.dataeye.proxy.apn.utils.ApnProxySSLContextFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.initializer.ApnProxyTunnelChannelInitializer 14-1-8 16:13 (xmx) Exp $
 */
public class TunnelRelayChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final Channel uaChannel;
    private final ApnProxyRemote apnProxyRemote;
    private final RequestMonitor requestMonitor;

    public TunnelRelayChannelInitializer(RequestMonitor requestMonitor,  ApnProxyRemote apnProxyRemote, Channel uaChannel) {
        this.requestMonitor = requestMonitor;
        this.apnProxyRemote = apnProxyRemote;
        this.uaChannel = uaChannel;
    }

    /**
     * @see ChannelInitializer#initChannel(Channel)
     */
    @Override
    protected void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();

        if (apnProxyRemote.getRemoteListenType() == ApnProxyListenType.SSL) {
            ApnProxySslRemote sslRemote = (ApnProxySslRemote) apnProxyRemote;
            SSLEngine engine = ApnProxySSLContextFactory.createClientSSLEnginForRemoteAddress(
                    sslRemote.getRemoteHost(), sslRemote.getRemotePort());
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        if (apnProxyRemote.getRemoteListenType() == ApnProxyListenType.PLAIN) {
            // nothing to do
        }

//        pipeline.addLast(new ApnProxyRelayHandler(requestMonitor, apnProxyRemote.getRemote() + " --> UA", uaChannel));
        pipeline.addLast(new TunnelRelayHandler(requestMonitor,  "Remote --> UA", uaChannel));

    }
}
