package com.dataeye.proxy.server;

import com.dataeye.proxy.server.handler.ProxyRemoteHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/19 15:14
 * @description 用于连接远程地址
 */
@Slf4j
@Component
public class ProxyClient {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 启动一个代理客户端，并请求远程地址
     *
     * @param in            proxy server 传递过来的字节流
     * @param byteBufUtils  提取http请求头：用于获取请求的远程地址和端口
     * @param clientChannel 代理客户端通道：用于处理入站数据
     * @param remoteChannel 客户端连接后返回的数据通道
     * @param taskId        任务id
     */
    private void start(ByteBuf in, ByteBufUtils byteBufUtils, Channel clientChannel, Channel remoteChannel, String taskId) {
        log.info("启动一个代理客户端，并请求远程地址...");
        Bootstrap bootstrap = new Bootstrap();
        // 使用相同的 EventLoop
        bootstrap.group(clientChannel.eventLoop())
                .channel(clientChannel.getClass())
                .handler(applicationContext.getBean(ProxyRemoteHandler.class, taskId, clientChannel));

        // 开始连接远程地址
        String remoteHost = byteBufUtils.getHost();
        int remotePort = byteBufUtils.getPort();
        log.debug("远程ip:{}, 远程端口:{}", remoteHost, remotePort);
        ChannelFuture remoteFuture = bootstrap.connect(remoteHost, remotePort);
        remoteChannel = remoteFuture.channel();

        // 远程连接监听
        Channel finalRemoteChannel = remoteChannel;
        remoteFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 连接已经准备就绪，开启自动读取
                clientChannel.config().setAutoRead(true);
                if (!byteBufUtils.isHttps()) {
                    // 转发请求头和剩下的字节
                    finalRemoteChannel.write(byteBufUtils.getByteBuf());
                }
                finalRemoteChannel.writeAndFlush(in);
            } else {
                in.release();
                clientChannel.close();
            }
        });
    }

}
