package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.DailiCloudConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.utils.OkHttpTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

/**
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 代理云ip获取
 */
@Slf4j
//@Service
public class DailiCloudFetchServiceImpl implements ProxyFetchService {

    @Resource
    DailiCloudConfig dailiCloudConfig;

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) {
        String ipFectchUrl = dailiCloudConfig.getIpFectchUrl();
        String data = OkHttpTool.doGet(ipFectchUrl, Collections.emptyMap(), false);
        if (StringUtils.isBlank(data)) {
            log.error("代理云 - api接口返回为空, respone={}", data);
            return null;
//            log.error("代理云获取ip为空，3秒后重试");
//            Thread.sleep(3000L);
//            return getOne();
        }
        JSONObject result = JSONObject.parseObject(data);
        if (!result.getBooleanValue("success")) {
            log.error("代理云 - 拉取ip失败, respone={}", data);
            return null;
        }
        JSONArray array = result.getJSONArray("result");
        if (array.isEmpty()) {
            log.error("代理云 - 获取ip列表为空, respone={}", data);
            return null;
        }
        for (Object element : array) {
            if (element instanceof JSONObject) {
                JSONObject item = (JSONObject) element;
                String ip = item.getString("ip");
                int port = item.getIntValue("port");
                int expireTime = item.getIntValue("ltime");
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(expireTime, 0, ZoneOffset.ofHours(8));
                return ProxyIp.builder()
                        .host(ip)
                        .port(port)
                        .expireTime(dateTime)
                        .userName(dailiCloudConfig.getUsername())
                        .password(dailiCloudConfig.getPassword())
                        .build();
            }
        }
        return null;
    }

}
