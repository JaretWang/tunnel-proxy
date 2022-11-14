package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.VpsConfig;
import com.dataeye.proxy.selector.vps.VpsIpSelector;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.VpsInstanceService;
import com.dataeye.proxy.utils.CommandUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.TimeUtils;
import com.jcraft.jsch.JSchException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * vps服务器操作:
 * 更换ip，重启服务器，获取ip
 *
 * @author jaret
 * @date 2022/4/1 19:30
 * @description
 */
@Service
public class VpsFetchServiceImpl implements ProxyFetchService {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("VpsFetchServiceImpl");
    private final ConcurrentLinkedQueue<ProxyIp> latestProxyIpQueue = new ConcurrentLinkedQueue<>();
    @Autowired
    VpsConfig vpsConfig;
    @Resource
    TunnelInitService tunnelInitService;
    @Resource(name = "getIpFromAllVps")
    ThreadPoolTaskExecutor getIpFromAllVpsThreadPool;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    VpsIpSelector vpsIpSelector;

    /**
     * 定时获取所有vps的 ppp0 网卡的ip
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void scheduleGetAllVpsIp() {
        if (!vpsIpSelector.isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS)) {
            return;
        }
        long begin = System.currentTimeMillis();
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        if (defaultTunnel == null || defaultTunnel.getEnable() != 1 || defaultTunnel.getType() != TunnelType.VPS.getId()) {
            return;
        }
        List<VpsInstance> vpsInstances = vpsIpSelector.getVpsInstances();
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.error("vpsInstances is empty, quit");
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(vpsInstances.size());
        for (VpsInstance vi : vpsInstances) {
            getIpFromAllVpsThreadPool.submit(() -> {
                try {
                    String exec = CommandUtils.exec(VpsConfig.Operate.ifconfig.getCommand(), vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
                    if (StringUtils.isNotBlank(exec)) {
                        //String lineSeparator = System.lineSeparator();
                        String[] split = exec.split("\n");
                        if (split.length > 0) {
                            String host = split[0].trim();
                            if (StringUtils.isNotBlank(host)) {
                                ProxyIp build = ProxyIp.builder()
                                        .host(host)
                                        .port(vpsConfig.getDefaultPort())
                                        .userName(vpsConfig.getUsername())
                                        .password(vpsConfig.getPassword())
                                        .expireTime(LocalDateTime.now().plusSeconds(vpsConfig.getIpValidSeconds()))
                                        .valid(new AtomicBoolean(true))
                                        .okTimes(new AtomicLong(0))
                                        .errorTimes(new AtomicLong(0))
                                        .useTimes(new AtomicLong(0))
                                        .connecting(new AtomicLong(0))
                                        .vpsInstance(vi)
                                        .createTime(TimeUtils.formatLocalDate(LocalDateTime.now()))
                                        .updateTime(TimeUtils.formatLocalDate(LocalDateTime.now()))
                                        .build();
                                latestProxyIpQueue.offer(build);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.info("获取ip异常, cause={}", e.getMessage(), e);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        try {
            countDownLatch.await();
        } catch (Exception e) {
            log.info("countDownLatch error, cause={}", e.getMessage(), e);
        }
        Map<String, String> collect = latestProxyIpQueue.stream().collect(Collectors.toMap(e -> e.getVpsInstance().getIp(), ProxyIp::getHost, (e1, e2) -> e2));
        log.info("定时获取所有vps的 ppp0 网卡的ip, cost={} ms, ip列表={}", (System.currentTimeMillis() - begin), JSON.toJSONString(collect));
    }

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws JSchException {
        List<ProxyIp> ipList = getIpList();
        if (CollectionUtils.isEmpty(ipList)) {
            return null;
        }
        return ipList.get(0);
    }

    /**
     * 获取ip
     */
    public List<ProxyIp> getIpList() {
        if (latestProxyIpQueue.isEmpty()) {
            log.error("latestProxyIpQueue is empty");
            return Collections.emptyList();
        }
        List<ProxyIp> collect = latestProxyIpQueue.stream().distinct().collect(Collectors.toList());
        return Optional.ofNullable(collect).orElse(new LinkedList<>());
    }

}
