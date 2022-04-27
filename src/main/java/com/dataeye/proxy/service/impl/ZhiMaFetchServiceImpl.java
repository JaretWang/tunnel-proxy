package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.config.ZhiMaConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ProxyIp getOne() throws InterruptedException {
        return getIpList(1).get(0);
    }

    public List<ProxyIp> getIpList(int num) throws InterruptedException {
        if (isOverFetchIpNumLimit()) {
            logger.error("已达到每日最大拉取ip数量 {} !!!", MAX_FETCH_IP_NUM_EVERY_DAY.get());
            return Collections.emptyList();
        }

        String url = zhiMaConfig.getDirectGetUrl() + "&num=" + num;

        String json = OkHttpTool.doGet(url, Collections.emptyMap(), false);
//        String json = buildIpPoolJson();
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
                return getIpList(num);
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
            LocalDateTime parse = LocalDateTime.parse(expireTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // 计数
            FETCH_IP_NUM_NOW.incrementAndGet();
            ProxyIp proxyIp = ProxyIp.builder()
                    .host(ip)
                    .port(port)
                    .expireTime(parse)
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
        logger.info("芝麻代理 - 重置当日累计拉取的ip数量为0");
        FETCH_IP_NUM_NOW.set(0);
        IS_SEND_ALARM_EMAIL.set(false);
    }

//    /**
//     * 发送告警邮件(每隔1个小时发送一次)
//     */
//    @Scheduled(cron = "0 0 0/1 * * ?")
//    void sendAlarmEmail() {
//        if (IS_SEND_ALARM_EMAIL.get()) {
//            String subject = "芝麻代理IP拉取数量告警";
//            String content = "已拉取ip数=" + FETCH_IP_NUM_NOW.get() + ", 每日最大ip数限制=" + MAX_FETCH_IP_NUM_EVERY_DAY.get();
//            sendMailService.sendMail(subject, content);
//        }
//    }

    /**
     * 每隔1小时,打印一次当日累计拉取ip数量
     */
    @Scheduled(cron = "0 0 0/1 * * ?")
    void getIpFetchNumNow() {
        logger.info("芝麻代理 - 今日累计拉取IP数量={}", FETCH_IP_NUM_NOW.get());
    }

    /**
     * 伪造拉取的数据
     * @return
     */
    private static String buildIpPoolJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", 0);
        jsonObject.put("msg", "0");
        jsonObject.put("success", true);
        LinkedList<JSONObject> data = new LinkedList<>();
        Random random = new Random();
        for (int i = 10; i < 20; i++) {
            int ip_val = random.nextInt(6553);
            int min_val = random.nextInt(5);
            int second_val = random.nextInt(60);
            JSONObject element = new JSONObject();
            element.put("ip", "10.10.10." + i);
            element.put("port", ip_val);
            element.put("expire_time", LocalDateTime.now().plusMinutes(min_val).plusSeconds(second_val));
            data.add(element);
        }
        jsonObject.put("data", data);
        return jsonObject.toJSONString();
    }

}
