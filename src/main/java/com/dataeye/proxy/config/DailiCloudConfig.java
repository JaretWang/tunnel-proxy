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
@ConfigurationProperties(prefix = "proxy.dailicloud")
public class DailiCloudConfig {

    private String ipFectchUrl;
    private String username;
    private String password;

}
