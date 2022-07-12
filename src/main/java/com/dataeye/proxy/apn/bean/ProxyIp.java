package com.dataeye.proxy.apn.bean;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/26 16:17
 * @description
 */
@Data
@Builder
public class ProxyIp {

    String host;
    Integer port;
    String userName;
    String password;
    String createTime;
    String updateTime;
    LocalDateTime expireTime;
    AtomicBoolean valid;
    AtomicLong useTimes;
    AtomicLong okTimes;
    AtomicLong errorTimes;
//    boolean applyRemoteRule = false;
//    ApnProxyListenType remoteListenType;
//    AtomicDouble okPercent;

    public String getIpAddr() {
        return this.host + ":" + this.port;
    }

    public String getIpAddrWithTime() {
        return this.host + ":" + this.port + "(" + expireTime + ")";
    }

    public String getIpAddrWithTimeAndValid() {
        return this.host + ":" + this.port + "(" + valid.get() + ", " + expireTime + ")";
    }

}
