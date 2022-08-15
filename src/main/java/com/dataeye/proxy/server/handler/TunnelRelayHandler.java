package com.dataeye.proxy.server.handler;


import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.utils.ReqMonitorUtils;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import lombok.SneakyThrows;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class TunnelRelayHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.relay";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final Channel relayChannel;
    private final String tag;
    private final RequestMonitor requestMonitor;
    private boolean first = true;

    public TunnelRelayHandler(RequestMonitor requestMonitor, String tag, Channel relayChannel) {
        this.requestMonitor = requestMonitor;
        this.tag = tag;
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("TunnelRelayHandler channelActive: {} channel active", tag);
        if (!ctx.channel().config().getOption(ChannelOption.AUTO_READ)) {
            ctx.read();
        }
    }

    @SneakyThrows
    public String convertByteBufToString(ByteBuf buf) {
        String str;
        // 处理堆缓冲区
        if (buf.hasArray()) {
            str = new String(buf.array(), buf.arrayOffset() + buf.readerIndex(), buf.readableBytes());
        } else {
            // 处理直接缓冲区以及复合缓冲区
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            str = new String(bytes, 0, buf.readableBytes(), StandardCharsets.UTF_8);
        }
        return str;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        logger.debug("TunnelRelayHandler channelRead: {} : {}", tag, msg);
        // 转为bytebuf，然后再求响应大小，最后再设置在 ReqMonitorUtils 里面
        ByteBuf byteBuf = (ByteBuf) msg;
        requestMonitor.getReponseSize().addAndGet(byteBuf.readableBytes());
        //System.out.println("TunnelRelayHandler msg refCnt=" + byteBuf.refCnt());

        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg)
                    .addListener((ChannelFutureListener) future -> {
                        if (!ctx.channel().config().getOption(ChannelOption.AUTO_READ)) {
                            ctx.read();
                        }
                        //System.out.println("TunnelRelayHandler addListener byteBuf refCnt=" + byteBuf.refCnt());

                        // 临时添加
                        if (first) {
                            if (future.isSuccess()) {
                                ReqMonitorUtils.ok(requestMonitor, "TunnelRelayHandler isSuccess");
                                IpMonitorUtils.ok(requestMonitor, "TunnelRelayHandler isSuccess");
                            } else {
                                // 临时增加
                                ReqMonitorUtils.error(requestMonitor, "TunnelRelayHandler isError", future.cause().getMessage());
                                IpMonitorUtils.error(requestMonitor, "TunnelRelayHandler isError", future.cause().getMessage());
                            }
                            first = false;
                        }
                    });
        } else {
            ReferenceCountUtil.release(msg);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 该方法会执行两次，因为要关闭两次， UA --> REMOTE | REMOTE --> UA
        logger.debug("TunnelRelayHandler channelInactive: {} channel inactive", tag);
        if (relayChannel != null && relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        ctx.fireChannelInactive();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("TunnelRelayHandler exceptionCaught: {}", cause.getMessage());
        ReqMonitorUtils.error(requestMonitor, "TunnelRelayHandler exceptionCaught", cause.getMessage());
        IpMonitorUtils.error(requestMonitor, "TunnelRelayHandler exceptionCaught", cause.getMessage());
        ctx.close();
    }

}