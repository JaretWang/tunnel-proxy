package com.dataeye.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/8/3 16:28
 * @description
 */
@Data
@Component
@ConfigurationProperties(prefix = "proxy.zhima")
public class ZhiMaConfig {

    /**
     * 每日百万ip(待测试版本)
     */
    String millionIpUrl;
    /**
     * 直连ip
     */
    String directGetUrl;
    /**
     * 独享ip
     */
    String exclusiveGetUrl;
    /**
     * 隧道ip
     */
    String tunnelGetUrl;
    /**
     * 获取套餐剩余ip的数量
     */
    String getRemainIpNumUrl;
    /**
     * 添加白名单接口
     */
    String addIpWhiteListUrl;
    /**
     * 删除白名单接口
     */
    String deleteIpWhiteListUrl;

}
