package com.dataeye.proxy.service;

import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyType;
import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import java.io.IOException;

/**
 * @author jaret
 * @date 2022/3/30 9:26
 * @description
 */
public interface ITunnelDistributeService {

    /**
     * 初始化多条隧道
     */
    void initMultiTunnel();

    /**
     * 检查请代理类型, 直连ip, 独享ip, 隧道ip
     *
     * @param httpRequest 源请求
     * @return
     */
    ProxyType checkType(HttpRequest httpRequest);

    /**
     * 分配代理IP,port，username，password
     *
     * @param httpRequest 源请求
     * @return
     * @throws IOException
     */
    TunnelAllocateResult getDistributeParams(HttpRequest httpRequest) throws IOException;

    /**
     * 发送代理请求
     */
    void sendProxyRequest(ChannelHandlerContext ctx,  HttpRequest httpRequest, TunnelInstance tunnelInstance) throws IOException;

}
