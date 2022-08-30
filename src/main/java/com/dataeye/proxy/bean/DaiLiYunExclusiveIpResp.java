package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/8/30 11:17
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DaiLiYunExclusiveIpResp {

    /**
     * 代理ip
     */
    String ip;
    /**
     * 端口
     */
    int port;
    /**
     * 出库ip
     */
    String rip;
    /**
     * 地理信息
     */
    String artx;
    /**
     * ip 入库时间
     */
    long ftime;
    /**
     * ip 过期时间
     */
    long ltime;

}
