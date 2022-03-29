package com.dataeye.proxy.bean;

import lombok.Builder;
import lombok.Data;

/**
 * @author jaret
 * @date 2022/3/29 11:24
 * @description 隧道分配结果
 */
@Builder
@Data
public class TunnelAllocateResult {

    /**
     * 代理ip
     */
    private String ip;
    /**
     * 代理端口
     */
    private int port;
    /**
     * 代理用户名
     */
    private String username;
    /**
     * 代理密码
     */
    private String password;

    /**
     * 获取代理地址
     *
     * @return
     */
    public final String getRemote() {
        return this.ip + ":" + this.port;
    }

}
