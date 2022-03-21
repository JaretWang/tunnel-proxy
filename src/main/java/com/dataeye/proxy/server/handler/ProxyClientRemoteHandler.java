package com.dataeye.proxy.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description 访问远程地址时的处理器
 */
@Slf4j
@Component
@NoArgsConstructor
@Scope("prototype")
public class ProxyClientRemoteHandler extends ChannelInboundHandlerAdapter {

    private String taskId = "未知任务id";
    private Channel clientChannel;
    private Channel remoteChannel;

    public ProxyClientRemoteHandler(String taskId, Channel clientChannel) {
        this.taskId = taskId;
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("代理客户端与远程目标地址建立连接...");
        this.remoteChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 只做转发
        log.info("代理客户端转发远程目标地址返回的数据...");
        clientChannel.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("代理客户端与远程目标地址断开连接...");
        flushAndClose(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        log.error("代理客户端：任务 [{}] 发生异常 {}", taskId, e);
        flushAndClose(remoteChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
