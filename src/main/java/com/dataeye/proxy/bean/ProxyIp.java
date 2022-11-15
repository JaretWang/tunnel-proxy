package com.dataeye.proxy.bean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.cons.Log;
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
    /**
     * 正在处理中的连接数（参考netty对象引用计数）
     */
    AtomicLong connecting;
    /**
     * vps实例
     */
    VpsInstance vpsInstance;

    public String getIpAddr() {
        return this.host + ":" + this.port;
    }

    public String getIpAddrWithTime() {
        return this.host + ":" + this.port + "(" + TimeUtils.formatLocalDate(expireTime) + ")";
    }

    public String getIpAddrWithTimeAndValid() {
        return this.host + ":" + this.port + "(" + valid.get() + ", " + TimeUtils.formatLocalDate(expireTime) + ")";
    }

    public final String getRemote() {
        return this.host + ":" + this.port + "(" + TimeUtils.formatLocalDate(expireTime) + ")";
    }

    public String getIpWithVps(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("proxy", this.getIpAddrWithTimeAndValid());
        jsonObject.put("vps", this.getVpsInstance().getInstanceInfo());
        return jsonObject.toJSONString();
    }

    /**
     * 标记连接数
     */
    public void addConnectCount(){
        AtomicLong connecting = this.getConnecting();
        if (connecting == null) {
            this.setConnecting(new AtomicLong(1));
        } else {
            this.getConnecting().incrementAndGet();
        }
    }

    /**
     * 移除连接数
     */
    public void removeConnectCount(){
        AtomicLong connecting = this.getConnecting();
        if (connecting == null) {
            Log.SERVER.info("移除连接数失败, connecting is null, proxyIp={}", JSON.toJSONString(this));
        } else {
            long currentCount = this.getConnecting().decrementAndGet();
            Log.SERVER.info("移除连接数成功, connect={}", currentCount);
        }
    }

    public static void removeConnect(ProxyIp proxyIp){
        if (proxyIp != null) {
            removeConnect(proxyIp);
        }
    }
}
