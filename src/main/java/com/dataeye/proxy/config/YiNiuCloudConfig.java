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
@ConfigurationProperties(prefix = "proxy.yiniucloud")
public class YiNiuCloudConfig {

    /**
     * 默认拉取一个ip的url
     */
    private String ipFectchUrl;
    /**
     * 自定义拉取的ip数量的url
     */
    private String ipFectchWithCustomQuantity;

}
