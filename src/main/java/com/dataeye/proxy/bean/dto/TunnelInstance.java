package com.dataeye.proxy.bean.dto;

import lombok.*;

import java.io.Serializable;

/**
 * @author jaret
 * @date 2022/3/30 11:19
 * @description 单个隧道实例的初始化参数
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TunnelInstance implements Serializable {

    private int id;
    private String alias;
    private String location;
    private String ip;
    private int port;
    private String username;
    private String password;
    private int bossThreadSize;
    private int workerThreadSize;
    private int businessThreadSize;
    private int concurrency;
    private int maxNetBandwidth;
    private int maxSlowReqSize;
    private int fixedIpPoolSize;
    private int checkIpPoolIntervalSeconds;
    private int minSuccessPercentForRemoveIp;
    private int maxFetchIpNumEveryDay;
    private int connectTimeoutMillis;
    private int retryCount;
    private String lastModified;
    private String createTime;
    private String description;

}
