package com.dataeye.proxy.overseas.impl;

import com.dataeye.proxy.bean.enums.RolaProxyInfo;
import com.dataeye.proxy.bean.enums.RolaProxyType;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.overseas.RolaProxyFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/8/10 11:09
 * @description 动态机房ip
 */
@Service
public class DynamicMachineRoomIpServiceImpl extends RolaProxyFetchService {

    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    IpSelector ipSelector;

    @Override
    public void initIpPool() {
        RolaProxyInfo[] proxyInfos = Arrays.stream(RolaProxyInfo.values())
                .filter(value -> !"gate9.rola.info".equals(value.getHost()))
                .collect(Collectors.toList())
                .toArray(new RolaProxyInfo[4]);
        buildIpPool(RolaProxyType.DYNAMIC_MACHINE_ROOM, proxyInfos);
    }

}
