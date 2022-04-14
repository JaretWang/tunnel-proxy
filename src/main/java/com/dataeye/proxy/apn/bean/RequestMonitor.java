package com.dataeye.proxy.apn.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    String requestType;
    String targetAddr;
    boolean success;
    String failReason;

}
