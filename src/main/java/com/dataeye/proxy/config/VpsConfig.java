package com.dataeye.proxy.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author hongjunhao
 * @date 2022/2/15 9:53
 */
@Data
@Component
@ConfigurationProperties(prefix = "proxy.vps")
public class VpsConfig {
    /**
     * 代理ip默认端口
     */
    int defaultPort;
    /**
     * 代理ip账号
     */
    String username;
    /**
     * 代理ip密码
     */
    String password;
    /**
     * 代理ip有效时间, 单位：秒
     */
    int ipValidSeconds;
    /**
     * 检查vps存活的最大重试次数
     */
    int maxRetryForCheckVpsAlive;
    /**
     * 代理ip在待重播队列中等待的最大存活时间
     */
    int maxAliveTimeInWaitingReplayQueue;
    String startCommand;
    String stopCommand;
    String restartCommand;

    @AllArgsConstructor
    public enum Operate {
        /**
         * 启动vps
         */
        start("启动", "pppoe-start"),
        /**
         * 关闭vps
         */
        stop("关闭", "pppoe-stop"),
        /**
         * 重启vps
         */
        restart("重播", "pppoe-stop && pppoe-start"),
        /**
         * 获取ip
         */
        ifconfig("获取ip(ppp0网卡)", "ifconfig | grep -A 1 'ppp0' | grep 'inet' | awk -F ' ' '{print$2}'");
        @Getter
        private final String type;
        @Getter
        private final String command;
    }

}
