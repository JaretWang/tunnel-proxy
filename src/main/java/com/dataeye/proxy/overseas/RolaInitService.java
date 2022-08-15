package com.dataeye.proxy.overseas;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.enums.TunnelType;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.RolaConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.overseas.impl.DynamicHomeIpServiceImpl;
import com.dataeye.proxy.overseas.impl.DynamicMachineRoomIpServiceImpl;
import com.dataeye.proxy.overseas.impl.StaticMachineRoomIpServiceImpl;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.RolaFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.ReqMonitorUtils;
import com.dataeye.proxy.utils.TimeUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/8/12 17:31
 * @description
 */
@Service
public class RolaInitService extends RolaProxyFetchService {

    public static final Logger LOGGER = MyLogbackRollingFileUtil.getLogger("RolaInitService");

    @Autowired
    RolaConfig rolaConfig;
    @Autowired
    DynamicHomeIpServiceImpl dhi;
    @Autowired
    DynamicMachineRoomIpServiceImpl dmri;
    @Autowired
    StaticMachineRoomIpServiceImpl smri;
    @Autowired
    TunnelInitService tunnelInitService;
    @Autowired
    RolaFetchServiceImpl rolaFetchServiceImpl;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("ip-pool-schedule-"), new ThreadPoolExecutor.AbortPolicy());

    public void init() throws IOException {
        LOGGER.info("海外隧道 - 初始化国家代号");
        initCountryCode();
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        LOGGER.info("海外隧道 - 添加白名单");
        dhi.addIpWhiteList(rolaConfig, defaultTunnel.getOutIp(), "海外隧道-" + defaultTunnel.getAlias());
        LOGGER.info("海外隧道 - 初始化动态住宅ip池");
        dhi.initIpPool();
        LOGGER.info("海外隧道 - 初始化动态机房ip池");
        dmri.initIpPool();
        LOGGER.info("海外隧道 - 初始化静态态机房ip池");
        smri.initIpPool();
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(this::checkAndUpdateIpPool, 0, 3, TimeUnit.SECONDS);
    }

    void initCountryCode() throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("rola/country_code.json");
        assert inputStream != null;
        String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (Objects.nonNull(jsonObject) && !jsonObject.isEmpty()) {
            RolaProxyFetchService.COUNTRY_CODE = jsonObject;
        }
        LOGGER.info("init rola country code={}", RolaProxyFetchService.COUNTRY_CODE.toJSONString());
    }

    /**
     * 检查更新代理IP池
     */
    public void checkAndUpdateIpPool() {
        try {
            dhi.initIpPool();
            dmri.initIpPool();
            smri.initIpPool();
            printIpPool();
        } catch (Throwable e) {
            LOGGER.error("定时更新ip池出现异常", e);
        }
    }

    void printIpPool() {
        proxyIpPool.forEach((tunnel, queue) -> {
            List<String> collect = queue.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
            int validIpSize = getValidIpSize(queue);
            LOGGER.info("tunnel={}, ip-pool-size={}, valid-ip-size={}, ip-pool-list={}", tunnel, queue.size(), validIpSize, JSON.toJSONString(collect));
        });
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
            LOGGER.warn("id存在，但是ip循环队列为空");
            initQueue(tunnelInstance);
            return;
        }
        // 逐个检查ip的过期时间
        for (ProxyIp ip : queue) {
            if (isExpired(ip)) {
                ip.getValid().set(false);
                LOGGER.info("ip={} 即将过期或已经过期，移除", ip.getIpAddrWithTime());
                // 放一个新的ip进去
                addFixedIp("ipCheck: ip过期追加", queue, tunnelInstance, 1, false);
            }
        }
        // 如果queue中的有效ip数低于最小阈值,则需要继续添加
        int validIpSize = getValidIpSize(queue);
        int coreIpSize = getCoreIpSize(LOGGER, tunnelInstance);
        if (validIpSize < coreIpSize) {
            LOGGER.warn("ip池数量低于最小阈值, 补充ip, validIpSize={}, coreIpSize={}", validIpSize, coreIpSize);
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
        int availableIpPerUnitTime = getAvailableIpPerUnitTime(LOGGER, tunnelInstance);
        int fetchIpPerUnit = ReqMonitorUtils.FETCH_IP_NUM_PER_UNIT.get();
        if (fetchIpPerUnit >= availableIpPerUnitTime) {
            LOGGER.warn("添加失败, 添加原因={}, 单位时间内拉取的ip数 {} 达到阈值 {}, 放弃添加ip", addReason, fetchIpPerUnit, availableIpPerUnitTime);
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
            List<ProxyIp> data = rolaFetchServiceImpl.getIpList(needIpSize, tunnelInstance, init);
            if (Objects.isNull(data) || data.isEmpty()) {
                LOGGER.error("从代理商获取ip结果为空, 即将重试");
                empty++;
                continue;
            }
            for (ProxyIp newProxyIp : data) {
                if (isExpired(newProxyIp)) {
                    LOGGER.warn("拉取的ip={} 已过期, 时间={}", newProxyIp.getIpAddr(), newProxyIp.getExpireTime());
                    expired++;
                    continue;
                }
                // 还需要检查ip是否在ip池中重复了
                // 还有一种可能，queue中虽然不包含 newProxyIp，但可能是ip相同，过期时间，或者端口不同的情况。(暂不处理)
                if (queue.contains(newProxyIp)) {
                    LOGGER.warn("拉取的IP={} 在IP池中已存在，即将重试", newProxyIp.getIpAddr());
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
        LOGGER.warn("添加成功, 添加原因={}, enough={}, needIpSize={}, realCount={}, expired={}, exist={}, empty={}",
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
            LOGGER.error("ip pool init error, tunnelInstance is null");
            return;
        }
        String tunnel = tunnelInstance.getAlias();
        if (StringUtils.isBlank(tunnel)) {
            LOGGER.error("tunnel alias is empty");
            return;
        }
        int coreIpSize = getCoreIpSize(LOGGER, tunnelInstance);
        if (coreIpSize <= 0) {
            LOGGER.error("coreIpSize <= 0, quit");
            return;
        }
        ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
        LOGGER.warn("初始化隧道 {} 的ip池, 核心ip数={}", tunnelInstance.getAlias(), coreIpSize);
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
            LOGGER.error("移除失败, queue is null or num <= 0");
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
            LOGGER.error("proxyIp is null");
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
    int getValidIpSize(ConcurrentLinkedQueue<ProxyIp> queue) {
        if (queue == null) {
            return 0;
        }
        return (int) queue.stream()
                .filter(proxyIp -> proxyIp.getValid().get())
                .distinct().count();
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
            LOGGER.error("tunnelInstance is null");
            return 0;
        }
        LOGGER.debug("隧道配置={}", JSON.toJSONString(tunnelInstance));
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
        long check = unit.toMinutes(period);

        // 剩余格子数
        long remainingGrids = surplusMinEachDay / check;
        int ipPerGrids = (int) (availableIp / remainingGrids);
        LOGGER.debug("每日ip数限制={}, 已拉取ip数={}, 剩余可用ip数={}, 每个格子分配的ip数={}", maxFetchIpNumEveryDay, tunnelInstance.getUsedIp(), availableIp, ipPerGrids);
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

    @Override
    public void initIpPool() {

    }
}
