package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.config.ZhiMaConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直接从芝麻代理官网拉取ip，有效时间：25min-3h
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
    private static final AtomicBoolean IS_SEND_ALARM_EMAIL = new AtomicBoolean(false);
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ZhiMaFetchServiceImpl");
    @Resource
    ZhiMaConfig zhiMaConfig;
    @Autowired
    SendMailService sendMailService;

    @Override
    public ProxyCfg getOne() {
        if (isOverFetchIpNumLimit()) {
            logger.error("已达到每日最大拉取ip数量 {} !!!", MAX_FETCH_IP_NUM_EVERY_DAY.get());
            return null;
        }

        String url = zhiMaConfig.getDirectGetUrl();
//        String url = zhiMaConfig.getExclusiveGetUrl();
//        String url = zhiMaConfig.getTunnelGetUrl();

        String json = OkHttpTool.doGet(url, Collections.emptyMap(), false);
        if (StringUtils.isBlank(json)) {
            logger.error("芝麻代理拉取ip为空");
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("success");
        if (success) {
            JSONArray data = jsonObject.getJSONArray("data");
            if (Objects.nonNull(data) && data.size() > 0) {
                String ipItem = data.get(0).toString();
                JSONObject ipElement = JSONObject.parseObject(ipItem);
                String ip = ipElement.getString("ip");
                int port = ipElement.getIntValue("port");
                String expireTime = ipElement.getString("expire_time");
                LocalDateTime parse = LocalDateTime.parse(expireTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                // 计数
                FETCH_IP_NUM_NOW.incrementAndGet();
                return ProxyCfg.builder()
                        .host(ip)
                        .port(port)
                        .expireTime(parse)
                        .userName("")
                        .password("")
                        .build();
            } else {
                logger.error("代理ip列表为空，原因：{}", json);
            }
        } else {
            logger.error("从芝麻代理拉取ip失败，原因：{}", json);
        }
        return null;
    }

    /**
     * 获取多个ip
     */
    public List<ProxyCfg> getMany(int num) {
        // 为了去重才用set
        Set<ProxyCfg> data = new HashSet<>();
        int count = 0;
        while (data.size() < num) {
            if (count >= MAX_RETRY_TIMES) {
                logger.warn("重试 {} 次获取ip无果，跳出循环", MAX_RETRY_TIMES);
                break;
            }
            ProxyCfg one = getOne();
            if (Objects.nonNull(one)) {
                data.add(one);
            } else {
                count++;
                try {
                    // 防止限流
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return new LinkedList<>(data);
    }

    /**
     * 检查每日拉取ip数量限制
     *
     * @return 是否超过限制
     */
    public boolean isOverFetchIpNumLimit() {
        boolean status = FETCH_IP_NUM_NOW.get() > MAX_FETCH_IP_NUM_EVERY_DAY.get();
        IS_SEND_ALARM_EMAIL.set(status);
        return status;
    }

    /**
     * 每晚12点，将当日累计拉取的ip数量重置为0
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void reSetFetchIpNum() {
        logger.info("重置当日累计拉取的ip数量为0");
        FETCH_IP_NUM_NOW.set(0);
        IS_SEND_ALARM_EMAIL.set(false);
    }

    /**
     * 发送告警邮件(每隔1个小时发送一次)
     */
    @Scheduled(cron = "0 0 0/1 * * ?")
    void sendAlarmEmail() {
//        if (IS_SEND_ALARM_EMAIL.get()) {
//            String subject = "芝麻代理IP拉取数量告警";
//            String content = "已拉取ip数=" + FETCH_IP_NUM_NOW.get() + ", 每日最大ip数限制=" + MAX_FETCH_IP_NUM_EVERY_DAY.get();
//            sendMailService.sendMail(subject, content);
//        }
    }

}
