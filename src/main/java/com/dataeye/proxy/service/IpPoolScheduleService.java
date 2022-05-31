package com.dataeye.proxy.service;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.service.impl.YouJieFetchServiceImpl;
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
     * ip池
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = new ConcurrentHashMap<>();
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchServiceImpl;
    @Autowired
    YouJieFetchServiceImpl youJieFetchServiceImpl;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitService tunnelInitService;

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        checkAndUpdateIp();
        // ip池定时更新
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new ScheduleUpdateTask(), 0, 3, TimeUnit.SECONDS);
    }

    /**
     * 检查和更新代理IP列表
     *
     * @throws IOException
     */
    public void checkAndUpdateIp() throws IOException, InterruptedException {
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
    public void initSingleServer(TunnelInstance tunnelInstance) throws InterruptedException {
        String tunnel = tunnelInstance.getAlias();
        if (proxyIpPool.containsKey(tunnel)) {
            ConcurrentLinkedQueue<ProxyIp> queue = proxyIpPool.get(tunnel);
            if (queue == null || queue.isEmpty()) {
                // 某个隧道对应的ip池中的所有ip同时失效，但可能性低
                log.warn("id存在，但是ip循环队列为空");
                ConcurrentLinkedQueue<ProxyIp> newQueue = new ConcurrentLinkedQueue<>();
                getFixedNumIpAddr(newQueue, tunnelInstance, tunnelInstance.getFixedIpPoolSize());
                proxyIpPool.put(tunnel, newQueue);
                return;
            }
            // 逐个检查ip的过期时间
            for (ProxyIp next : queue) {
                if (isExpired(next)) {
                    next.getValid().set(false);
                    log.info("ip [{}] 即将过期或已经过期，移除", next.getHost());
                    // 放一个新的ip进去
                    getFixedNumIpAddr(queue, tunnelInstance, 1);
                }
            }

            // 会有一段时间的ip池数量不足
            // 如果queue中的有效ip数不够数量,则需要继续添加
            int validIpSize = getValidIpSize(queue);
            int fixedIpPoolSize = tunnelInstance.getFixedIpPoolSize();
            if (validIpSize < fixedIpPoolSize) {
                int fetchSize = fixedIpPoolSize - validIpSize;
                getFixedNumIpAddr(queue, tunnelInstance, fetchSize);
            }
        } else {
            log.warn("实例 {} 的ip池不存在，即将初始化", tunnelInstance.getAlias());
            ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
            getFixedNumIpAddr(queue, tunnelInstance, tunnelInstance.getFixedIpPoolSize());
            proxyIpPool.put(tunnel, queue);
        }
    }

    /**
     * 获取固定数量的ip
     * ps: 因为会拉取到重复的ip或者过期的ip，所以ip池在更新的时候ip量总是小于10个，所以需要边轮询，边检查ip池数量是不是达到了固定的10个，不能只做简单的轮询10次
     */
    void getFixedNumIpAddr(ConcurrentLinkedQueue<ProxyIp> queue, TunnelInstance tunnelInstance, int numOnce) throws InterruptedException {
        //TODO ip池数量不能减小的bug
        int fixedIpPoolSize = tunnelInstance.getFixedIpPoolSize();
        while (getValidIpSize(queue) < fixedIpPoolSize) {
            log.warn("当前ip池数量={}, 实际有效ip数={}, 小于规定的 {} 个, 即将重试", queue.size(), getValidIpSize(queue), fixedIpPoolSize);
            checkBeforeUpdate(queue, tunnelInstance, numOnce);
        }
    }

    /**
     * 在更新IP池之前, 检查拉取的ip是否已经过期, 是否已经存在于ip池
     *
     * @param numOnce 一次拉取ip个数
     * @param queue   ip循环队列
     */
    public void checkBeforeUpdate(ConcurrentLinkedQueue<ProxyIp> queue, TunnelInstance tunnelInstance, int numOnce) throws InterruptedException {
        // 先检查，从代理商拉取的ip可能马上或者已经过期
        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
            List<ProxyIp> data = zhiMaFetchServiceImpl.getIpList(numOnce, tunnelInstance);

//            List<ProxyIp> data;
//            // 优量使用芝麻   edx销量使用游杰
//            String alias = tunnelInstance.getAlias();
//            if ("youliang".equalsIgnoreCase(alias)) {
//                data = zhiMaFetchServiceImpl.getIpList(numOnce);
//            } else if ("edx-sale".equalsIgnoreCase(alias)) {
//                data = zhiMaFetchServiceImpl.getIpList(numOnce);
////                data = youJieFetchServiceImpl.getIpList(numOnce);
//            } else {
//                throw new RuntimeException("未知隧道名: " + alias);
//            }

            if (Objects.isNull(data) || data.isEmpty()) {
                log.error("从代理商获取ip结果为空, 即将重试");
                continue;
            }

            for (ProxyIp newProxyIp : data) {
                if (isExpired(newProxyIp)) {
                    log.warn("拉取的ip={} 已过期, 时间={}", newProxyIp.getIpAddr(), newProxyIp.getExpireTime());
                    continue;
                }
                // 还需要检查ip是否在ip池中重复了
                // todo 还有一种可能，queue中虽然不包含 newProxyIp，但可能是ip相同，过期时间，或者端口不同的情况。
                if (queue.contains(newProxyIp)) {
                    log.warn("拉取的IP={} 在IP池中已存在，即将重试", newProxyIp.getIpAddr());
                    continue;
                }
                // ip池满的就不用添加
                int validIpSize = getValidIpSize(queue);
                if (validIpSize >= tunnelInstance.getFixedIpPoolSize()) {
                    log.warn("IP池已满, 配置数量={}, 有效数量={}, 取消添加", tunnelInstance.getFixedIpPoolSize(), validIpSize);
                    continue;
                }
                queue.offer(newProxyIp);
            }
            break;
        }
    }

    /**
     * 检查是否过期
     *
     * @param proxyIp
     * @return
     */
    public boolean isExpired(ProxyIp proxyIp) {
        if (proxyIp == null) {
            log.error("proxyCfg is null");
            return true;
        }
        LocalDateTime expireTime = proxyIp.getExpireTime();
        //获取秒数, 提前过期
        long instanceSecond = expireTime.toEpochSecond(ZoneOffset.of("+8"));
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        long duration = instanceSecond - nowSecond;
        return duration < proxyServerConfig.getJudgeExpiredIpMinSeconds();
    }

    /**
     * 获取ip池有效ip数量
     *
     * @param queue ip池
     * @return
     */
    private int getValidIpSize(ConcurrentLinkedQueue<ProxyIp> queue) {
        return (int) queue.stream()
                .filter(proxyIp -> proxyIp.getValid().get())
                .distinct().count();
    }

    /**
     * 定时更新ip池
     */
    class ScheduleUpdateTask implements Runnable {

        @Override
        public void run() {
            try {
                checkAndUpdateIp();
                proxyIpPool.forEach((tunnel, queue) -> {
                    List<String> collect = queue.stream()
                            .map(ProxyIp::getIpAddrWithTimeAndValid)
                            .distinct()
                            .collect(Collectors.toList());
                    int validIpSize = getValidIpSize(queue);
                    log.info("tunnel={}, ip-pool-size={}, valid-ip-size={}, ip-pool-list={}", tunnel, queue.size(), validIpSize, JSON.toJSONString(collect));
                });
            } catch (Throwable e) {
                log.error("定时更新ip池出现异常，原因：{}", e.getCause().getMessage());
            }
        }
    }

}
