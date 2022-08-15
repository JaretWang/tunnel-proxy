package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ZhiMaConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直接从芝麻代理官网拉取ip
 *
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 芝麻ip获取
 */
@Service
public class ZhiMaFetchServiceImpl implements ProxyFetchService {

    /**
     * 当前已经拉取的ip数量
     */
    private static final AtomicInteger FETCH_IP_NUM_NOW = new AtomicInteger(0);
    private static final AtomicInteger SURPLUS_IP_SIZE = new AtomicInteger(0);
    private static final AtomicInteger ALARM_LEVEL = new AtomicInteger(0);
    private static final AtomicBoolean IS_SEND_ALARM_EMAIL = new AtomicBoolean(false);
    private static final AtomicBoolean FIRST_SEND = new AtomicBoolean(true);
    private static final AtomicBoolean RESET_GET_USED_IP_STATUS = new AtomicBoolean(false);
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ZhiMaFetchServiceImpl");
    /**
     * 是否开启降低请求成功率
     */
    private static final boolean ENABLE_REDUCE_REQ_SUCCESS_PERCENT = false;
    @Resource
    ZhiMaConfig zhiMaConfig;
    @Autowired
    IpSelector ipSelector;
    @Autowired
    SendMailService sendMailService;
    @Resource
    TunnelInitService tunnelInitService;

    public int getFetchIp() {
        return FETCH_IP_NUM_NOW.get();
    }

    public int getSurplusIp() {
        return SURPLUS_IP_SIZE.get();
    }

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws InterruptedException {
        return getIpList(1, tunnelInstance, false).get(0);
    }

