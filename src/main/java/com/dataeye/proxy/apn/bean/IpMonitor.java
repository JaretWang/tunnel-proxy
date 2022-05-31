package com.dataeye.proxy.apn.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/14 20:09
 * @description
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpMonitor {

    String tunnelName;
    String proxyIp;
    LocalDateTime expireTime;
    AtomicLong useTimes = new AtomicLong(0);
    AtomicLong okTimes = new AtomicLong(0);
    AtomicLong errorTimes = new AtomicLong(0);
    AtomicLong bandwidth = new AtomicLong(0);

}
