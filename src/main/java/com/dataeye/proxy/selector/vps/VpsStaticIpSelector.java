package com.dataeye.proxy.selector.vps;

import cn.hutool.extra.ssh.JschUtil;
import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.VpsInstanceService;
import com.dataeye.proxy.service.impl.VpsFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.jcraft.jsch.Session;
import lombok.Getter;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/11/15 18:34
 * @description
 */
@Component
public class VpsStaticIpSelector implements CommonIpSelector, DisposableBean {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("VpsStaticIpSelector");
    private final ConcurrentLinkedQueue<ProxyIp> ipPool = new ConcurrentLinkedQueue<>();
    @Autowired
    VpsInstanceService vpsInstanceService;
    @Getter
    List<VpsInstance> vpsInstances;
    @Autowired
    VpsFetchServiceImpl vpsFetchService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitService tunnelInitService;

    @Override
    public void init() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
            return;
        }
        // 获取所有vps实例
        getVpsInstancesFromDb();
        // 初始化所有VPS的SSH连接
        initSSH(vpsInstances);
        // 初始化ip池: 获取所有vps的 ppp0 网卡的ip
        List<ProxyIp> ipList = vpsFetchService.getAllLatestProxyIp(vpsInstances);
        if (CollectionUtils.isEmpty(ipList)) {
            throw new RuntimeException("初始化ip池失败");
        }
        // 将代理ip加入到ip池
        ipList.forEach(ipPool::offer);
        List<String> collect = ipPool.stream().map(ProxyIp::getIpWithVps).collect(Collectors.toList());
        log.info("初始化ip池完成, size={}, data={}", collect.size(), collect.toString());
    }

    /**
     * 刷新ip池
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    void refreshIpPool() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
            return;
        }
        List<ProxyIp> ipList = vpsFetchService.getAllLatestProxyIp(vpsInstances);
        // 删除原来的
        ipPool.forEach(e->e.getValid().set(false));
        log.info("标记删除原来的ip池: {}", JSON.toJSONString(ipPool));
        // 添加最新的
        ipList.forEach(ipPool::offer);
        log.info("更新ip池后: {}", JSON.toJSONString(ipPool));
    }

    /**
     * 初始化所有VPS的SSH连接
     */
    void initSSH(List<VpsInstance> vpsInstances) {
        if (CollectionUtils.isEmpty(vpsInstances)) {
            throw new RuntimeException("初始化SSH连接失败");
        }
        try {
            for (VpsInstance vi : vpsInstances) {
                // 连接后放入了缓存
                Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
                session.setTimeout(5000);
                log.info("init SSH: {}", vi.getInstanceInfo());
            }
        } catch (Exception e) {
            log.error("初始化SSH连接失败: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0/5 * * * * ?")
    void printIpPool() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
            return;
        }
        printIpPool(log, "vps", ipPool);
    }

    /**
     * 从数据库同步vps实例列表
     */
    @Scheduled(cron = "0/10 * * * * ?")
    void getVpsInstancesFromDb() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
            return;
        }
        List<VpsInstance> collect = vpsInstanceService.list().stream()
                .filter(e -> Objects.nonNull(e) && e.getValid() == 1 && e.getType() == TunnelType.VPS_DOMESTIC_STATIC.getId())
                .distinct()
                .collect(Collectors.toList());
        List<String> info = collect.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("从数据库同步vps实例列表: {}", JSON.toJSONString(info));
        vpsInstances = Optional.ofNullable(collect).orElse(new LinkedList<>());
    }

    @Override
    public void destroy() throws Exception {
        JschUtil.closeAll();
        System.out.println("关闭所有SSH会话");
    }

    @Override
    public ProxyIp getOne() {
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("get ip error, tunnelInstance is null");
            return null;
        }
        ProxyIp poll = ipPool.poll();
        if (Objects.isNull(poll)) {
            log.error("the ip from queue is null");
            return null;
        }
        // 只取有效的
        boolean valid = poll.getValid().get();
        if (!valid) {
            log.info("ip={} is invalid and will be removed", poll.getIpAddr());
            return getOne();
        }
        // 取了需要再放进去
        ipPool.offer(poll);
        return poll;
    }

    @Override
    public void addWhiteList() {

    }

    @Override
    public void healthCheck() {

    }

    @Override
    public void successPercentStatistics() {

    }

    @Override
    public void addFixedNumIp(int num) {

    }

    @Override
    public void removeIp(String ip, int port) {

    }

    @Override
    public ConcurrentLinkedQueue<ProxyIp> getIpPool() {
        return ipPool;
    }
}