    public List<ProxyIp> getIpList(int num, TunnelInstance tunnelInstance, boolean init) throws InterruptedException {
        if (isOverFetchIpNumLimit(tunnelInstance, init)) {
            logger.error("已达到每日最大拉取ip数量 {} !!!", tunnelInstance.getMaxFetchIpNumEveryDay());
            return Collections.emptyList();
        }

        String url = zhiMaConfig.getDirectGetUrl() + "&num=" + num;
        String json = OkHttpTool.doGet(url, Collections.emptyMap(), false);
        if (StringUtils.isBlank(json)) {
            logger.error("芝麻代理拉取ip为空");
            return Collections.emptyList();
        }

        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("success");
        if (!success) {
            logger.error("从芝麻代理拉取ip失败，原因：{}", json);
            // 处理限流
            int code = jsonObject.getIntValue("code");
            if (code == 111) {
                logger.error("被芝麻限流, 重试");
                Thread.sleep(2000L);
                return getIpList(num, tunnelInstance, init);
            }
            return Collections.emptyList();
        }

        JSONArray data = jsonObject.getJSONArray("data");
        if (Objects.isNull(data) || data.size() <= 0) {
            logger.error("拉取代理ip列表为空，原因：{}", json);
            return Collections.emptyList();
        }

        List<ProxyIp> result = new LinkedList<>();
        logger.info("本次拉取ip, 需要数量={}, 实际拉取数量={}", num, data.size());
        for (Object datum : data) {
            JSONObject ipElement = JSONObject.parseObject(datum.toString());
            String ip = ipElement.getString("ip");
            int port = ipElement.getIntValue("port");
            String expireTime = ipElement.getString("expire_time");
            LocalDateTime time = LocalDateTime.parse(expireTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // 计数
            FETCH_IP_NUM_NOW.incrementAndGet();
            ReqMonitorUtils.FETCH_IP_NUM_PER_UNIT.incrementAndGet();
            ProxyIp proxyIp = ProxyIp.builder()
                    .host(ip)
                    .port(port)
                    .expireTime(time)
                    .valid(new AtomicBoolean(true))
                    .userName("")
                    .password("")
                    .build();
            result.add(proxyIp);
        }
        return result;
    }

    /**
     * 检查每日拉取ip数量限制
     *
     * @return 是否超过限制
     */
    public boolean isOverFetchIpNumLimit(TunnelInstance tunnelInstance, boolean init) {
        if (init) {
            return false;
        }
        int alarmLevel = getAlarmLevel(ENABLE_REDUCE_REQ_SUCCESS_PERCENT, tunnelInstance);
        if (alarmLevel > 0) {
            ALARM_LEVEL.set(alarmLevel);
            IS_SEND_ALARM_EMAIL.set(true);
            if (FIRST_SEND.get()) {
                sendAlarmEmail();
                FIRST_SEND.set(false);
            }
            logger.info("告警等级大于0: IS_SEND_ALARM_EMAIL={}, ALARM_LEVEL={}", IS_SEND_ALARM_EMAIL.get(), ALARM_LEVEL.get());
        }
        return tunnelInstance.getAvailableIp() <= 0;
    }

    /**
     * 发送告警邮件
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    void sendAlarmEmail() {
        if (!IS_SEND_ALARM_EMAIL.get()) {
            return;
        }
        List<TunnelInstance> tunnelList = tunnelInitService.getTunnelList();
        if (CollectionUtils.isEmpty(tunnelList)) {
            return;
        }
        try {
            TunnelInstance tunnelInstance = tunnelList.get(0);
            String subject = "隧道 " + tunnelInstance.getAlias() + " IP拉取数量告警";
            String alarmContent = getAlarmContent(tunnelInstance);
            logger.warn("告警邮件: subject={}, content={}", subject, alarmContent);
            if (tunnelInstance.getSendAlarmEmail() == 0) {
                return;
            }
            sendMailService.sendMail(subject, alarmContent);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 重置
            IS_SEND_ALARM_EMAIL.compareAndSet(true, false);
            ALARM_LEVEL.set(0);
            logger.info("重置发送状态: IS_SEND_ALARM_EMAIL={}, ALARM_LEVEL={}", IS_SEND_ALARM_EMAIL.get(), ALARM_LEVEL.get());
        }
    }

    String getAlarmContent(TunnelInstance tunnelInstance) {
        String content = new StringJoiner(", ")
                .add("隧道=" + tunnelInstance.getAlias())
                .add("告警等级=" + ALARM_LEVEL.get() + "级")
                .add("已拉取ip数=" + tunnelInstance.getUsedIp())
                .add("每日最大拉取ip数=" + tunnelInstance.getMaxFetchIpNumEveryDay()).toString();
        String alarmRule = new StringJoiner(System.lineSeparator())
                .add("告警规则:")
                .add("最少需要ip数 = (每日剩余分钟数/5min) * ip池的大小(ps: 套餐内ip的有效时间都是1-5min) + 安全保障ip数(500) (当日不使用劣质ip剔除机制后需要的ip数)")
                .add("最大容错ip数 = 总限制数 - 当日不使用劣质ip剔除机制后需要的最少ip数 (最大容错ip数是为了保证套餐内一天下来肯定会有ip可用)")
                .add("TPS = 每秒处理的连接数")
                .add("ip每秒被使用次数 = TPS/ip池大小")
                .add("ip移除前最少使用次数 = (ip每秒被使用次数 * ip质量检测时间间隔) * 5(使用轮数)")
                .add("ip使用数达到总限制数70%: 1级告警 (主动降低移除ip的最低成功率为原来的75%, 且调大ip移除前最少使用次数为原来的25%)")
                .add("ip使用数达到总限制数80%: 2级告警 (主动降低移除ip的最低成功率为原来的50%, 且调大ip移除前最少使用次数为原来的50%)")
                .add("ip使用数达到总限制数90%: 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除机制, 只根据ip是否过期来剔除)")
                .add("ip使用数达到最大容错ip数: 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除规则, 只根据ip是否过期来剔除)")
                .add("套餐剩余ip总数小于所有隧道的当日最少需要ip数之和 -> 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除规则, 只根据ip是否过期来剔除)")
                .toString();
//        return content + System.lineSeparator() + System.lineSeparator() + alarmRule;
        return content;
    }

    /**
     * 每晚12点，重置所有状态
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void reSetFetchIpNum() {
        logger.info("芝麻代理 - 重置所有状态");
        FETCH_IP_NUM_NOW.set(0);
        IS_SEND_ALARM_EMAIL.set(false);
        ALARM_LEVEL.set(0);
        SURPLUS_IP_SIZE.set(0);
        FIRST_SEND.set(true);
        RESET_GET_USED_IP_STATUS.set(true);
        ReqMonitorUtils.FETCH_IP_NUM_PER_UNIT.set(0);
//        tunnelInitService.updateUsedIp(tunnelInitService.getDefaultTunnel().getAlias(), 0);
    }

    /**
     * 每隔30分钟, 打印一次当日累计拉取ip数量, 套餐每日剩余ip数量
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    void getIpFetchNumNow() {
        logger.info("芝麻代理 - 今日累计拉取IP数量={}", FETCH_IP_NUM_NOW.get());
        logger.info("套餐每日剩余ip数量={}", SURPLUS_IP_SIZE.get());
    }

    /**
     * 更新套餐ip剩余数量和使用量
     */
    @Scheduled(cron = "0/5 * * * * ?")
    void updateSurplusIpSize() {
        int surplusIpSize = getSurplusIpSize();
        // 避免因为网络不好，导致获取的剩余ip数为0，重试3次
        if (surplusIpSize == 0) {
            int count = 0;
            while (count < 3) {
                count++;
                try {
                    Thread.sleep(2000L);
                    // 重试
                    surplusIpSize = getSurplusIpSize();
                    if (surplusIpSize > 0) {
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        SURPLUS_IP_SIZE.set(surplusIpSize);
        int usedIp = FETCH_IP_NUM_NOW.get();
        try {
            TunnelInstance tunnel = tunnelInitService.getDefaultTunnel();
            if (tunnel == null) {
                return;
            }
//            //更新数据库ip已经拉取的数量
//            int usedIp;
//            // 防止昨天的累计ip数会算到第二天的累计ip数上
//            if (RESET_GET_USED_IP_STATUS.get()) {
//                usedIp = FETCH_IP_NUM_NOW.get();
//                RESET_GET_USED_IP_STATUS.set(false);
//            } else {
//                usedIp = tunnel.getUsedIp() + FETCH_IP_NUM_NOW.get();
//            }
//            tunnelInitService.updateUsedIp(tunnel.getAlias(), usedIp);
            tunnelInitService.updateUsedIp(tunnel.getAlias(), usedIp);
            logger.info("套餐剩余ip数={}, 隧道每日限制={}, 已拉取(自启动程序时)={}, 今日累计拉取={}",
                    SURPLUS_IP_SIZE.get(), tunnel.getMaxFetchIpNumEveryDay(), FETCH_IP_NUM_NOW.get(), usedIp);

            if (surplusIpSize < 20000) {
                String subject = "芝麻套餐ip数余额不足";
                String content = new StringJoiner(", ")
                        .add("隧道=" + tunnel.getAlias())
                        .add("套餐余额=" + surplusIpSize)
                        .add("已拉取=" + usedIp)
                        .add("每日限额=" + tunnel.getMaxFetchIpNumEveryDay()).toString();
                logger.info(subject + ": " + content);
                if (tunnel.getSendAlarmEmail() <= 0) {
                    return;
                }
                sendMailService.sendMail(subject, content);
            }
        } catch (Exception e) {
            logger.error("更新已经拉取的ip数异常", e);
        }
    }

    /**
     * 获取告警等级
     * <p>
     * 最少需要ip数 = (每日剩余分钟数/5min) * ip池的大小(ps: 套餐内ip的有效时间都是1-5min) + 安全保障ip数(500) (当日不使用劣质ip剔除机制后需要的ip数)
     * 最大容错ip数 = 总限制数 - 当日不使用劣质ip剔除机制后需要的最少ip数 (最大容错ip数是为了保证套餐内一天下来肯定会有ip可用)
     * TPS = 每秒处理的连接数
     * ip每秒被使用次数 = TPS/ip池大小
     * ip移除前最少使用次数 = (ip每秒被使用次数 * ip质量检测时间间隔) * 5(使用轮数)
     * <p>
     * ip使用数达到总限制数70% -> 1级告警 (主动降低移除ip的最低成功率为原来的75%, 且调大ip移除前最少使用次数为原来的25%)
     * ip使用数达到总限制数80% -> 2级告警 (主动降低移除ip的最低成功率为原来的50%, 且调大ip移除前最少使用次数为原来的50%)
     * ip使用数达到总限制数90% -> 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除机制, 只根据ip是否过期来剔除)
     * ip使用数达到最大容错ip数 -> 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除规则, 只根据ip是否过期来剔除)
     * 套餐剩余ip总数小于所有隧道的当日最少需要ip数之和 -> 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除规则, 只根据ip是否过期来剔除)
     *
     * @return
     */
    int getAlarmLevel(boolean enableReduceIpQualityCheck, TunnelInstance tunnelInstance) {
        int currentFetchIpSize = tunnelInstance.getUsedIp();
        int coreIpSize = ipSelector.getCoreIpSize(logger, tunnelInstance);
        // 告警等级应该是在总的限制之上做比对,所以不用剩余可用ip数: getAvailableIp()
        int maxIpLimit = tunnelInstance.getMaxFetchIpNumEveryDay();
        if (currentFetchIpSize <= 0 || coreIpSize <= 0 || maxIpLimit <= 0) {
            return 0;
        }
        // 套餐每日剩余ip数
        int surplusIpSize = SURPLUS_IP_SIZE.get();
        // 避免一过12点 套餐剩余量重置为0 就告警，理论上当消耗的ip占比达到70%就会告警，所以不考虑剩余数量为0的情况
        if (surplusIpSize == 0) {
            return 0;
        }
        int minNeedIpSize = getMinNeedIpSize(coreIpSize);
        // 套餐剩余数量小于该隧道当日最少需要的ip数 -> 3级告警
        if (surplusIpSize < minNeedIpSize) {
            logger.warn("套餐剩余ip数小于该隧道当日最少需要的ip数 -> 3级告警, surplusIpSize={}, minNeedIpSize={}", surplusIpSize, minNeedIpSize);
            reduceIpQualityCheckCriteria(enableReduceIpQualityCheck, 3, tunnelInstance);
            return 3;
        }
        //        // 已经拉取的ip数量达到最大容错数 -> 3级告警
        //        int thirdLevel = maxIpLimit - minNeedIpSize;
        //        if (currentFetchIpSize >= thirdLevel) {
        //            logger.warn("已经拉取的ip数量达到最大容错数 -> 3级告警, currentFetchIpSize={}, thirdLevel={}", currentFetchIpSize, thirdLevel);
        //            reduceIpQualityCheckCriteria(3, tunnelInstance);
        //            return 3;
        //        }
        // ip使用数达到总限制数 80%
        double secondLevel = maxIpLimit * 0.8;
        if (currentFetchIpSize >= secondLevel) {
            logger.warn("ip使用数达到总限制数 80% -> 2级告警, currentFetchIpSize={}, secondLevel={}", currentFetchIpSize, secondLevel);
            reduceIpQualityCheckCriteria(enableReduceIpQualityCheck, 2, tunnelInstance);
            return 2;
        }
        // ip使用数达到总限制数 70%
        double firstLevel = maxIpLimit * 0.7;
        if (currentFetchIpSize >= firstLevel) {
            logger.warn("ip使用数达到总限制数 70% -> 1级告警, currentFetchIpSize={}, firstLevel={}", currentFetchIpSize, firstLevel);
            reduceIpQualityCheckCriteria(enableReduceIpQualityCheck, 1, tunnelInstance);
            return 1;
        }
        return 0;
    }

    /**
     * 获取当日最少需要ip数
     *
     * @param ipPoolSize ip池大小
     * @return
     */
    int getMinNeedIpSize(int ipPoolSize) {
        // 每日剩余分钟数 = 24*60 - 当前已过去分钟数
        int surplusMinutes = 24 * 60 - (LocalTime.now().getHour() * 60 + LocalTime.now().getMinute());
        // 该隧道当日最少需要的ip数
        return ((surplusMinutes / 5) + 1) * ipPoolSize + 500;
    }

    /**
     * 降低ip质量判定标准:
     * 1.降低判定为劣质ip的最低成功率
     * 2.调大ip质量检测时的最少使用次数
     *
     * @param alarmLevel     告警等级
     * @param tunnelInstance 隧道实例
     */
    void reduceIpQualityCheckCriteria(boolean enable, int alarmLevel, TunnelInstance tunnelInstance) {
        if (!enable) {
            logger.info("关闭降低ip质量判定标准策略");
            return;
        }
        int minSuccessPercent = tunnelInstance.getMinSuccessPercentForRemoveIp();
        int minUseTimes = tunnelInstance.getMinUseTimesForRemoveIp();
        if (alarmLevel < 1 || alarmLevel > 3) {
            logger.error("未知告警等级, 更新隧道配置失败");
            return;
        }
        // 判定为劣质ip的最低成功率
        int rate = 0;
        // ip检测时最少使用次数
        int useTimes = 0;
        if (alarmLevel == 1) {
            rate = (int) (minSuccessPercent * 0.75);
            useTimes = (int) (minUseTimes * 1.25);
        }
        if (alarmLevel == 2) {
            rate = (int) (minSuccessPercent * 0.5);
            useTimes = (int) (minUseTimes * 1.5);
        }
        if (alarmLevel == 3) {
            useTimes = minUseTimes;
        }
        int count = tunnelInitService.updateSuccessRate(tunnelInstance.getAlias(), rate, useTimes);
        if (count == 0) {
            logger.error("更新隧道配置失败");
        }
    }

    TunnelInstance buildNewTunnelInstance(TunnelInstance ti, int rate, int useTimes) {
        TunnelInstance newTunnel = new TunnelInstance();
        BeanUtils.copyProperties(ti, newTunnel, TunnelInstance.class);
        newTunnel.setMinSuccessPercentForRemoveIp(rate);
        newTunnel.setMinUseTimesForRemoveIp(useTimes);
        return newTunnel;
    }

    /**
     * 获取套餐剩余IP数
     *
     * @return
     */
    public int getSurplusIpSize() {
        // 获取剩余数量
        String url = zhiMaConfig.getGetRemainIpNumUrl();
        String json = OkHttpTool.doGet(url, Collections.emptyMap(), false);
        if (StringUtils.isBlank(json)) {
            logger.error("获取套餐每日剩余ip数量失败, http response is empty");
            return 0;
        }
        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("success");
        if (!success) {
            logger.error("获取套餐每日剩余ip数量失败, 原因: {}", json);
            return 0;
        }
        JSONObject data = jsonObject.getJSONObject("data");
        if (data.isEmpty()) {
            logger.error("获取套餐每日剩余ip数量失败, data is null");
            return 0;
        }
        return data.getIntValue("package_balance");
    }

}
