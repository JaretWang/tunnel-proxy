package com.dataeye.proxy.apn.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    long cost;
    String tunnelName;
    String proxyAddr;
    LocalDateTime expireTime;
    String requestType;
    String targetAddr;
    boolean success;
    String failReason;

}
