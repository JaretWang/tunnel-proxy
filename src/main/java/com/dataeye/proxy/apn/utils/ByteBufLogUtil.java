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

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ByteBufLogUtil {

    public static String toLogString(ByteBuf byteBuf) {
        StringBuilder sb = new StringBuilder();
        byteBuf.readerIndex(0);
        while (byteBuf.isReadable()) {
            sb.append(String.valueOf((int) byteBuf.readByte())).append(",");
        }

        byteBuf.readerIndex(0);
        return sb.toString();
    }
}
