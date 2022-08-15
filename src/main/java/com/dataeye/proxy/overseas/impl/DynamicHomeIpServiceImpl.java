package com.dataeye.proxy.overseas.impl;

import com.dataeye.proxy.bean.enums.RolaProxyInfo;
import com.dataeye.proxy.bean.enums.RolaProxyType;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.overseas.RolaProxyFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author jaret
 * @date 2022/8/10 11:09
 * @description 动态住宅ip
 */
@Service
public class DynamicHomeIpServiceImpl extends RolaProxyFetchService {

    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    IpSelector ipSelector;

    @Override
    public void initIpPool() {
        buildIpPool(RolaProxyType.DYNAMIC_HOME, RolaProxyInfo.values(), ipSelector);
    }

    /**
     * 国家/地区
     * 州:  城市: 随机市
     * 协议： socks5  http/https
     * 接入点：中国服务器，新加坡服务器，美国服务器
     * IP时长：10分钟（默认），30分钟，60分钟，90分钟
     * 子账号列表：dataeye_(1-500)
     */
    void changeIpConfig() {

    }

}
