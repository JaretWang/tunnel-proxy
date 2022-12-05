package com.dataeye.proxy.service;

import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.config.VpsConfig;
import com.dataeye.proxy.selector.vps.VpsIpSelector;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/11/15 17:45
 * @description
 */
@Service
public class EnterpriseWeChatRobotSerice {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("EnterpriseWeChatRobotSerice");

    @Autowired
    VpsIpSelector vpsIpSelector;
    @Autowired
    VpsConfig vpsConfig;

    @Scheduled(cron = "0 0/10 * * * ?")
    void scheduleSend() {
        try {
            ConcurrentHashMap<VpsInstance, AtomicInteger> vpsAlarmCache = vpsIpSelector.getVpsAlarmCache();
            if (vpsAlarmCache == null || vpsAlarmCache.isEmpty()) {
                log.info("vpsAlarmCache is empty, quit");
                return;
            }
            StringJoiner joiner = new StringJoiner(", ");
            vpsAlarmCache.forEach((k, v) -> joiner.add(k.getIpAddrUsernamePwd() + ", 重播次数=" + v.get()));
            String msg = "VPS多次重播失败告警, 机器数量: " + vpsAlarmCache.size() + System.lineSeparator() + joiner.toString();
            log.info("发送企微机器告警信息: {}", msg);
            send(msg);
            vpsAlarmCache.clear();
        } catch (Exception e) {
            log.info("定时发送异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送信息
     */
    public void send(String msg) {
        try {
            JSONObject content = new JSONObject();
            content.put("content", msg);
            JSONObject body = new JSONObject();
            body.put("msgtype", "text");
            body.put("text", content);

            String url = vpsConfig.getWechatAlarmRobotUrl();
            Map<String, String> headers = new HashMap<>(1);
            headers.put("Content-Type", "application/json;charset=UTF-8");

            String resp = OkHttpTool.doPost(url, headers, body);
            log.info("企微机器人发送告警信息, body={}, resp={}", body.toJSONString(), resp);
        } catch (Exception e) {
            log.error("发送信息到企业微信失, cause={}", e.getMessage(), e);
        }
    }

}
