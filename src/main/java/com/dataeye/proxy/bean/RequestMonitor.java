package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/4/14 20:09
 * @description
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestMonitor {

    /**
     * 隧道名称
     */
    String tunnelName;
    /**
     * 代理ip+port
     */
    String proxyAddr;
    /**
     * 代理IP的有效时间
     */
    LocalDateTime expireTime;
    /**
     * 请求方ip
     */
    String srcIp;
    /**
     * 请求类型
     */
    String method;
    /**
     * 请求地址
     */
    String uri;
    /**
     * 请求报文的大小 = 请求头字节数 + Content-Length(没有该值则统计请求的BODY实际字节数), 单位：字节
     */
    AtomicInteger requestSize = new AtomicInteger(0);
    /**
     * 请求是否成功
     */
    boolean success;
    /**
     * 响应码
     */
    int code;
    /**
     * 请求失败的原因
     */
    String failReason;
    /**
     * 响应报文的大小 = 响应头字节数 + Content-Length(没有该值则统计响应的BODY实际字节数), 单位：字节
     */
    AtomicInteger reponseSize = new AtomicInteger(0);
    /**
     * 接收请求时间
     */
    long begin = System.currentTimeMillis();
    /**
     * 请求耗时 ms
     */
    long cost;

}
