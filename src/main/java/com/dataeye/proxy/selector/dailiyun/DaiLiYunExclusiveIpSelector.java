package com.dataeye.proxy.selector.dailiyun;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.DaiLiYunExclusiveFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 代理云独享ip
 *
 * @author jaret
 * @date 2022/8/17 23:01
 * @description
 */
@Data
@Component
public class DaiLiYunExclusiveIpSelector implements CommonIpSelector {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("DaiLiYunExclusiveIpSelector");
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("DaiLiYunExclusiveIpSelector-"), new ThreadPoolExecutor.AbortPolicy());
    private final ConcurrentLinkedQueue<ProxyIp> IP_POOL = new ConcurrentLinkedQueue<>();
    String username = "18922868909";
    String password = "18922868909";
    @Autowired
    DaiLiYunExclusiveFetchServiceImpl daiLiYunExclusiveFetchService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitService tunnelInitService;

    @Override
    public ProxyIp getOne() {
        ProxyIp poll = IP_POOL.poll();
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
        IP_POOL.offer(poll);

        // 添加用户名密码
        return poll;
    }

    @Override
    public List<ProxyIp> getIpList(int count) {
        LinkedList<ProxyIp> ips = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            ProxyIp proxyIp = getOne();
            if (proxyIp != null) {
                ips.add(proxyIp);
            }
        }
        return ips;
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
        return IP_POOL;
    }

    @Override
    public void init() {
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(this::checkAndUpdateIpPool, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * 检查更新代理IP池
     */
    public void checkAndUpdateIpPool() {
        ipCheck();
        printIpPool();
    }

    /**
     * ip有效时间检查
     */
    public void ipCheck() {
        if (IP_POOL.isEmpty()) {
            addFixedIp("initQueue: 初始化添加", IP_POOL, 5);
            return;
        }
        // 逐个检查ip的过期时间
        for (ProxyIp ip : IP_POOL) {
            if (isExpired(ip)) {
                ip.getValid().set(false);
                log.info("ip={} 即将过期或已经过期，移除", ip.getIpAddrWithTime());
                addFixedIp("ipCheck: ip过期追加", IP_POOL, 1);
            }
        }
    }

    /**
     * 获取有效的ip字符串，便于去重
     * @return
     */
    public List<String> getRealIpList(){
        return IP_POOL.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
    }

    public void printIpPool() {
        List<String> collect = IP_POOL.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
        String tunnel = tunnelInitService.getDefaultTunnel().getAlias();
        int validIpSize = getValidIpSize(IP_POOL);
        log.info("tunnel={}, ip-pool-size={}, valid-ip-size={}, ip-pool-list={}", tunnel, IP_POOL.size(), validIpSize, JSON.toJSONString(collect));
    }

    /**
     * 添加固定数量的ip
     *
     * @param queue          IP池
     * @param needIpSize     需要的ip数
     */
    public boolean addFixedIp(String addReason, ConcurrentLinkedQueue<ProxyIp> queue, int needIpSize) {
        List<String> realIpList = getRealIpList();
        boolean status = false;
        // 先检查，从代理商拉取的ip可能马上或者已经过期
        int realCount = 0, expired = 0, exist = 0, empty = 0;
        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
            List<ProxyIp> data = daiLiYunExclusiveFetchService.getIpList(needIpSize);
            if (Objects.isNull(data) || data.isEmpty()) {
                log.error("从代理商获取ip结果为空, 即将重试");
                empty++;
                continue;
            }
            for (ProxyIp newProxyIp : data) {
                if (isExpired(newProxyIp)) {
                    log.warn("拉取的ip={} 已过期, 时间={}", newProxyIp.getIpAddr(), newProxyIp.getExpireTime());
                    expired++;
                    continue;
                }
                // 还需要检查ip是否在ip池中重复了
                // 还有一种可能，queue中虽然不包含 newProxyIp，但可能是ip相同，过期时间，或者端口不同的情况。(暂不处理)
                if (queue.contains(newProxyIp)) {
                    log.warn("拉取的IP={} 在IP池中已存在，即将重试", newProxyIp.getIpAddr());
                    exist++;
                    continue;
                }
                if (realIpList.contains(newProxyIp.getIpAddrWithTimeAndValid())) {
                    exist++;
                    log.info("手动去重: {}", newProxyIp.getIpAddrWithTime());
                    continue;
                }
                realCount++;

                // 设置用户名密码
                newProxyIp.setUserName(username);
                newProxyIp.setPassword(password);
                queue.offer(newProxyIp);
            }
            break;
        }
        if (realCount >= needIpSize) {
            status = true;
        }
        log.warn("添加成功, 添加原因={}, enough={}, needIpSize={}, realCount={}, expired={}, exist={}, empty={}",
                addReason, status, needIpSize, realCount, expired, exist, empty);
        return status;
    }

    /**
     * 检查ip是否过期
     *
     * @param proxyIp
     * @return
     */
    public boolean isExpired(ProxyIp proxyIp) {
        if (proxyIp == null) {
            log.error("proxyIp is null");
            return true;
        }
        LocalDateTime expireTime = proxyIp.getExpireTime();
        //获取秒数, 提前过期
        long instanceSecond = expireTime.toEpochSecond(ZoneOffset.of("+8"));
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        long duration = instanceSecond - nowSecond;
//        return duration < proxyServerConfig.getJudgeExpiredIpMinSeconds();
        return duration < 30;
    }

    /**
     * 获取ip池有效ip数量
     *
     * @param queue ip池
     * @return
     */
    public int getValidIpSize(ConcurrentLinkedQueue<ProxyIp> queue) {
        if (queue == null) {
            return 0;
        }
        return (int) queue.stream()
                .filter(proxyIp -> proxyIp.getValid().get())
                .distinct().count();
    }

}
