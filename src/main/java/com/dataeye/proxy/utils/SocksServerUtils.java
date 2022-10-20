package com.dataeye.proxy.utils;

import com.dataeye.proxy.cons.GlobalParams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.math.BigDecimal;
import java.util.Map;
import java.util.StringJoiner;

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

    /**
     * 获取请求方ip
     *
     * @return
     */
    public static String getReqSrcIp(ChannelHandlerContext ctx) {
        return ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0];
    }

    /**
     * 获取请求报文大小
     *
     * @param httpRequest
     * @return
     */
    public static int getReqSize(FullHttpRequest httpRequest) {
        int contentLength = 0;
        HttpHeaders headers = httpRequest.headers();
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> header : headers) {
            String key = header.getKey();
            String value = header.getValue();
            if ("content-length".equalsIgnoreCase(key)) {
                contentLength = Integer.parseInt(value);
            }
            builder.append(key).append(": ").append(value).append(System.lineSeparator());
        }
        if (contentLength <= 0) {
            contentLength = httpRequest.content().readableBytes();
            GlobalParams.LOGGER.warn("content-length not exist, use readableBytes, contentLength={}", formatByte(contentLength));
        }
        int headerSize = builder.toString().getBytes().length;
        int reqSize = headerSize + contentLength;
        GlobalParams.LOGGER.info("reqSize={}, headerSize={}, bodySize={}", formatByte(reqSize), formatByte(headerSize), formatByte(contentLength));
        return reqSize;
    }

    /**
     * 获取响应报文大小
     *
     * @param httpResponse
     * @return
     */
    public static int getRespSize(FullHttpResponse httpResponse) {
        int contentLength = 0;
        HttpHeaders headers = httpResponse.headers();
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (Map.Entry<String, String> header : headers) {
            String key = header.getKey();
            String value = header.getValue();
            if ("content-length".equalsIgnoreCase(key)) {
                contentLength = Integer.parseInt(value);
            }
            String entry = key + ": " + value;
            joiner.add(entry);
        }
        if (contentLength <= 0) {
            contentLength = httpResponse.content().readableBytes();
            GlobalParams.LOGGER.warn("content-length not exist, use readableBytes, contentLength={}", formatByte(contentLength));
        }
        int headerSize = joiner.toString().getBytes().length;
        int respSize = headerSize + contentLength;
        GlobalParams.LOGGER.info("respSize={}, headerSize={}, bodySize={}", formatByte(respSize), formatByte(headerSize), formatByte(contentLength));
        return respSize;
    }

    /**
     * 格式化 bytes 大小
     *
     * @param byteLength
     * @return
     */
    public static String formatByte(int byteLength) {
        if (byteLength <= 0) {
            return "0 Byte";
        }
        if (byteLength < 1024) {
            return byteLength + " Byte";
        } else if (byteLength < 1024 * 1024) {
            double reqSize = new BigDecimal(byteLength).divide(new BigDecimal(1024), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
            return reqSize + " KB";
        } else {
            double reqSize = new BigDecimal(byteLength).divide(new BigDecimal(1024 * 1024), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
            return reqSize + " MB";
        }
    }

}
