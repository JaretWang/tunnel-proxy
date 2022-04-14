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

package com.dataeye.proxy.apn.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class HttpErrorUtil {

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
