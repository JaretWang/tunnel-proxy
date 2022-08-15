package com.dataeye.proxy.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class HttpErrorUtils {

    public static HttpMessage buildHttpErrorMessage(HttpResponseStatus status, String errorMsg) {
        ByteBuf errorResponseContent = Unpooled.copiedBuffer(errorMsg, CharsetUtil.UTF_8);
        // send error response
        FullHttpMessage errorResponseMsg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                status, errorResponseContent);
        errorResponseMsg.headers()
                .add(HttpHeaders.Names.CONTENT_ENCODING, CharsetUtil.UTF_8.name());
        errorResponseMsg.headers().add(HttpHeaders.Names.CONTENT_LENGTH,
                errorResponseContent.readableBytes());

        return errorResponseMsg;
    }
}
