package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.YouJieConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import com.dataeye.proxy.utils.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 游杰ip获取
 */
//@Service
public class YouJieFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("YouJieFetchServiceImpl");
    private static final AtomicInteger FETCH_IP_NUM_NOW = new AtomicInteger(0);
    private static final AtomicBoolean IS_SEND_ALARM_EMAIL = new AtomicBoolean(false);

    @Resource
    YouJieConfig youJieConfig;
    @Autowired
    SendMailService sendMailService;

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws InterruptedException {
        return getIpList(1, tunnelInstance).get(0);
    }

    public List<ProxyIp> getIpList(int num, TunnelInstance tunnelInstance) throws InterruptedException {
        if (isOverFetchIpNumLimit(tunnelInstance)) {
            logger.error("已达到每日最大拉取ip数量 {} !!!", tunnelInstance.getMaxFetchIpNumEveryDay());
            return Collections.emptyList();
        }

        String url = youJieConfig.getIpFectchUrl() + "&num=" + num;

        String data = OkHttpTool.doGet(url, Collections.emptyMap(), false);
        if (StringUtils.isBlank(data)) {
            logger.error("游杰代理拉取ip为空");
            return Collections.emptyList();
        }
        JSONArray array = JSONObject.parseArray(data);
        if (array.size() <= 0) {
            logger.error("游杰代理获取ip列表为空");
            return Collections.emptyList();
        }

        List<ProxyIp> result = new LinkedList<>();
        for (Object obj : array) {
            JSONObject jsonObject = JSONObject.parseObject(obj.toString());
            String ipAddr = jsonObject.getString("ip");
            String exptime = jsonObject.getString("Exptime");
            String[] split = ipAddr.split(":");
            String ip = split[0];
            int port = Integer.parseInt(split[1]);
            LocalDateTime localDateTime = TimeUtils.str2LocalDate(exptime);
            // 计数
            FETCH_IP_NUM_NOW.incrementAndGet();
            ProxyIp proxyIp = ProxyIp.builder()
                    .host(ip)
                    .port(port)
                    .expireTime(localDateTime)
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
    public boolean isOverFetchIpNumLimit(TunnelInstance tunnelInstance) {
        boolean status = FETCH_IP_NUM_NOW.get() > tunnelInstance.getMaxFetchIpNumEveryDay();
        IS_SEND_ALARM_EMAIL.set(status);
        return status;
    }

    /**
     * 每晚12点，将当日累计拉取的ip数量重置为0
     */
//    @Scheduled(cron = "0 0 0 * * ?")
    public void reSetFetchIpNum() {
        logger.info("游杰代理 - 重置当日累计拉取的ip数量为0");
        FETCH_IP_NUM_NOW.set(0);
        IS_SEND_ALARM_EMAIL.set(false);
    }

    /**
     * 每隔1小时,打印一次当日累计拉取ip数量
     */
//    @Scheduled(cron = "0 0 0/1 * * ?")
    void getIpFetchNumNow() {
        logger.info("游杰代理 - 今日累计拉取IP数量={}", FETCH_IP_NUM_NOW.get());
    }

}
