//package com.dataeye.proxy.service;
//
//import com.alibaba.fastjson.JSON;
//import com.dataeye.proxy.apn.bean.ProxyIp;
//import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
//import com.dataeye.proxy.bean.dto.TunnelInstance;
//import com.dataeye.proxy.config.ProxyServerConfig;
//import com.dataeye.proxy.config.ThreadPoolConfig;
//import com.dataeye.proxy.service.impl.YouJieFetchServiceImpl;
//import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
//import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
//import com.dataeye.proxy.utils.TimeUtils;
//import lombok.Data;
//import org.slf4j.Logger;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.Resource;
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDateTime;
//import java.time.ZoneOffset;
//import java.util.List;
//import java.util.Objects;
//import java.util.concurrent.*;
//import java.util.stream.Collectors;
//
///**
// * ip池
// *
// * @author jaret
// * @date 2022/4/14 10:52
// */
//@Data
//@Service
//public class IpPoolScheduleService {
//
//    private static final Logger log = MyLogbackRollingFileUtil.getLogger("IpPoolScheduleService");
//    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(2,
//            new ThreadPoolConfig.TunnelThreadFactory("ip-pool-schedule-"), new ThreadPoolExecutor.AbortPolicy());
//    /**
//     * 普通ip池
//     */
//    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = new ConcurrentHashMap<>();
//    /**
//     * 带优先级的ip池
//     */
////    private final ConcurrentHashMap<String, PriorityBlockingQueue<ProxyIp>> proxyIpPool = new ConcurrentHashMap<>();
//    @Autowired
//    ZhiMaFetchServiceImpl zhiMaFetchServiceImpl;
//    @Autowired
//    YouJieFetchServiceImpl youJieFetchServiceImpl;
//    @Autowired
//    ProxyServerConfig proxyServerConfig;
//    @Resource
//    TunnelInitService tunnelInitService;
//
//    @PostConstruct
//    public void init() throws InterruptedException {
//        if (!proxyServerConfig.isEnable()) {
//            return;
//        }
//        checkAndUpdateIp();
//        // ip池定时更新
//        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new ScheduleUpdateTask(), 0, 3, TimeUnit.SECONDS);
//    }
//
//    /**
//     * 检查和更新代理IP列表
//     */
//    public void checkAndUpdateIp() throws InterruptedException {
//        for (TunnelInstance tunnelInstance : tunnelInitService.getTunnelList()) {
//            initSingleServer(tunnelInstance);
//        }
//    }
//
//    /**
//     * 初始化单个server
//     *
//     * @param tunnelInstance
//     */
//    public void initSingleServer(TunnelInstance tunnelInstance) throws InterruptedException {
//        String tunnel = tunnelInstance.getAlias();
//        if (proxyIpPool.containsKey(tunnel)) {
//            ConcurrentLinkedQueue<ProxyIp> queue = proxyIpPool.get(tunnel);
//            if (queue == null || queue.isEmpty()) {
//                // 某个隧道对应的ip池中的所有ip同时失效，但可能性低
//                log.warn("id存在，但是ip循环队列为空");
//                ConcurrentLinkedQueue<ProxyIp> newQueue = new ConcurrentLinkedQueue<>();
//                getFixedNumIpAddr(newQueue, tunnelInstance, tunnelInstance.getCoreIpSize());
//                proxyIpPool.put(tunnel, newQueue);
//                return;
//            }
//            // 逐个检查ip的过期时间
//            for (ProxyIp next : queue) {
//                if (isExpired(next)) {
//                    next.getValid().set(false);
//                    log.info("ip [{}] 即将过期或已经过期，移除", next.getHost());
//                    // 放一个新的ip进去
//                    getFixedNumIpAddr(queue, tunnelInstance, 1);
//                }
//            }
//
//            // 会有一段时间的ip池数量不足, 果queue中的有效ip数不够数量,则需要继续添加
//            int validIpSize = getValidIpSize(queue);
//            int fixedIpPoolSize = tunnelInstance.getCoreIpSize();
//            if (validIpSize < fixedIpPoolSize) {
//                int fetchSize = fixedIpPoolSize - validIpSize;
//                getFixedNumIpAddr(queue, tunnelInstance, fetchSize);
//            }
//        } else {
//            log.warn("实例 {} 的ip池不存在，即将初始化", tunnelInstance.getAlias());
//            ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
//            getFixedNumIpAddr(queue, tunnelInstance, tunnelInstance.getCoreIpSize());
//            proxyIpPool.put(tunnel, queue);
//        }
//    }
//
//    /**
//     * 获取固定数量的ip
//     * ps: 因为会拉取到重复的ip或者过期的ip，所以ip池在更新的时候ip量总是小于10个，所以需要边轮询，边检查ip池数量是不是达到了固定的10个，不能只做简单的轮询10次
//     */
//    void getFixedNumIpAddr(ConcurrentLinkedQueue<ProxyIp> queue, TunnelInstance tunnelInstance, int numOnce) throws InterruptedException {
//        // 存在ip池数量不能减小的bug,只能等待时间过期
//        int fixedIpPoolSize = tunnelInstance.getCoreIpSize();
//        if (getValidIpSize(queue) < fixedIpPoolSize) {
//            log.warn("当前ip池数量={}, 实际有效ip数={}, 小于规定的 {} 个, 即将重试", queue.size(), getValidIpSize(queue), fixedIpPoolSize);
//            checkBeforeUpdate(queue, tunnelInstance, numOnce);
//        }
//    }
//
//    /**
//     * 在更新IP池之前, 检查拉取的ip是否已经过期, 是否已经存在于ip池
//     *
//     * @param numOnce 一次拉取ip个数
//     * @param queue   ip循环队列
//     */
//    public void checkBeforeUpdate(ConcurrentLinkedQueue<ProxyIp> queue, TunnelInstance tunnelInstance, int numOnce) throws InterruptedException {
//        // 先检查，从代理商拉取的ip可能马上或者已经过期
//        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
//            List<ProxyIp> data = zhiMaFetchServiceImpl.getIpList(numOnce, tunnelInstance);
//            if (Objects.isNull(data) || data.isEmpty()) {
//                log.error("从代理商获取ip结果为空, 即将重试");
//                continue;
//            }
//            for (ProxyIp newProxyIp : data) {
//                if (isExpired(newProxyIp)) {
//                    log.warn("拉取的ip={} 已过期, 时间={}", newProxyIp.getIpAddr(), newProxyIp.getExpireTime());
//                    continue;
//                }
//                // 还需要检查ip是否在ip池中重复了
//                // 还有一种可能，queue中虽然不包含 newProxyIp，但可能是ip相同，过期时间，或者端口不同的情况。
//                if (queue.contains(newProxyIp)) {
//                    log.warn("拉取的IP={} 在IP池中已存在，即将重试", newProxyIp.getIpAddr());
//                    continue;
//                }
//                // ip池满的就不用添加
//                int validIpSize = getValidIpSize(queue);
//                if (validIpSize >= tunnelInstance.getCoreIpSize()) {
//                    log.warn("IP池已满, 配置数量={}, 有效数量={}, 取消添加", tunnelInstance.getCoreIpSize(), validIpSize);
//                    continue;
//                }
//                queue.offer(newProxyIp);
//            }
//            break;
//        }
//    }
//
//    public boolean addIp(int num, ConcurrentLinkedQueue<ProxyIp> queue) throws InterruptedException {
//        List<ProxyIp> data = zhiMaFetchServiceImpl.getIpList(num, tunnelInitService.getDefaultTunnel());
//        boolean status = false;
//        int count = 0;
//        if (Objects.isNull(data) || data.isEmpty()) {
//            log.error("从代理商获取ip结果为空, 即将重试");
//            return false;
//        }
//        for (ProxyIp newProxyIp : data) {
//            if (isExpired(newProxyIp)) {
//                log.warn("拉取的ip={} 已过期, 时间={}", newProxyIp.getIpAddr(), newProxyIp.getExpireTime());
//                continue;
//            }
//            // 还需要检查ip是否在ip池中重复了
//            if (queue.contains(newProxyIp)) {
//                log.warn("拉取的IP={} 在IP池中已存在，即将重试", newProxyIp.getIpAddr());
//                continue;
//            }
//            queue.offer(newProxyIp);
//            count++;
//        }
//        if (count == data.size()) {
//            status = true;
//        }
//        return status;
//    }
//
//    /**
//     * 随机1个移除ip
//     *
//     * @param queue
//     */
//    public boolean removeIp(int num, ConcurrentLinkedQueue<ProxyIp> queue) {
//        if (queue == null || num <= 0) {
//            return false;
//        }
//        // 随机移除ip
//        // 优化点: 此处剔除成功率最低的一个ip
//        for (int i = 0; i < num; i++) {
//            queue.poll();
//        }
//        return true;
//    }
//
//    /**
//     * 检查是否过期
//     *
//     * @param proxyIp
//     * @return
//     */
//    public boolean isExpired(ProxyIp proxyIp) {
//        if (proxyIp == null) {
//            log.error("proxyCfg is null");
//            return true;
//        }
//        LocalDateTime expireTime = proxyIp.getExpireTime();
//        //获取秒数, 提前过期
//        long instanceSecond = expireTime.toEpochSecond(ZoneOffset.of("+8"));
//        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
//        long duration = instanceSecond - nowSecond;
//        return duration < proxyServerConfig.getJudgeExpiredIpMinSeconds();
//    }
//
//    /**
//     * 获取ip池有效ip数量
//     *
//     * @param queue ip池
//     * @return
//     */
//    private int getValidIpSize(ConcurrentLinkedQueue<ProxyIp> queue) {
//        return (int) queue.stream()
//                .filter(proxyIp -> proxyIp.getValid().get())
//                .distinct().count();
//    }
//
//    /**
//     * 获取每个格子（最小检查单位时间）可用的ip数（动态的）
//     *
//     * @param logger              日志
//     * @param period              检查时间间隔
//     * @param unit                时间单位
//     * @param ipLimitSizeEveryDay 每日拉取ip数限制
//     * @param usedIpSize          已经使用的ip数 (唯一在变化的参数)
//     * @return
//     */
//    public int getAvailableIpPerUnitTime(Logger logger, long period, TimeUnit unit, int ipLimitSizeEveryDay, int usedIpSize) {
//        if (period <= 0 || unit == null || ipLimitSizeEveryDay <= 0 || usedIpSize <= 0) {
//            logger.error("params check error, period={}, ipLimitSizeEveryDay={}", period, ipLimitSizeEveryDay);
//            return 0;
//        }
//        // 当日已经过去的分钟数
//        int passedMin = TimeUtils.toPassedMin();
//        // 全天分钟数
//        int oneDayMin = 24 * 60;
//        // 一天剩余分钟数
//        int surplusMinEachDay = oneDayMin - passedMin;
//        // 检查时间间隔
//        long check = unit.toSeconds(period);
//        // 剩余格子数
////        long remainingGrids = surplusMinEachDay / check;
//        int remainingGrids = new BigDecimal(surplusMinEachDay).divide(new BigDecimal(check), 2, RoundingMode.HALF_UP).intValue();
//        // 剩余可用ip数
//        int surplusIpSize = ipLimitSizeEveryDay - usedIpSize;
//        // 每个格子拥有的ip数
////        long ipPerGrids =  surplusIpSize / remainingGrids;
//        int ipPerGrids = new BigDecimal(surplusIpSize).divide(new BigDecimal(remainingGrids), 2, RoundingMode.HALF_UP).intValue();
//        logger.info("每日拉取ip数限制={}, 剩余可用ip数={}, 每个格子的可用ip数={}", ipLimitSizeEveryDay, surplusIpSize, ipPerGrids);
//        return ipPerGrids;
//    }
//
//    public int getCoreIpSize(int availableIp) {
////        return availableIp / 2;
//        return new BigDecimal(availableIp).divide(new BigDecimal(2), 2, RoundingMode.HALF_UP).intValue();
//    }
//
//    public int getCoreIpSize(Logger logger, TunnelInstance tunnelInstance, int ipLimitSizeEveryDay, int usedIpSize) {
//        int availableIp = getAvailableIpPerUnitTime(logger, ReqMonitorUtils.CHECK_INTERVAL, ReqMonitorUtils.CHECK_TIME_UNIT, ipLimitSizeEveryDay, usedIpSize);
//        return new BigDecimal(availableIp).divide(new BigDecimal(2), 2, RoundingMode.HALF_UP).intValue();
//    }
//
//    /**
//     * 定时更新ip池
//     */
//    class ScheduleUpdateTask implements Runnable {
//
//        @Override
//        public void run() {
//            try {
//                checkAndUpdateIp();
//                proxyIpPool.forEach((tunnel, queue) -> {
//                    List<String> collect = queue.stream()
//                            .map(ProxyIp::getIpAddrWithTimeAndValid)
//                            .distinct()
//                            .collect(Collectors.toList());
//                    int validIpSize = getValidIpSize(queue);
//                    log.info("tunnel={}, ip-pool-size={}, valid-ip-size={}, ip-pool-list={}", tunnel, queue.size(), validIpSize, JSON.toJSONString(collect));
//                });
//            } catch (Throwable e) {
//                log.error("定时更新ip池出现异常，原因：{}", e.getCause().getMessage());
//            }
//        }
//    }
//
//}
