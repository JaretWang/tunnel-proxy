package com.dataeye.proxy.cons;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/4/7 11:05
 * @description
 */
public class GlobalParams {

    public static final AttributeKey<ProxyIp> REQUST_IP_ATTRIBUTE_KEY = AttributeKey.valueOf("apnproxy.request_ip");
    public static final Logger LOGGER = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    public static ProxyIp getProxyIp(final ChannelHandlerContext ctx){
        return ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).get();
    }

    public static void setProxyIp(final ChannelHandlerContext ctx, ProxyIp proxyIp){
        ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).set(proxyIp);
    }

}
