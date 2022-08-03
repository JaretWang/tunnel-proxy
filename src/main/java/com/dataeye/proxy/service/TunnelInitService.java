package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.TunnelMonitorLog;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.NetUtils;
import com.dataeye.proxy.utils.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/24 18:06
 * @description
 */
@Service
public class TunnelInitService {

    /**
     * 本机器使用的隧道
     */
    public static final ConcurrentHashMap<String, TunnelInstance> TUNNEL_INSTANCES_CACHE = new ConcurrentHashMap<>();
    /**
     * 正在使用中的所有隧道
     */
    public static final ConcurrentHashMap<String, TunnelInstance> ALL_USED_TUNNEL = new ConcurrentHashMap<>();
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("TunnelInitService");
    private static String DEFAULT_TUNNEL_NAME;
    @Resource
    TunnelInitMapper tunnelInitMapper;
    @Value("${spring.profiles.active}")
    String profile;
    String eth0Inet4InnerIp;
    @Autowired
    ProxyServerConfig proxyServerConfig;

    @PostConstruct
    private void getEth0Inet4InnerIp() {
//        if (!proxyServerConfig.isEnable()) {
//            return;
//        }
        if ("local".equals(profile)) {
            eth0Inet4InnerIp = "localhost";
        } else {
            eth0Inet4InnerIp = NetUtils.getEth0Inet4InnerIp();
        }
    }

    /**
     * 获取所有正在使用中的隧道
     *
     * @return
     */
    public List<TunnelInstance> getAllUsedTunnel() {
        if (!ALL_USED_TUNNEL.isEmpty()) {
            return ALL_USED_TUNNEL.values().stream().distinct().collect(Collectors.toList());
        }
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        if (tunnelInstances == null || tunnelInstances.isEmpty()) {
            return Collections.emptyList();
        }
        List<TunnelInstance> instanceList = tunnelInstances.stream()
                // 只启动本机器需要的隧道 && 0关闭 1开启
                .filter(element -> element.getEnable() == 1)
                .distinct()
                .collect(Collectors.toList());
        instanceList.forEach(instance -> ALL_USED_TUNNEL.put(instance.getAlias(), instance));
        return ALL_USED_TUNNEL.values().stream().distinct().collect(Collectors.toList());
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
        if (StringUtils.isBlank(eth0Inet4InnerIp)) {
            logger.error("获取本机eth0网卡ip地址失败");
            return Collections.emptyList();
        }
        logger.info("本机eth0网卡的ip地址={}", eth0Inet4InnerIp);
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
        logger.info("启用了 {} 条隧道, 分别是={}", nameList.size(), nameList);

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
        logger.error("get tunnel instance is null for [{}]", tunnelName);
        return null;
    }

    public TunnelInstance getDefaultTunnel() {
        return getTunnel(DEFAULT_TUNNEL_NAME);
    }

    /**
     * 定时更新隧道列表缓存
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void schduleUpdateTunnelListCache() {
//        updateTunnelByTime();
        updateTunnelByEquals();
    }

    void updateTunnelByEquals() {
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
//                        logger.info("更新隧道参数: {}", tunnelInstance);
//                        // fixed bug: just update tunnel params on the machine
//                        TUNNEL_INSTANCES_CACHE.put(alias, tunnelInstance);
//                    }

                    logger.info("更新隧道参数: {}", tunnelInstance);
                    // fixed bug: just update tunnel params on the machine
                    TUNNEL_INSTANCES_CACHE.put(alias, tunnelInstance);
                }
            }
        }
    }

    void updateTunnelByTime() {
        // get from db
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        // check element and update
        for (TunnelInstance tunnelInstance : tunnelInstances) {
            String lastModified = tunnelInstance.getLastModified();
            LocalDateTime lastUpdateTime = TimeUtils.str2LocalDate(lastModified);
            boolean update = LocalDateTime.now().isBefore(lastUpdateTime.plusSeconds(5));
            boolean belong2Local = tunnelInstance.getLocation().equals(eth0Inet4InnerIp.trim()) && tunnelInstance.getEnable() == 1;
            if (update && belong2Local) {
                logger.info("更新隧道参数: {}", tunnelInstance);
                // fixed bug: just update tunnel params on the machine
                String alias = tunnelInstance.getAlias();
                TUNNEL_INSTANCES_CACHE.put(alias, tunnelInstance);
            }
        }
    }

    /**
     * 更新ip检查规则
     */
    public int updateTunnel(TunnelInstance tunnelInstance) {
        return tunnelInitMapper.updateTunnel(tunnelInstance);
    }

    public int updateSuccessRate(String tunnelName, int rate, int useTimes) {
        return tunnelInitMapper.updateSuccessRate(tunnelName, rate, useTimes);
    }

    public int updateUsedIp(String tunnelName, int num) {
        return tunnelInitMapper.updateUsedIp(tunnelName, num);
    }

}
