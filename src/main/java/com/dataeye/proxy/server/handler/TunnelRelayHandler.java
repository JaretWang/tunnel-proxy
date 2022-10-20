package com.dataeye.proxy.server.handler;


import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import lombok.SneakyThrows;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

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
//    AtomicLong totalSend = new AtomicLong();
//    AtomicLong reqSize = new AtomicLong();
//    AtomicLong respSize = new AtomicLong();
//    private boolean status = true;
    private boolean first = true;

    public TunnelRelayHandler(RequestMonitor requestMonitor, String tag, Channel relayChannel) {
        this.requestMonitor = requestMonitor;
        this.tag = tag;
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("TunnelRelayHandler channelActive: {}", tag);
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

    long calculateSize(Object msg) {
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof ByteBufHolder) {
            return ((ByteBufHolder) msg).content().readableBytes();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).count();
        }
        return -1;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        logger.debug("TunnelRelayHandler channelRead, tag={}, msg={}", tag, msg);
//        int bytebufsize = 0, size = 0;
//        // 转为bytebuf，然后再求响应大小，最后再设置在 ReqMonitorUtils 里面
//        long len = calculateSize(msg);
//        len = bytebufsize + len;
        // 将netty的bytebuf转为 java的string
//        size = convertByteBufToString(byteBuf).getBytes().length + size;
//        if (tag.startsWith("UA -->")) {
////            System.out.println("UA---->REMOTE(bytebuf): " + len);
////            System.out.println("UA---->REMOTE: " + size);
//            requestMonitor.getRequestSize().addAndGet(len);
//        } else {
////            System.out.println("REMOTE---->UA(bytebuf): " + len);
////            System.out.println("REMOTE---->UA: " + size);
//            requestMonitor.getReponseSize().addAndGet(len);
//        }

        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg)
                    .addListener((ChannelFutureListener) future -> {
                        if (!ctx.channel().config().getOption(ChannelOption.AUTO_READ)) {
                            ctx.read();
                        }

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
                        // 测试计算数据包大小临时使用
//                        if (tag.startsWith("UA -->")) {
//                            System.out.println("UA---->REMOTE: " + reqSize.get());
//                            reqSize.getAndAdd(len);
//                        } else {
//                            System.out.println("REMOTE---->UA: " + respSize.get());
//                            respSize.getAndAdd(len);
//                        }
//                        System.out.println("totalSend---->: " + totalSend.get());
//                        totalSend.getAndAdd(len);
//                        status = future.isSuccess();
                    });
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 该方法会执行两次，因为要关闭两次， UA --> REMOTE | REMOTE --> UA
        logger.debug("TunnelRelayHandler channelInactive: {}", tag);
        if (relayChannel != null && relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        ctx.fireChannelInactive();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("转发通道 exceptionCaught: {}, tag: {}", cause.getMessage(), tag);
        ReqMonitorUtils.error(requestMonitor, "TunnelRelayHandler exceptionCaught", cause.getMessage());
        IpMonitorUtils.error(requestMonitor, "TunnelRelayHandler exceptionCaught", cause.getMessage());
        ctx.close();
    }

}