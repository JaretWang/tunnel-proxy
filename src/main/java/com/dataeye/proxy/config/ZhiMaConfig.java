package com.dataeye.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author hongjunhao
 * @date 2022/2/15 9:53
 */
@Data
@Component
@ConfigurationProperties(prefix = "service.zhima")
public class ZhiMaConfig {

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
