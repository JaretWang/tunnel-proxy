package com.dataeye.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/8/11 16:09
 * @description
 */
@Data
@Component
@ConfigurationProperties(prefix = "rola.proxy")
public class RolaConfig {

    String token;
    /**
     * 所有接口的根url
     */
    String rootUrl;
    /**
     * 获取下单的key(订单号)
     */
    String getOrderKeyUrl;
    /**
     * 添加ip白名单 url
     */
    String addIpWhiteListUrl;
    /**
     * 获取已购买的静态机房IP列表 url
     */
    String getStaticIpListUrl;

}
