package com.dataeye.proxy.apn.bean;

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

    long begin = System.currentTimeMillis();
    /**
     * 请求耗时
     */
    long cost;
    /**
     * 带宽
     */
    int bandwidth;
    /**
     * 隧道名称
     */
    String tunnelName;
    /**
     * 请求报文的大小, 单位：字节
     */
    AtomicInteger requestSize = new AtomicInteger(0);
    /**
     * 响应报文的大小, 单位：字节
     */
    AtomicInteger reponseSize = new AtomicInteger(0);
    /**
     * 代理ip+port
     */
    String proxyAddr;
    /**
     * 代理IP的有效时间
     */
    LocalDateTime expireTime;
    /**
     * 请求类型
     */
    String requestType;
    /**
     * 请求url
     */
    String targetAddr;
    /**
     * 请求是否成功
     */
    boolean success;
    /**
     * 请求失败的原因
     */
    String failReason;

}
