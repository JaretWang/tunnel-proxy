package com.dataeye.proxy.service.impl;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.extra.ssh.JschUtil;
import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.VpsConfig;
import com.dataeye.proxy.selector.vps.VpsIpSelector;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.TimeUtils;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
        long begin = System.currentTimeMillis();
        List<VpsInstance> vpsInstances = vpsIpSelector.getVpsInstances();
        List<ProxyIp> allLatestProxyIp = getAllLatestProxyIp(vpsInstances);
        List<String> collect2 = allLatestProxyIp.stream().map(ProxyIp::getIpAddrWithTimeAndValid).collect(Collectors.toList());
        log.info("获取所有vps的 ppp0 网卡的ip, cost={} ms, size={}, 代理ip列表={}", (System.currentTimeMillis() - begin), allLatestProxyIp.size(), collect2.toString());
        // 加入ip池
        allLatestProxyIp.forEach(vpsIpSelector::addIpPool);
    }

    /**
     * 获取最新的所有vps的代理ip列表
     * @return
     */
    public List<ProxyIp> getAllLatestProxyIp(List<VpsInstance> vpsInstances) {
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.error("vpsInstances is empty, quit");
            return Collections.emptyList();
        }
        List<String> collect = vpsInstances.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("vps实例列表: size={}, data={}", collect.size(), JSON.toJSONString(collect));
        CountDownLatch countDownLatch = new CountDownLatch(vpsInstances.size());
        CopyOnWriteArrayList<ProxyIp> result = new CopyOnWriteArrayList<>();
        for (VpsInstance vi : vpsInstances) {
            getIpFromAllVpsThreadPool.submit(() -> {
                try {
                    ProxyIp proxyIp = getSingleVpsInstanceIp(vi);
                    if (proxyIp != null) {
                        result.add(proxyIp);
                        log.info("添加代理ip成功, proxyIp={}", proxyIp.getIpAddrWithTimeAndValid());
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
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    public ProxyIp getSingleVpsInstanceIp(VpsInstance vi) {
        try {
            //String exec = CommandUtils.exec(VpsConfig.Operate.ifconfig.getCommand(), vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
            Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
            String exec = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
            if (StringUtils.isBlank(exec)) {
                log.error("获取代理ip失败, exec={}, vps={}", exec, vi.getInstanceInfo());
                return null;
            }
            //String lineSeparator = System.lineSeparator();
            String[] split = exec.split("\n");
            if (split.length > 0) {
                String host = split[0].trim();
                if (StringUtils.isNotBlank(host)) {
                    return ProxyIp.builder()
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
                }
            }
        } catch (Exception e) {
            log.error("获取单个vps实例的代理ip失败, vps={}", vi.getInstanceInfo());
        }
        return null;
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
        return Collections.emptyList();
    }

}
