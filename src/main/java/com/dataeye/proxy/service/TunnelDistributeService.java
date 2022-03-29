package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.IpTimer;
import com.dataeye.proxy.bean.ProxyType;
import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.TimeCountDown;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.TunnelManageConfig;
import com.dataeye.proxy.exception.ProxyTypeUnSupportException;
import com.dataeye.proxy.exception.TunnelProxyConfigException;
import com.sun.org.apache.regexp.internal.RE;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jaret
 * @date 2022/3/29 11:31
 * @description 隧道分发服务
 */
@Slf4j
@Service
public class TunnelDistributeService {

    @Autowired
    private IpSelector ipSelector;
    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Autowired
    private TunnelManageConfig tunnelManageConfig;

    /**
     * 检查请代理类型, 直连ip, 独享ip, 隧道ip
     *
     * @param httpRequest 源请求
     * @return
     */
    public ProxyType checkType(HttpRequest httpRequest) {
        if (httpRequest.method() == HttpMethod.CONNECT) {
            return ProxyType.tuunel;
        }
        String type = httpRequest.headers().get("my-proxy-type", "");
        if (StringUtils.isBlank(type)) {
            throw new ProxyTypeUnSupportException("请求中不存在 my-proxy-type 选项");
        }
        log.debug("my-proxy-type -> {} ", type);
        return ProxyType.valueOf(type.trim());
    }

    /**
     * 分配代理IP
     *
     * @param httpRequest 源请求
     * @return
     * @throws IOException
     */
    public TunnelAllocateResult distribute(HttpRequest httpRequest) throws IOException {
        ProxyType proxyType = checkType(httpRequest);
        // 分配ip
        ConcurrentHashMap<ProxyType, List<IpTimer>> distributeList = ipSelector.getDistributeList();
        boolean contains = distributeList.containsKey(proxyType);
        if (!contains) {
            throw new ProxyTypeUnSupportException("代理类型" + proxyType + "不支持");
        }
        List<IpTimer> ipTimers = distributeList.get(proxyType);
        // 临界阈值
        double loadFactor = tunnelManageConfig.getLoadFactor();
        int thresholdValue = (int) (loadFactor * ipTimers.size());
        for (IpTimer ipTimer : ipTimers) {
            // 取一个
            TimeCountDown timeCountDown = ipTimer.getTimeCountDown();
            // 有效期
            if (timeCountDown.getDuration() > 0) {
                String ip = ipTimer.getIp();
                int port = ipTimer.getPort();
                TunnelAllocateResult.TunnelAllocateResultBuilder allocateResultBuilder = TunnelAllocateResult.builder().ip(ip).port(port);
                if (proxyType == ProxyType.tuunel) {
                    String proxyUserName = proxyServerConfig.getProxyUserName();
                    String proxyPassword = proxyServerConfig.getProxyPassword();
                    allocateResultBuilder.username(proxyUserName).password(proxyPassword);
                }
                TunnelAllocateResult tunnelAllocateResult = allocateResultBuilder.build();
                // 检测剩余代理ip池数量
                // 当代理ip的数量, 低于负载因子的个数,再放一批进去
                if (ipTimers.size() <= thresholdValue) {
                    log.warn("{} 代理ip池的存活数量低于阈值 {}, 即将进行补充", proxyType, thresholdValue);
                    List<IpTimer> proIpList = ipSelector.getDistributeList().get(proxyType);
                    String ipAccessLink = ipSelector.getAccessLink().get(proxyType);
                    ipSelector.initList(proIpList, ipAccessLink);
                }
                return tunnelAllocateResult;
            } else {
                // 失效了,移除
                ipTimers.remove(ipTimer);
            }
        }
        List<IpTimer> proxyIpList = ipSelector.getDistributeList().get(proxyType);
        String ipAccessLink = ipSelector.getAccessLink().get(proxyType);
        ipSelector.initList(proxyIpList, ipAccessLink);
        log.error("{} 代理ip池全部失效, 重新初始化, 代理ip个数: {}", proxyType, proxyIpList.size());
        return distribute(httpRequest);
    }

}
