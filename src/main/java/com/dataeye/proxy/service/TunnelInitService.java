package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.NetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/24 18:06
 * @description
 */
@Service
public class TunnelInitService {

    public static final List<TunnelInstance> TUNNEL_INSTANCES = new LinkedList<>();
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("TunnelInitService");
    @Resource
    TunnelInitMapper tunnelInitMapper;
    @Value("${spring.profiles.active}")
    String profile;

    /**
     * 获取所有隧道实例配置列表
     *
     * @return 隧道实例配置列表
     */
    public List<TunnelInstance> getTunnelList() {
        if (!TUNNEL_INSTANCES.isEmpty()) {
            return TUNNEL_INSTANCES.stream().distinct().collect(Collectors.toList());
        }
        String eth0Inet4InnerIp;
        if ("local".equals(profile)) {
            eth0Inet4InnerIp = "localhost";
        } else {
            eth0Inet4InnerIp = NetUtils.getEth0Inet4InnerIp();
        }
        if (StringUtils.isBlank(eth0Inet4InnerIp)) {
            logger.error("获取本机eth0网卡ip地址失败");
            return Collections.emptyList();
        }
        logger.info("本机eth0网卡的ip地址={}", eth0Inet4InnerIp);
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        List<TunnelInstance> enableList = tunnelInstances.stream()
                // 只启动本机器需要的隧道
                .filter(element -> element.getLocation().equals(eth0Inet4InnerIp.trim()))
                .distinct()
                .collect(Collectors.toList());
        List<String> nameList = tunnelInstances.stream()
                // 只启动本机器需要的隧道
                .filter(element -> element.getLocation().equals(eth0Inet4InnerIp.trim()))
                .map(TunnelInstance::getAlias)
                .collect(Collectors.toList());
        logger.info("启用了 {} 条隧道, 分别是={}", nameList.size(), nameList);
        TUNNEL_INSTANCES.addAll(enableList);
        return TUNNEL_INSTANCES.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 根据名称获取单个tunnel
     */
    public TunnelInstance getTunnel(String tunnelName) {
        for (TunnelInstance element : getTunnelList()) {
            if (tunnelName.equalsIgnoreCase(element.getAlias())) {
                return element;
            }
        }
        return null;
    }

}
