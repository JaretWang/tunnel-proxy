package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.config.DailiCloudConfig;
import com.dataeye.proxy.config.ZhiMaConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;

/**
 * 直接从芝麻代理官网拉取ip，有效时间：25min-3h
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 芝麻ip获取
 */
@Service
public class ZhiMaFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ZhiMaFetchServiceImpl");

    @Resource
    ZhiMaConfig zhiMaConfig;

    @Override
    public ProxyCfg getOne() {
        String url = zhiMaConfig.getDirectGetUrl();
//        String url = zhiMaConfig.getExclusiveGetUrl();
//        String url = zhiMaConfig.getTunnelGetUrl();

        String json = OkHttpTool.doGet(url, Collections.emptyMap(), false);
        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("success");
        if (success) {
            JSONArray data = jsonObject.getJSONArray("data");
            if (data.size() > 0) {
                String ipItem = data.get(0).toString();
                JSONObject ipElement = JSONObject.parseObject(ipItem);
                String ip = ipElement.getString("ip");
                int port = ipElement.getIntValue("port");
                String expireTime = ipElement.getString("expire_time");
                LocalDateTime parse = LocalDateTime.parse(expireTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return ProxyCfg.builder()
                        .host(ip)
                        .port(port)
                        .expireTime(parse)
                        .userName("")
                        .password("")
                        .build();
            }
        } else {
            throw new RuntimeException("从芝麻代理官网拉取ip失败，原因：" + json);
        }
        return null;
    }


}
