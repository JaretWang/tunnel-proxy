package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.CustomIpAllocate;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.utils.NetUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/24 18:06
 * @description
 */
@Slf4j
@Service
public class TunnelInitService {

    /**
     * 本机器使用的隧道
     */
    public static final ConcurrentHashMap<String, TunnelInstance> TUNNEL_INSTANCES_CACHE = new ConcurrentHashMap<>();
    private static String DEFAULT_TUNNEL_NAME;
    @Resource
    TunnelInitMapper tunnelInitMapper;
    @Value("${spring.profiles.active}")
    String profile;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    TunnelInitService tunnelInitService;

    public boolean isStart(TunnelType tunnelType) {
        String innerIp = tunnelInitService.getEth0Inet4InnerIp();
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        return proxyServerConfig.isEnable()
                && defaultTunnel.getType() == tunnelType.getId()
                && defaultTunnel.getLocation().equalsIgnoreCase(innerIp.trim())
                && defaultTunnel.getEnable() == 1;
    }

    /**
     * 定时更新隧道列表缓存
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void schduleUpdateTunnelListCache() {
        updateTunnelByEquals();
    }

    /**
     * 获取定制ip网卡序号分配情况
     *
     * @param ip
     * @param port
     * @return
     */
    public CustomIpAllocate getCustomIpAllocate(String ip, int port) {
        return tunnelInitMapper.queryCustomIpAllocate(ip, port);
    }

    /**
     * 获取eth0网卡内网ip
     *
     * @return
     */
    public String getEth0Inet4InnerIp() {
        String ip;
        if ("dev".equals(profile)) {
            ip = "localhost";
        } else {
            ip = NetUtils.getEth0Inet4InnerIp();
        }
        return ip;
    }

    /**
     * 获取所有隧道实例配置列表
     *
     * @return 隧道实例配置列表
     */
    public List<TunnelInstance> getTunnelList() {
        // 检查缓存
        if (!TUNNEL_INSTANCES_CACHE.isEmpty()) {
            return TUNNEL_INSTANCES_CACHE.values().stream().distinct().collect(Collectors.toList());
        }
        String eth0Inet4InnerIp = getEth0Inet4InnerIp();
        if (StringUtils.isBlank(eth0Inet4InnerIp)) {
            log.error("获取本机eth0网卡ip地址失败");
            return Collections.emptyList();
        }
        log.info("本机eth0网卡的ip地址={}", eth0Inet4InnerIp);
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        List<TunnelInstance> enableList = tunnelInstances.stream()
                // 只启动本机器需要的隧道 && 0关闭 1开启
                .filter(element -> element.getLocation().equals(eth0Inet4InnerIp.trim()) && element.getEnable() == 1)
                .distinct()
                .collect(Collectors.toList());
        List<String> nameList = enableList.stream()
                .map(TunnelInstance::getAlias)
                .collect(Collectors.toList());
        // 理论上一个机器只有一个隧道，所以只用取第一个
        DEFAULT_TUNNEL_NAME = nameList.get(0);
        log.info("启用了 {} 条隧道, 分别是={}", nameList.size(), nameList);

        // add cache
        enableList.forEach(instance -> TUNNEL_INSTANCES_CACHE.put(instance.getAlias(), instance));
        return TUNNEL_INSTANCES_CACHE.values().stream().distinct().collect(Collectors.toList());
    }

    /**
     * 根据名称获取单个tunnel
     */
    public TunnelInstance getTunnel(String tunnelName) {
        if (!TUNNEL_INSTANCES_CACHE.isEmpty() && TUNNEL_INSTANCES_CACHE.containsKey(tunnelName)) {
            return TUNNEL_INSTANCES_CACHE.get(tunnelName);
        }
        log.error("get tunnel instance is null for [{}]", tunnelName);
        return null;
    }

    public TunnelInstance getDefaultTunnel() {
        return getTunnel(DEFAULT_TUNNEL_NAME);
    }

    /**
     * 更新隧道参数
     */
    void updateTunnelByEquals() {
        String eth0Inet4InnerIp = getEth0Inet4InnerIp();
        // get from db
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        // check element and update
        for (TunnelInstance tunnelInstance : tunnelInstances) {
            boolean belong2Local = tunnelInstance.getLocation().equals(eth0Inet4InnerIp.trim()) && tunnelInstance.getEnable() == 1;
            if (belong2Local) {
                String alias = tunnelInstance.getAlias();
                if (TUNNEL_INSTANCES_CACHE.containsKey(alias)) {
//                    TunnelInstance tunnel = TUNNEL_INSTANCES_CACHE.get(alias);
//                    String oldConfig = tunnel.toString();
//                    String newConfig = tunnelInstance.toString();
//                    if (!oldConfig.equals(newConfig)) {
//                        log.info("更新隧道参数: {}", tunnelInstance);
//                        // fixed bug: just update tunnel params on the machine
//                        TUNNEL_INSTANCES_CACHE.put(alias, tunnelInstance);
//                    }

                    log.info("更新隧道参数: {}", tunnelInstance);
                    // fixed bug: just update tunnel params on the machine
                    TUNNEL_INSTANCES_CACHE.put(alias, tunnelInstance);
                }
            }
        }
    }

    public int updateSuccessRate(String tunnelName, int rate, int useTimes) {
        return tunnelInitMapper.updateSuccessRate(tunnelName, rate, useTimes);
    }

    public int updateUsedIp(String tunnelName, int num) {
        return tunnelInitMapper.updateUsedIp(tunnelName, num);
    }

}
