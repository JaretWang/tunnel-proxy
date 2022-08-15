package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/8/10 15:12
 * @description
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProxyCfg {

    String host;
    Integer port;
    String userName;
    String password;
    LocalDateTime expireTime;
    AtomicBoolean valid;
    AtomicLong useTimes;
    AtomicLong okTimes;
    AtomicLong errorTimes;

}