package com.dataeye.proxy.service;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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

/**
 * @author caiguanghui
 */
@Slf4j
@Data
@Service
public class IpPoolScheduleService {

//    private static final Logger log = LogbackRollingFileUtil.getLogger("IpPoolScheduleService");

    @Autowired
    ZhiMaProxyService zhiMaProxyService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitMapper tunnelInitMapper;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    /**
     * ip池
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = new ConcurrentHashMap<>();
//    /**
//     * 线程循环检查ip是否有效的时间，单位：秒
//     */
////    int cycleCheckTime = 5 * 60;
//    int cycleCheckTime = 60;
//    /**
//     * 每个实例server对应的ip池中的ip数量
//     */
//    int ipSizeEachPool = 3;
//    /**
//     * 获取的ip是失效ip的次数
//     */
//    int failureIpGetCount = 5;
//    /**
//     * 提前判定ip为失效状态的最小时间间隔
//     */
//    int judgeFailMinTimeSeconds = 60;

    @PostConstruct
    public void init() {
        executorService.submit(new ScheduleUpdateTask());
    }


    class ScheduleUpdateTask implements Runnable {

        @Override
        public void run() {
            while (true) {
                log.warn("每 {}s 循环检查更新ip池", proxyServerConfig.getCycleCheckTime());
                try {
                    checkAndUpdateIp();
                    log.info("ip池检查更新完成");
                    proxyIpPool.forEach((instance, ipConfig) -> {
                        List<String> collect = ipConfig.stream().map(item -> item.getHost() + "(" + item.getExpireTime() + ")").collect(Collectors.toList());
                        log.info("实例:{}, ip池数量：{}, ip列表：{}", instance, ipConfig.size(), collect);
                    });
                    Thread.sleep(proxyServerConfig.getCycleCheckTime() * 1000L);
                } catch (Throwable e) {
                    log.error("定时更新ip池出现异常，原因：{}", e.getCause().getMessage());
                }
            }
        }
    }

    /**
     * 检查和更新代理IP列表
     *
     * @throws IOException
     */
    public void checkAndUpdateIp() throws IOException {
        List<TunnelInstance> tunnelInstanceList = tunnelInitMapper.queryAll();
        for (TunnelInstance tunnelInstance : tunnelInstanceList) {
            //todo id暂时换成name
//            String id = tunnelInstance.toString();
            String id = tunnelInstance.getAlias();
            if (proxyIpPool.containsKey(id)) {
                log.info("存在实例 {} 对应的ip池", tunnelInstance.getAlias());
                ConcurrentLinkedQueue<ProxyCfg> queue = proxyIpPool.get(id);
                if (queue == null || queue.isEmpty()) {
                    log.warn("id存在，但是ip循环队列为空");
                    for (int i = 0; i < proxyServerConfig.getIpSizeEachPool(); i++) {
//                        ProxyCfg proxyCfg = zhiMaProxyService.getOne().get();
//                        queue.add(proxyCfg);

                        checkBeforeUpdate(queue);
                    }
                    proxyIpPool.put(id, queue);
                    continue;
                }
                log.info("逐个检查ip的过期时间");
                for (ProxyCfg next : queue) {
                    if (next == null || checkExpireTime(next)) {
                        queue.remove(next);
                        // todo 直接放入
//                        ProxyCfg proxyCfg = zhiMaProxyService.getOne().get();
//                        queue.add(proxyCfg);

                        checkBeforeUpdate(queue);
                    }
                }
            } else {
                log.warn("实例 {} 的ip池不存在，即将初始化", tunnelInstance.getAlias());
                ConcurrentLinkedQueue<ProxyCfg> queue = new ConcurrentLinkedQueue<>();
                for (int i = 0; i < proxyServerConfig.getIpSizeEachPool(); i++) {
//                    ProxyCfg proxyCfg = zhiMaProxyService.getOne().get();
//                    queue.add(proxyCfg);

                    checkBeforeUpdate(queue);
                }
                proxyIpPool.put(id, queue);
            }
        }
    }

    void checkBeforeUpdate(ConcurrentLinkedQueue<ProxyCfg> queue) {
        // todo 先检查，获取的可能已经过期
        for (int i = 0; i < proxyServerConfig.getFailureIpGetCount(); i++) {
            System.out.println("zhiMaProxyService 为空？"+zhiMaProxyService);
            System.out.println("zhiMaProxyService.getOne() 为空？"+zhiMaProxyService);

            Optional<ProxyCfg> one = zhiMaProxyService.getOne();
            if (one.isPresent()) {
                ProxyCfg newProxyCfg = one.get();
                if (!checkExpireTime(newProxyCfg)) {
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
    boolean checkExpireTime(ProxyCfg proxyCfg) {
        if (proxyCfg == null) {
            return true;
        }
        LocalDateTime expireTime = proxyCfg.getExpireTime();
        //获取秒数, 提前过期
        long instanceSecond = expireTime.toEpochSecond(ZoneOffset.of("+8"));
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        long duration = instanceSecond - nowSecond;
        return duration < proxyServerConfig.getJudgeFailMinTimeSeconds();
//        return now.isAfter(expireTime);
    }

}
