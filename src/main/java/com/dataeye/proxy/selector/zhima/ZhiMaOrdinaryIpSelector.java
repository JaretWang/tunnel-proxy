package com.dataeye.proxy.selector.zhima;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import com.dataeye.proxy.utils.TimeUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 芝麻ip 普通套餐2: 单日定制套餐-(22-04-07 09:39~23-02-21 09:39)
 *
 * @author jaret
 * @date 2022/8/17 23:01
 * @description
 */
@Slf4j
@Data
@Component
public class ZhiMaOrdinaryIpSelector implements CommonIpSelector {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = new ConcurrentHashMap<>();
    private final int minIpPoolSize = 5;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchService;
    @Autowired
    IpMonitorUtils ipMonitorUtils;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchServiceImpl;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitService tunnelInitService;

    @Override
    public ProxyIp getOne() {
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("get ip error, tunnelInstance is null");
            return null;
        }
        ConcurrentLinkedQueue<ProxyIp> proxyCfgsQueue = proxyIpPool.get(tunnelInstance.getAlias());
        if (Objects.isNull(proxyCfgsQueue)) {
            log.error("queue is not exist");
            return null;
        }
        ProxyIp poll = proxyCfgsQueue.poll();
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
        proxyCfgsQueue.offer(poll);
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
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("tunnelInstance is null");
            return null;
        }
        return proxyIpPool.get(tunnelInstance.getAlias());
    }

    @Override
    public void init() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.ZHIMA)) {
            return;
        }
        zhiMaFetchServiceImpl.updateSurplusIpSize();
        ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
                new ThreadPoolConfig.TunnelThreadFactory("ZhiMaOrdinaryIpSelector-"), new ThreadPoolExecutor.AbortPolicy());
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(this::checkAndUpdateIpPool, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * 检查更新代理IP池
     */
    public void checkAndUpdateIpPool() {
        for (TunnelInstance tunnelInstance : tunnelInitService.getTunnelList()) {
            try {
                ipCheck(tunnelInstance);
                forceAddIp(tunnelInstance);
                handleInvalidIp(log, tunnelInstance, proxyIpPool);
                printIpPool(log, proxyIpPool);
            } catch (Throwable e) {
                log.error("定时更新ip池出现异常", e);
            }
        }
    }

    /**
     * 强制追加ip，但是仍然符合ip最大拉取限制规则
     */
    public void forceAddIp(TunnelInstance tunnelInstance) throws InterruptedException {
        int forceAddIp = tunnelInstance.getForceAddIp();
        if (forceAddIp == 0) {
            return;
        }
        int forceKeepIpPoolSize = tunnelInstance.getForceKeepIpPoolSize();
        ConcurrentLinkedQueue<ProxyIp> queue = proxyIpPool.get(tunnelInstance.getAlias());
        int validIpSize = getValidIpSize(queue);
        if (validIpSize <= forceKeepIpPoolSize) {
            int needIp = forceKeepIpPoolSize - validIpSize;
            addFixedIp("强制追加ip", queue, tunnelInstance, needIp, false);
        }
    }

    /**
     * ip有效时间检查
     *
     * @param tunnelInstance 隧道实例
     * @throws InterruptedException
     */
    public void ipCheck(TunnelInstance tunnelInstance) throws InterruptedException {
        String tunnel = tunnelInstance.getAlias();
        // 首次需要初始化
        if (!proxyIpPool.containsKey(tunnel)) {
            initQueue(tunnelInstance);
            return;
        }
        ConcurrentLinkedQueue<ProxyIp> queue = proxyIpPool.get(tunnel);
        if (queue == null || queue.isEmpty()) {
            // 某个隧道对应的ip池中的所有ip同时失效，但可能性低
            log.warn("id存在，但是ip循环队列为空");
            initQueue(tunnelInstance);
            return;
        }
        // 逐个检查ip的过期时间
        for (ProxyIp ip : queue) {
            if (isExpired(ip)) {
                ip.getValid().set(false);
                log.info("ip={} 即将过期或已经过期，移除", ip.getIpAddrWithTime());
                // 放一个新的ip进去
                addFixedIp("ipCheck: ip过期追加", queue, tunnelInstance, 1, false);
            }
        }
        // 如果queue中的有效ip数低于最小阈值,则需要继续添加
        int validIpSize = getValidIpSize(queue);
        int coreIpSize = getCoreIpSize(log, tunnelInstance);
        if (validIpSize < coreIpSize) {
            log.warn("ip池数量低于最小阈值, 补充ip, validIpSize={}, coreIpSize={}", validIpSize, coreIpSize);
            int size = coreIpSize - validIpSize;
            addFixedIp("ipCheck: ip池数量低于最小阈值", queue, tunnelInstance, size, false);
        }
    }

    /**
     * 判断是否允许添加ip
     *
     * @return
     */
    public boolean isAllowAddIp(String addReason, TunnelInstance tunnelInstance) {
        // 检查ip拉取是否已经超过单位时间内的最大值
        int availableIpPerUnitTime = getAvailableIpPerUnitTime(log, tunnelInstance);
        int fetchIpPerUnit = ReqMonitorUtils.FETCH_IP_NUM_PER_UNIT.get();
        if (fetchIpPerUnit >= availableIpPerUnitTime) {
            log.warn("添加失败, 添加原因={}, 单位时间内拉取的ip数 {} 达到阈值 {}, 放弃添加ip", addReason, fetchIpPerUnit, availableIpPerUnitTime);
            return false;
        }
        return true;
    }

    /**
     * 添加固定数量的ip
     *
     * @param queue          IP池
     * @param tunnelInstance 隧道实例
     * @param needIpSize     需要的ip数
     * @throws InterruptedException
     */
    public boolean addFixedIp(String addReason, ConcurrentLinkedQueue<ProxyIp> queue,
                              TunnelInstance tunnelInstance,
                              int needIpSize, boolean init) throws InterruptedException {
        // 检查ip拉取是否已经超过单位时间内的最大值
        if (!isAllowAddIp(addReason, tunnelInstance)) {
            return false;
        }
        boolean status = false;
        // 先检查，从代理商拉取的ip可能马上或者已经过期
        int realCount = 0, expired = 0, exist = 0, empty = 0;
        for (int i = 0; i < proxyServerConfig.getExpiredIpRetryCount(); i++) {
            List<ProxyIp> data = zhiMaFetchServiceImpl.getIpList(needIpSize, tunnelInstance, init);
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
                realCount++;
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

    public boolean addIp(TunnelInstance tunnelInstance, int needSize) throws InterruptedException {
        String alias = tunnelInstance.getAlias();
        ConcurrentLinkedQueue<ProxyIp> queue = proxyIpPool.get(alias);
        return addFixedIp("addIp", queue, tunnelInstance, needSize, false);
    }

    /**
     * 首次初始化ip池
     * bug fixed: 增加初始化加载标志位,防止被多个netty线程并发调用
     *
     * @param tunnelInstance
     * @throws InterruptedException
     */
    public void initQueue(TunnelInstance tunnelInstance) throws InterruptedException {
        if (tunnelInstance == null) {
            log.error("ip pool init error, tunnelInstance is null");
            return;
        }
        String tunnel = tunnelInstance.getAlias();
        if (StringUtils.isBlank(tunnel)) {
            log.error("tunnel alias is empty");
            return;
        }
        int coreIpSize = getCoreIpSize(log, tunnelInstance);
        if (coreIpSize <= 0) {
            log.error("coreIpSize <= 0, quit");
            return;
        }
        ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
        log.warn("初始化隧道 {} 的ip池, 核心ip数={}", tunnelInstance.getAlias(), coreIpSize);
        addFixedIp("initQueue: 初始化添加", queue, tunnelInstance, coreIpSize, true);
        proxyIpPool.put(tunnel, queue);
    }

    /**
     * 移除指定数量的ip
     *
     * @param queue
     */
    public boolean removeFixedIp(int num, ConcurrentLinkedQueue<ProxyIp> queue) {
        if (queue == null || num <= 0) {
            log.error("移除失败, queue is null or num <= 0");
            return false;
        }
        // ip池低于核心值的时候，不能移除，否则会变成0
        // 随机移除ip
        // TODO 优化点: 此处剔除成功率最低的一个ip 等加上ip池优先级队列就可以了
        for (int i = 0; i < num; i++) {
            queue.poll();
//            ipMonitorUtils.removeHighErrorPercent();
        }
        return true;
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
        return duration < proxyServerConfig.getJudgeExpiredIpMinSeconds();
    }

    /**
     * 获取每个格子（最小检查单位时间）可用的ip数（动态的）
     *
     * @param logger  日志
     * @param period2 检查时间间隔
     * @param unit2   时间单位
     * @return
     */
    public int getAvailableIpPerUnitTime(Logger logger, long period2, TimeUnit unit2, TunnelInstance tunnelInstance2) {
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("tunnelInstance is null");
            return 0;
        }
        log.debug("隧道配置={}", JSON.toJSONString(tunnelInstance));
        long period = ReqMonitorUtils.CHECK_INTERVAL;
        TimeUnit unit = ReqMonitorUtils.CHECK_TIME_UNIT;
        int maxFetchIpNumEveryDay = tunnelInstance.getMaxFetchIpNumEveryDay();
        int availableIp = tunnelInstance.getAvailableIp();
        if (availableIp <= 0 || maxFetchIpNumEveryDay <= 0) {
            logger.error("params check error, period={}, maxFetchIpNumEveryDay={}, availableIp={}", period, maxFetchIpNumEveryDay, availableIp);
            return 0;
        }
        // 当日已经过去的分钟数
        int passedMin = TimeUtils.toPassedMin();
        // 全天分钟数
        int oneDayMin = 24 * 60;
        // 一天剩余分钟数
        int surplusMinEachDay = oneDayMin - passedMin;
//        if (surplusMinEachDay > availableIp) {
//            log.error("可用ip数小于一天剩余分钟数,表示每个格子(最小时间一分钟)的可用ip只有0和1");
//            // 不写成1的话 就会返回0
//            return 0;
//        }
        // 检查时间间隔
        long check = unit.toMinutes(period);

        // 剩余格子数
        long remainingGrids = surplusMinEachDay / check;
//        int remainingGrids = new BigDecimal(surplusMinEachDay).divide(new BigDecimal(check), 2, RoundingMode.HALF_UP).intValue();

//        // 剩余可用ip数不够
//        if (remainingGrids > availableIp) {
//            log.error("剩余ip数小于格子数,表示每个格子的可用ip只有0和1");
//            // 不写成1的话 就会返回0
//            return 0;
//        }

        // 每个格子拥有的ip数
        int ipPerGrids = (int) (availableIp / remainingGrids);
//        int ipPerGrids = new BigDecimal(surplusIpSize).divide(new BigDecimal(remainingGrids), 2, RoundingMode.HALF_UP).intValue();
        log.debug("每日ip数限制={}, 已拉取ip数={}, 剩余可用ip数={}, 每个格子分配的ip数={}", maxFetchIpNumEveryDay, tunnelInstance.getUsedIp(), availableIp, ipPerGrids);
        return ipPerGrids;
    }

    public int getAvailableIpPerUnitTime(Logger logger, TunnelInstance tunnelInstance) {
        return getAvailableIpPerUnitTime(logger, ReqMonitorUtils.CHECK_INTERVAL, ReqMonitorUtils.CHECK_TIME_UNIT, tunnelInstance);
    }

    public int getCoreIpSize(Logger logger, TunnelInstance tunnelInstance) {
        return getCoreIpSize(logger, tunnelInstance, ReqMonitorUtils.CHECK_INTERVAL, ReqMonitorUtils.CHECK_TIME_UNIT);
    }

    public int getCoreIpSize(TunnelInstance tunnelInstance, int availableIp) {
        if (availableIp <= 0) {
            return 0;
        }
        int coreIpSize;
        // 正数1 -> 使用自动计算核心ip数
        if (tunnelInstance.getAutoGetCoreIpSize() > 0) {
            // 取20%的ip作为阈值
            coreIpSize = new BigDecimal(availableIp).divide(new BigDecimal(2), 2, RoundingMode.HALF_UP).intValue();
        } else {
            // 否则使用数据库中手动配置的ip数,一般为2
            coreIpSize = tunnelInstance.getCoreIpSize();
        }
        // 防止ip池一个都没有
        if (coreIpSize <= 0) {
            coreIpSize = 1;
        }
        return coreIpSize;
    }

    /**
     * 获取ip池核心数(动态)
     *
     * @param logger 日志
     * @return
     */
    public int getCoreIpSize(Logger logger, TunnelInstance tunnelInstance, long period, TimeUnit unit) {
        int availableIp = getAvailableIpPerUnitTime(logger, period, unit, tunnelInstance);
        return getCoreIpSize(tunnelInstance, availableIp);
    }

}
