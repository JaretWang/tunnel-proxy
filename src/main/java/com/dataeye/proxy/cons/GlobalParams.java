package com.dataeye.proxy.cons;

import com.dataeye.proxy.server.remotechooser.ApnProxyRemote;
import io.netty.util.AttributeKey;

/**
 * @author jaret
 * @date 2022/4/7 11:05
 * @description
 */
public class GlobalParams {

    public static final AttributeKey<ApnProxyRemote> REQUST_IP_ATTRIBUTE_KEY = AttributeKey.valueOf("apnproxy.request_ip");

}
