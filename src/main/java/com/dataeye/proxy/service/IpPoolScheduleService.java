package com.dataeye.proxy.service;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/14 10:52
 */
@Data
@Service
public class IpPoolScheduleService {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("IpPoolScheduleService");
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(2,
            new ThreadPoolConfig.TunnelThreadFactory("ip-pool-schedule-"), new ThreadPoolExecutor.AbortPolicy());
    /**
     * ip拉取记录计数器
     */
    private static final ConcurrentHashMap<String, Short> IP_FETCH_RECORD = new ConcurrentHashMap<>();
    private static final int CHECK_IP_FETCH_HOUR = 1;
    /**
     * 统计时间
     */
    private final AtomicInteger statisticsTime = new AtomicInteger();
    /**
     * ip池
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = new ConcurrentHashMap<>();
    @Autowired
    ZhiMaProxyService zhiMaProxyService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitService tunnelInitService;

    @PostConstruct
    public void init() {
        // ip池定时更新
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new ScheduleUpdateTask(), 0, 3, TimeUnit.SECONDS);
        // ip使用记录，监控计数
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new IpFetchCounter(), 0, CHECK_IP_FETCH_HOUR, TimeUnit.HOURS);
    }

    /**
     * 检查和更新代理IP列表
     *
     * @throws IOException
     */
    public void checkAndUpdateIp() throws IOException {
        List<TunnelInstance> tunnelInstanceList = tunnelInitService.getTunnelList();
        for (TunnelInstance tunnelInstance : tunnelInstanceList) {
            initSingleServer(tunnelInstance);
        }
    }

    /**
     * 初始化单个server
     *
     * @param tunnelInstance
     */
    public void initSingleServer(TunnelInstance tunnelInstance) {
        String id = tunnelInstance.getAlias();
        if (proxyIpPool.containsKey(id)) {
            ConcurrentLinkedQueue<ProxyCfg> queue = proxyIpPool.get(id);
            if (queue == null || queue.isEmpty()) {
                // todo 某个id对应的ip为空,会存在IP池所有的ip同时失效的情况
                log.warn("id存在，但是ip循环队列为空");
                for (int i = 0; i < tunnelInstance.getFixedIpPoolSize(); i++) {
                    checkBeforeUpdate(queue);
                }
                proxyIpPool.put(id, queue);
                return;
            }
            // 逐个检查ip的过期时间
            for (ProxyCfg next : queue) {
                if (isExpired(next)) {
                    queue.remove(next);
                    log.info("ip [{}] 即将过期或已经过期，移除", next.getHost());
                    // 放一个新的ip进去
                    checkBeforeUpdate(queue);
                }
            }
        } else {
            log.warn("实例 {} 的ip池不存在，即将初始化", tunnelInstance.getAlias());
            ConcurrentLinkedQueue<ProxyCfg> queue = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < tunnelInstance.getFixedIpPoolSize(); i++) {
                checkBeforeUpdate(queue);
            }
            proxyIpPool.put(id, queue);
        }
    }

    /**
     * 在更新之前检查ip的有效时间
     *
     * @param queue ip循环队列
     */
    public void checkBeforeUpdate(ConcurrentLinkedQueue<ProxyCfg> queue) {
        // 先检查，从代理商拉取的ip可能马上或者已经过期
        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
            Optional<ProxyCfg> one = zhiMaProxyService.getOne();
            if (one.isPresent()) {
                ProxyCfg newProxyCfg = one.get();
                if (isExpired(newProxyCfg)) {
                    log.warn("拉取的ip已经过期, 具体ip: {}, 时间：{}", newProxyCfg.getHost(), newProxyCfg.getExpireTime());
                } else {
                    // 还需要检查ip是否在ip池中重复了
                    if (queue.contains(newProxyCfg)) {
                        log.warn("拉取的IP={}:{} 在IP池中已存在，即将重试", newProxyCfg.getHost(), newProxyCfg.getPort());
                        continue;
                    }
                    //todo 还有一种可能，queue中虽然不包含 newProxyCfg，但可能是ip相同，过期时间，或者端口不同的情况。
                    queue.add(newProxyCfg);
                    break;
                }
            } else {
                log.error("从代理商获取ip结果为空");
            }
        }
    }

    /**
     * 检查是否过期
     *
     * @param proxyCfg
     * @return
     */
    public boolean isExpired(ProxyCfg proxyCfg) {
        if (proxyCfg == null) {
            return true;
        }
        LocalDateTime expireTime = proxyCfg.getExpireTime();
        //获取秒数, 提前过期
        long instanceSecond = expireTime.toEpochSecond(ZoneOffset.of("+8"));
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        long duration = instanceSecond - nowSecond;
        return duration < proxyServerConfig.getJudgeExpiredIpMinSeconds();
//        return now.isAfter(expireTime);
    }

    class ScheduleUpdateTask implements Runnable {

        @Override
        public void run() {
            try {
                checkAndUpdateIp();
                proxyIpPool.forEach((instance, ipConfig) -> {
                    List<String> collect = ipConfig.stream()
                            .map(item -> item.getHost() + ":" + item.getPort() + "(" + item.getExpireTime() + ")")
                            .collect(Collectors.toList());
                    log.info("instance={}, ip-pool-size={}, ip-pool-list={}", instance, ipConfig.size(), collect);
                });
            } catch (Throwable e) {
                log.error("定时更新ip池出现异常，原因：{}", e.getCause().getMessage());
            }
        }
    }

    class IpFetchCounter implements Runnable {

        @Override
        public void run() {
            if (proxyIpPool.isEmpty()) {
                log.error("ip拉取计数器异常：ip池为空");
                return;
            }
            for (ConcurrentLinkedQueue<ProxyCfg> pool : proxyIpPool.values()) {
                // 添加到计数器容器中
                for (ProxyCfg proxyCfg : pool) {
                    String host = proxyCfg.getHost();
                    int port = proxyCfg.getPort();
                    String proxyAddr = host + ":" + port;
                    IP_FETCH_RECORD.putIfAbsent(proxyAddr, (short) 1);
                }
            }
            log.info("{} 小时内，已拉取IP数量={}", CHECK_IP_FETCH_HOUR, IP_FETCH_RECORD.size());
        }
    }

}
