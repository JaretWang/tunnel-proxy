package com.dataeye.proxy.bean;

import com.dataeye.proxy.utils.TimeUtils;
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
    /**
     * rola代理 子账号序号
     */
    AtomicLong rolaAccountNum;

    public String getIpAddr() {
        return this.host + ":" + this.port;
    }

    public String getIpAddrWithTime() {
        return this.host + ":" + this.port + "(" + expireTime + ")";
    }

    public String getIpAddrWithTimeAndValid() {
        return this.host + ":" + this.port + "(" + valid.get() + ", " + expireTime + ")";
    }

    public final String getRemote() {
        String formatLocalDate = TimeUtils.formatLocalDate(expireTime);
        return this.host + ":" + this.port + "(" + formatLocalDate + ")";
    }

}
