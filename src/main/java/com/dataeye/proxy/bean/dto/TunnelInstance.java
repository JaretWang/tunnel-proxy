package com.dataeye.proxy.bean.dto;

import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import lombok.*;
import org.slf4j.Logger;

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
@ToString(exclude = {"usedIp"})
@EqualsAndHashCode(exclude = {"usedIp"})
public class TunnelInstance implements Serializable, Cloneable {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("TunnelInstance");

    private int id;
    /**
     * 隧道别名
     */
    private String alias;
    /**
     * 隧道部署机器
     */
    private String location;
    /**
     * 是否开启隧道 0关闭 1开启
     */
    private int enable;
    /**
     * 是否发送告警邮件 0不发送 1发送
     */
    private int sendAlarmEmail;
    /**
     * 是否使用自动计算核心ip数  0不使用 1使用
     */
    private int autoGetCoreIpSize;
    /**
     * 隧道域名地址
     */
    private String domain;
    /**
     * 隧道启动端口
     */
    private int port;
    /**
     * 访问隧道的用户名
     */
    private String username;
    /**
     * 访问隧道的密码
     */
    private String password;
    /**
     * proxy-server的boss线程数
     */
    private int bossThreadSize;
    /**
     * proxy-server的worker线程数
     */
    private int workerThreadSize;
    /**
     * 每秒并发请求数, 单位: 个
     */
    private int concurrency;
    /**
     * 最大网络带宽的限制，单位：MB
     */
    private int maxNetBandwidth;
    /**
     * 最大慢请求数量，不超过业务线程数的10%
     */
    private int maxSlowReqSize;
    /**
     * 核心ip数
     */
    private int coreIpSize;
    /**
     * 最大ip数
     */
    private int maxIpSize;
    /**
     * 检查ip池的时间间隔，单位：秒
     */
    private int checkIpPoolIntervalSeconds;
    /**
     * 移除低质量ip的最低成功率阈值, 单位: 百分比
     */
    private int minSuccessPercentForRemoveIp;
    /**
     * 移除低质量ip的最低使用次数
     */
    private int minUseTimesForRemoveIp;
    /**
     * 每日累计拉取ip最大数量, 单位: 个
     */
    private int maxFetchIpNumEveryDay;
    /**
     * 每日已经消耗的ip数(防止程序重启后重置)
     */
    private int usedIp;
    /**
     * 代理ip连接的超时时间，单位：毫秒
     */
    private int connectTimeoutMillis;
    /**
     * 一个ip连接超时，最大重试次数
     */
    private int retryCount;
    /**
     * 请求读超时
     */
    private int readTimeoutSeconds;
    /**
     * 请求写超时
     */
    private int writeTimeoutSeconds;
    /**
     * 最后一次修改时间
     */
    private String lastModified;
    /**
     * 创建时间
     */
    private String createTime;
    /**
     * 描述备注信息
     */
    private String description;

    /**
     * 获取每日剩余可用ip数
     *
     * @return
     */
    public int getAvailableIp() {
        return maxFetchIpNumEveryDay - usedIp;
    }

}
