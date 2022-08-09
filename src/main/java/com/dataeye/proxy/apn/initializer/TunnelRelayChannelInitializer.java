package com.dataeye.proxy.apn.initializer;

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.handler.IdleHandler;
import com.dataeye.proxy.apn.handler.TunnelRelayHandler;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.utils.ApnProxySSLContextFactory;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/5/9 16:03
 */
public class TunnelRelayChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final Channel uaChannel;
    private final ApnProxyRemote apnProxyRemote;
    private final RequestMonitor requestMonitor;
    private final TunnelInstance tunnelInstance;

    public TunnelRelayChannelInitializer(RequestMonitor requestMonitor, ApnProxyRemote apnProxyRemote, Channel uaChannel,
                                         TunnelInstance tunnelInstance) {
        this.requestMonitor = requestMonitor;
        this.apnProxyRemote = apnProxyRemote;
        this.uaChannel = uaChannel;
        this.tunnelInstance = tunnelInstance;
    }

    /**
     * @see ChannelInitializer#initChannel(Channel)
     */
    @Override
    protected void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();
        if (apnProxyRemote.getRemoteListenType() == ApnProxyListenType.SSL) {
            SSLEngine engine = ApnProxySSLContextFactory.createSslEngine(
                    apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort());
            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }
        if (apnProxyRemote.getRemoteListenType() == ApnProxyListenType.PLAIN) {
            // nothing to do
        }
//        pipeline.addLast("client.datalog", new LoggingHandler("CLIENT_BYTE_LOGGER", LogLevel.DEBUG));
        pipeline.addLast("client_idlestate", new IdleStateHandler(
                ApnProxyServerChannelInitializer.CLIENT_READ_IDLE_TIME,
                ApnProxyServerChannelInitializer.CLIENT_WRITE_IDLE_TIME,
                ApnProxyServerChannelInitializer.CLIENT_ALL_IDLE_TIME, TimeUnit.SECONDS));
        pipeline.addLast("client_idlehandler", new IdleHandler("client"));
//        pipeline.addLast("client_read_timeout", new ReadTimeoutHandler(tunnelInstance.getReadTimeoutSeconds()));
//        pipeline.addLast("client_write_timeout", new WriteTimeoutHandler(tunnelInstance.getWriteTimeoutSeconds()));
        pipeline.addLast(new TunnelRelayHandler(requestMonitor,  apnProxyRemote.getIpAddr() + " --> UA", uaChannel));

    }
}
