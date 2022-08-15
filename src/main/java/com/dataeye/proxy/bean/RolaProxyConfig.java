package com.dataeye.proxy.bean;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.enums.RolaProxyInfo;
import com.dataeye.proxy.bean.enums.RolaProxyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author jaret
 * @date 2022/8/11 11:31
 * @description
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RolaProxyConfig {

    /**
     * 国家代号, 默认：us
     */
    @Builder.Default
    String countryCode = RolaProxyInfo.US_HTTP.getServerLocationCode();
    /**
     * 协议 1 socks5 2 http/https, 默认：2
     */
    @Builder.Default
    int protocol = 2;
    /**
     * 服务器接入点：中国(cn) 新加披(sg) 美国(us), 默认：us
     */
    @Builder.Default
    String serverLocation = RolaProxyInfo.US_HTTP.getServerLocationCode();
    /**
     * ip时长: 10min 30min 60min 90min, 默认：10
     */
    @Builder.Default
    int ipTime = 10;
    /**
     * 1 动态住宅ip  2 动态机房ip 3 静态机房ip, 4手机ip, 默认：2
     */
    @Builder.Default
    int ipType = RolaProxyType.DYNAMIC_MACHINE_ROOM.getIpType();

    public static void main(String[] args) {
        RolaProxyConfig rolaProxyConfig = new RolaProxyConfig();
//        rolaProxyConfig.setCountryCode(RolaProxyInfo.US_HTTP.getServerLocationCode());
//        rolaProxyConfig.setCountryCode(RolaProxyInfo.SGP_HTTP.getServerLocationCode());
//        rolaProxyConfig.setCountryCode(RolaProxyInfo.CHINA_HTTP.getServerLocationCode());

//        rolaProxyConfig.setProtocol(1);
//        rolaProxyConfig.setProtocol(2);

//        rolaProxyConfig.setServerLocation(RolaProxyInfo.US_HTTP.getServerLocationCode());
//        rolaProxyConfig.setServerLocation(RolaProxyInfo.SGP_HTTP.getServerLocationCode());
//        rolaProxyConfig.setServerLocation(RolaProxyInfo.CHINA_HTTP.getServerLocationCode());

//        rolaProxyConfig.setIpTime(10);
//        rolaProxyConfig.setIpTime(30);
//        rolaProxyConfig.setIpTime(60);
//        rolaProxyConfig.setIpTime(90);

//        rolaProxyConfig.setIpType(RolaProxyType.DYNAMIC_HOME.getIpType());
//        rolaProxyConfig.setIpType(RolaProxyType.DYNAMIC_MACHINE_ROOM.getIpType());
//        rolaProxyConfig.setIpType(RolaProxyType.STATIC_MACHINE_ROOM.getIpType());

        String s = JSON.toJSONString(rolaProxyConfig);
        System.out.println(s);
        String code = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
        System.out.println(code);
    }

}
