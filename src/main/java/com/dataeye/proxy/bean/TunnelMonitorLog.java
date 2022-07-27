package com.dataeye.proxy.bean;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.logging.Level;

/**
 * @author jaret
 * @date 2022/7/27 17:05
 * @description
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TunnelMonitorLog {

    String location;
    String name;
    int concurrency;
    String okPercent;
    String cost;
    String reqSize;
    String respSize;
    String reqBandwidth;
    String respBandwidth;
    int tcpConn;
    int surplusIp;
    int ipLimit;
    int usedIp;
    int ipPoolSize;
    String updateTime;

}
