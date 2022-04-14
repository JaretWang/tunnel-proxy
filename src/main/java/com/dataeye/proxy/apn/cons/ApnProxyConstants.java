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

package com.dataeye.proxy.apn.cons;

import io.netty.util.AttributeKey;

/**
 * @author jaret
 * @date 2022/4/14 10:41
 */
public class ApnProxyConstants {

    public static final AttributeKey<String> REQUST_URL_ATTRIBUTE_KEY = AttributeKey.valueOf("apnproxy.request_url");

    public static final String CACHE_DIR = "cache";

    public static final String CACHE_DATA_DIR = "data";

}
