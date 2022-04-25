package com.dataeye.proxy.service;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
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
import java.util.Objects;
import java.util.concurrent.*;
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
    /**
     * 循环检查ip拉取情况的时间间隔，单位：小时
     */
    private static final int CHECK_IP_FETCH_HOUR = 1;
    /**
     * ip池
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = new ConcurrentHashMap<>();
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchServiceImpl;
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
                // todo 某个隧道对应的ip池中的所有ip同时失效，可能性低
                log.warn("id存在，但是ip循环队列为空");
                getFixedNumIpAddr(queue, tunnelInstance);
                proxyIpPool.put(id, queue);
                return;
            }
            // 逐个检查ip的过期时间
            for (ProxyCfg next : queue) {
                if (isExpired(next)) {
                    queue.remove(next);
                    log.info("ip [{}] 即将过期或已经过期，移除", next.getHost());
                    // 放一个新的ip进去
                    getFixedNumIpAddr(queue, tunnelInstance);
                }
            }
        } else {
            log.warn("实例 {} 的ip池不存在，即将初始化", tunnelInstance.getAlias());
            ConcurrentLinkedQueue<ProxyCfg> queue = new ConcurrentLinkedQueue<>();
            getFixedNumIpAddr(queue, tunnelInstance);
            proxyIpPool.put(id, queue);
        }
    }

    /**
     * 获取固定数量的ip
     * ps: 因为会拉取到重复的ip或者过期的ip，所以ip池在更新的时候ip量总是小于10个，所以需要边轮询，边检查ip池数量是不是达到了固定的10个，不能只做简单的轮询10次
     */
    void getFixedNumIpAddr(ConcurrentLinkedQueue<ProxyCfg> queue, TunnelInstance tunnelInstance) {
        int fixedIpPoolSize = tunnelInstance.getFixedIpPoolSize();
        while (queue.size() < fixedIpPoolSize) {
            log.warn("当前ip池数量={}, 小于规定的 {} 个, 即将重试", queue.size(), fixedIpPoolSize);
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            checkBeforeUpdate(queue, tunnelInstance);
        }
    }

    /**
     * 在更新之前检查ip的有效时间
     *
     * @param queue ip循环队列
     */
    public void checkBeforeUpdate(ConcurrentLinkedQueue<ProxyCfg> queue, TunnelInstance tunnelInstance) {
        // 先检查，从代理商拉取的ip可能马上或者已经过期
        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
//            Optional<ProxyCfg> one = zhiMaProxyService.getOne();
//            if (!one.isPresent()) {
//                log.error("从代理商获取ip结果为空");
//                continue;
//            }

            List<ProxyCfg> data = zhiMaFetchServiceImpl.getMany(1);
            if (Objects.isNull(data) || data.isEmpty()) {
                log.error("从代理商获取ip结果为空");
                continue;
            }

            ProxyCfg newProxyCfg = data.get(0);
            if (isExpired(newProxyCfg)) {
                log.warn("拉取的ip已过期, ip={}, port={}, 时间={}", newProxyCfg.getHost(), newProxyCfg.getPort(), newProxyCfg.getExpireTime());
                continue;
            }
            // 还需要检查ip是否在ip池中重复了
            // todo 还有一种可能，queue中虽然不包含 newProxyCfg，但可能是ip相同，过期时间，或者端口不同的情况。
            if (queue.contains(newProxyCfg)) {
                log.warn("拉取的IP={}:{} 在IP池中已存在，即将重试", newProxyCfg.getHost(), newProxyCfg.getPort());
                continue;
            }
            // ip池满的就不用添加
            if (queue.size() >= tunnelInstance.getFixedIpPoolSize()) {
                log.warn("IP池已满, 配置数量={}, 真实数量={}, 取消添加", tunnelInstance.getFixedIpPoolSize(), queue.size());
                continue;
            }
            queue.add(newProxyCfg);
            break;
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
            log.error("proxyCfg is null");
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

    /**
     * 定时更新ip池
     */
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

    /**
     * 单位时间内 ip 拉取累计计数器
     */
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
