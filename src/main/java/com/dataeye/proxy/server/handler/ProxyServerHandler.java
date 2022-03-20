package com.dataeye.proxy.server.handler;

import com.dataeye.proxy.bean.ProxyRemote;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.TunnelCons;
import com.dataeye.proxy.server.ByteBufUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description 代理服务器处理器
 */
@Component
@Scope("prototype")
@Slf4j
@NoArgsConstructor
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {

    private String id;
    private Channel clientChannel;
    private Channel remoteChannel;
    private static final String CONNECT_ESTABLISHED = "HTTP/1.1 200 Connection Established\r\n\r\n";
    @Autowired
    private ByteBufUtils byteBufUtils;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Autowired
    private IpSelector ipSelector;

    public ProxyServerHandler(String id) {
        this.id = id;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.debug("代理服务器建立连接...");
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
        if (byteBufUtils.isComplete()) {
            log.info("代理服务器转发数据...");
            remoteChannel.writeAndFlush(msg);
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        // 读取数据
        byteBufUtils.digest(in);
        if (!byteBufUtils.isComplete()) {
            // 释放数据
            in.release();
            return;
        }
        log.info("{} {}", id, byteBufUtils);
        // disable AutoRead until remote connection is ready
        clientChannel.config().setAutoRead(false);
        // if https, respond 200 to create tunnel
        if (byteBufUtils.isHttps()) {
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(CONNECT_ESTABLISHED.getBytes()));
        }

        // 发送代理请求
        sendProxyRequest(in);
    }

    /**
     * 发送代理请求
     *
     * @param in
     */
    private void sendProxyRequest(ByteBuf in) throws IOException {
        log.info("发送代理请求...");
        String localHost = proxyServerConfig.getHost();
        int localPort = proxyServerConfig.getPort();
        // 获取随机代理ip
//        String proxyIp = ipSelector.getProxyIp(localHost, localPort);
//        log.info("获取的随机代理ip为：{}", proxyIp);
//        String tunnelProxyServerIp = proxyServerConfig.getTunnelProxyServerIp();
//        int tunnelProxyServerPort = proxyServerConfig.getTunnelProxyServerPort();

        // 创建一个客户端，发送请求
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientChannel.eventLoop())
//                .localAddress(proxyIp, localPort)
//                .localAddress(TunnelCons.IP, TunnelCons.PORT)
//                .localAddress(tunnelProxyServerIp, tunnelProxyServerPort)
                .channel(clientChannel.getClass())
                .handler(applicationContext.getBean(ProxyRemoteHandler.class, id, clientChannel));
//                .bind(TunnelCons.IP, TunnelCons.PORT);

        // 获取远程地址
        String remoteHost = byteBufUtils.getHost();
        int remotePort = byteBufUtils.getPort();
        // 开始连接远程地址
        log.debug("远程地址: {}, 远程端口: {}", remoteHost, remotePort);
        ChannelFuture remoteFuture = bootstrap.connect(remoteHost, remotePort);
        remoteChannel = remoteFuture.channel();

        // 远程连接监听
        remoteFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 连接已经准备就绪，开启自动读取
                clientChannel.config().setAutoRead(true);
                if (!byteBufUtils.isHttps()) {
                    // 转发请求头和剩下的字节
                    remoteChannel.write(byteBufUtils.getByteBuf());
                }
                remoteChannel.writeAndFlush(in);
            } else {
                in.release();
                clientChannel.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("代理服务器断开连接...");
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        log.error("{} 代理服务器发生异常 : {}", id, e.getMessage());
        e.printStackTrace();
        flushAndClose(clientChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
