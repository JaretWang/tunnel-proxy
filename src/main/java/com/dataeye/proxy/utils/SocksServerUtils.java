package com.dataeye.proxy.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * @author jaret
 * @date 2022/5/9 16:36
 */
public final class SocksServerUtils {

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static void okHttpResp(Channel ch, String msg) {
        if (ch != null && ch.isActive()) {
            ByteBuf responseContent = Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8);
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseContent);
            ch.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static void errorHttpResp(Channel ch, String msg) {
        if (ch != null && ch.isActive()) {
            ByteBuf responseContent = Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8);
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, responseContent);
            ch.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
