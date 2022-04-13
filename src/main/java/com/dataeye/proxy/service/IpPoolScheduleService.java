package com.dataeye.proxy.service;

import com.dataeye.commonx.domain.ProxyCfg;

import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Data
@Service
public class IpPoolScheduleService {

    private static final Logger log = LogbackRollingFileUtil.getLogger("IpPoolScheduleService");

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    /**
     * ip池
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = new ConcurrentHashMap<>();
    @Autowired
    ZhiMaProxyService zhiMaProxyService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitMapper tunnelInitMapper;

    @PostConstruct
    public void init() {
        executorService.submit(new ScheduleUpdateTask());
    }

    /**
     * 检查和更新代理IP列表
     *
     * @throws IOException
     */
    public void checkAndUpdateIp() throws IOException {
        List<TunnelInstance> tunnelInstanceList = tunnelInitMapper.queryAll();
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

    void checkBeforeUpdate(ConcurrentLinkedQueue<ProxyCfg> queue) {
        // 先检查，从芝麻拉取的ip可能马上或者已经过期
        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
            Optional<ProxyCfg> one = zhiMaProxyService.getOne();
            if (one.isPresent()) {
                ProxyCfg newProxyCfg = one.get();
                if (isExpired(newProxyCfg)) {
                    log.warn("从芝麻拉取的ip已经过期, 具体ip: {}, 时间：{}", newProxyCfg.getHost(), newProxyCfg.getExpireTime());
                } else {
                    queue.add(newProxyCfg);
                    break;
                }
            } else {
                log.error("芝麻代理服务获取ip结果为空");
            }
        }
    }

    /**
     * 检查是否过期
     *
     * @param proxyCfg
     * @return
     */
    boolean isExpired(ProxyCfg proxyCfg) {
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
            while (true) {
                log.warn("每 {}s 循环检查更新ip池", proxyServerConfig.getCycleCheckTime());
                try {
                    checkAndUpdateIp();
                    proxyIpPool.forEach((instance, ipConfig) -> {
                        List<String> collect = ipConfig.stream().map(item -> item.getHost() + "(" + item.getExpireTime() + ")").collect(Collectors.toList());
                        log.info("实例 [{}], ip池数量 [{}], ip列表：{}", instance, ipConfig.size(), collect);
                    });
                    Thread.sleep(proxyServerConfig.getCycleCheckTime() * 1000L);
                } catch (Throwable e) {
                    log.error("定时更新ip池出现异常，原因：{}", e.getCause().getMessage());
                }
            }
        }
    }

}
