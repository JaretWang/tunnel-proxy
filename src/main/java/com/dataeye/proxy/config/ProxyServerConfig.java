package com.dataeye.proxy.config;

import com.dataeye.proxy.bean.enums.TunnelType;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/18 13:23
 * @description
 */
@Data
@Component
@ConfigurationProperties(prefix = "proxy.server")
public class ProxyServerConfig {

    /**
     * 是否开启隧道
     */
    private boolean enable;
    /**
     * 隧道类型
     */
    private TunnelType tunnelType;
    /**
     * 循环检查ip池的时间间隔
     */
    private int cycleCheckTime;
    /**
     * 获取的ip是失效ip的重试次数
     */
    private int expiredIpRetryCount;
    /**
     * 提前判定ip为失效状态的最小时间间隔
     */
    private int judgeExpiredIpMinSeconds;
    /**
     * 邮件发送接口
     */
    private String mailSendAddr;
    /**
     * 接收方邮件地址(负责人)
     */
    private String principal;

}
