package com.dataeye.proxy.bean;

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
@ConfigurationProperties(prefix = "tunnel.proxy")
public class TunnelConfig {
    /**
     * 代理商ip
     */
    private String ip;
    /**
     * 代理商端口
     */
    private int port;
    /**
     * 隧道代理：用户名
     */
    private String username;
    /**
     * 隧道代理：密码
     */
    private String password;
    /**
     * 应用使用代理商的代理
     */
    private boolean appleyRemoteRule = false;

    /**
     * 获取代理商的地址
     *
     * @return
     */
    public final String getRemote() {
        return this.ip + ":" + this.port;
    }

}
