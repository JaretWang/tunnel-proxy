package com.dataeye.proxy.server.forward;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * @author jaret
 * @date 2022/11/2 10:58
 * @description
 */
public interface RequestForwardService {

    void sendHttp(ChannelHandlerContext ctx,
                  ProxyIp proxyIp,
                  ApnHandlerParams apnHandlerParams,
                  FullHttpRequest fullHttpRequest,
                  String handler);

    void sendhttps(ChannelHandlerContext ctx,
                   ProxyIp proxyIp,
                   ApnHandlerParams apnHandlerParams,
                   FullHttpRequest fullHttpRequest,
                   String handler);

    void sendSocks5(ChannelHandlerContext ctx,
                    ProxyIp proxyIp,
                    ApnHandlerParams apnHandlerParams,
                    FullHttpRequest fullHttpRequest,
                    String handler);

}
