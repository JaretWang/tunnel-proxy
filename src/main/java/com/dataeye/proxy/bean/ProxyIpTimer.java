package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Timer;

/**
 * @author jaret
 * @date 2022/11/14 14:41
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProxyIpTimer {

    ProxyIp proxyIp;
    Timer timer;
}
