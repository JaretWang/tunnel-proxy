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
    private String ip;
    private int port;
    private String proxyUsername;
    private String proxyPassword;
    private int concurrency;
    private int bossThreadSize;
    private int workerThreadSize;
    private int businessThreadSize;
    private int proxyIpPoolSize;
    private int singleIpTtl;
    private String updateIpPoolCronExpress;
    private int maxNetBandwidthSize;
    private String lastModified;
    private String createTime;
    private String description;
    // 新增
    private int connectTimeoutMillis;

}
