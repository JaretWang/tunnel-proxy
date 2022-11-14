package com.dataeye.proxy.selector;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.service.TunnelInitService;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 一般的ip选择器包含以下几个功能：
 * 添加ip白名单，初始化ip池，分发ip，维护ip池，自动更换ip，自动剔除劣质ip，监控ip使用情况，监控请求使用情况
 *
 * @author jaret
 * @date 2022/8/17 23:01
 * @description
 */
public interface CommonIpSelector {

    /**
     * 添加ip白名单
     */
    void addWhiteList();

    /**
     * 初始化ip池
     */
    void init();

    /**
     * 获取一个ip
     *
     * @return 代理ip
     */
    ProxyIp getOne();

    /**
     * 获取多个ip
     *
     * @param count 需要数量
     * @return 代理ip列表
     * @throws Exception
     */
    default List<ProxyIp> getIpList(int count) throws Exception {
        LinkedList<ProxyIp> ips = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            ProxyIp proxyIp = getOne();
            if (proxyIp != null) {
                ips.add(proxyIp);
            }
        }
        return ips;
    }

    /**
     * ip池健康检查
     */
    void healthCheck();

    /**
     * 成功率统计
     */
    void successPercentStatistics();

    /**
     * 添加固定数量的ip到ip池
     */
    void addFixedNumIp(int num);

    /**
     * 从ip池中移除ip
     */
    void removeIp(String ip, int port);

    /**
     * 获取ip池
     *
     * @return
     */
    ConcurrentLinkedQueue<ProxyIp> getIpPool();

    default boolean isStart(TunnelInitService tunnelInitService, ProxyServerConfig proxyServerConfig, TunnelType tunnelType) {
        String innerIp = tunnelInitService.getEth0Inet4InnerIp();
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        return proxyServerConfig.isEnable()
                && defaultTunnel.getType() == tunnelType.getId()
                && defaultTunnel.getLocation().equalsIgnoreCase(innerIp.trim())
                && defaultTunnel.getEnable() == 1;
    }

    /**
     * 处理失效ip
     */
    default void handleInvalidIp(Logger logger, TunnelInstance tunnelInstance, ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool) {
        if (proxyIpPool == null || proxyIpPool.isEmpty() || tunnelInstance == null) {
            logger.error("处理失效ip, quit");
            return;
        }
        String alias = tunnelInstance.getAlias();
        ConcurrentLinkedQueue<ProxyIp> proxyIps = proxyIpPool.get(alias);
        if (proxyIps == null || proxyIps.isEmpty()) {
            return;
        }
        int total = proxyIps.size();
        int validIpSize = getValidIpSize(proxyIps);
        int invalidIp = total - validIpSize;
        if (invalidIp > 0) {
            for (ProxyIp proxyIp : proxyIps) {
                AtomicBoolean valid = proxyIp.getValid();
                if (!valid.get()) {
                    proxyIps.remove(proxyIp);
                    logger.info("处理失效ip: {}", proxyIp.getIpAddrWithTimeAndValid());
                }
            }
        }
    }

    default void handleInvalidIp(Logger logger, ConcurrentLinkedQueue<ProxyIp> proxyIps) {
        if (proxyIps == null || proxyIps.isEmpty()) {
            logger.error("处理失效ip, ip池为空, quit");
            return;
        }
        int total = proxyIps.size();
        int validIpSize = getValidIpSize(proxyIps);
        int invalidIp = total - validIpSize;
        if (invalidIp > 0) {
            for (ProxyIp proxyIp : proxyIps) {
                AtomicBoolean valid = proxyIp.getValid();
                if (!valid.get()) {
                    proxyIps.remove(proxyIp);
                    logger.info("处理失效ip: {}", proxyIp.getIpAddrWithTimeAndValid());
                }
            }
        }
    }

    /**
     * 打印ip池
     */
    default void printIpPool(Logger logger, ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool) {
        if (proxyIpPool == null) {
            return;
        }
        proxyIpPool.forEach((tunnel, queue) -> {
            List<String> collect = queue.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
            int validIpSize = getValidIpSize(queue);
            logger.info("tunnel={}, ip-pool-size={}, valid-ip-size={}, ip-pool-list={}", tunnel, queue.size(), validIpSize, JSON.toJSONString(collect));
        });
    }

    default void printIpPool(Logger logger, String tunnel, ConcurrentLinkedQueue<ProxyIp> queue) {
        if (queue == null) {
            return;
        }
        List<String> collect = queue.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
        int validIpSize = getValidIpSize(queue);
        logger.info("tunnel={}, ip-pool-size={}, valid-ip-size={}, ip-pool-list={}", tunnel, queue.size(), validIpSize, JSON.toJSONString(collect));
    }

    /**
     * 获取ip池有效ip数量
     *
     * @param queue ip池
     * @return ip数
     */
    default int getValidIpSize(ConcurrentLinkedQueue<ProxyIp> queue) {
        if (queue == null) {
            return 0;
        }
        return (int) queue.stream()
                .filter(proxyIp -> proxyIp.getValid().get())
                .distinct().count();
    }

}
