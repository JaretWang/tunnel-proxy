package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/8/11 17:13
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RolaStaticIp {

    private String address;
    private String port;
    private String opUserName;
    private String opPassword;
    private String protocol;
    private String country;
    private String createTime;
    private String expireTime;
    private int id;
    /**
     * ip类型 Ipv4 Ipv6
     */
    private String ipType;
    /**
     * 订单状态，0:配置中，1：已完成
     */
    private int orderStatus;
    /**
     * 订单号
     */
    private String orderNo;

}
