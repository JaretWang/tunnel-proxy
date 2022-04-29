package com.dataeye.proxy.service.impl;

import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.DailiCloudConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

/**
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 代理云ip获取
 */
@Service
public class DailiCloudFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("DailiCloudFetchServiceImpl");

    @Resource
    DailiCloudConfig dailiCloudConfig;

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws InterruptedException {
        String ipFectchUrl = dailiCloudConfig.getIpFectchUrl();
        String data = OkHttpTool.doGet(ipFectchUrl, Collections.emptyMap(), false);
        if (StringUtils.isBlank(data)) {
            throw new RuntimeException("代理云获取ip为空");
//            logger.error("代理云获取ip为空，3秒后重试");
//            Thread.sleep(3000L);
//            return getOne();
        }
        String[] split = data.split(System.lineSeparator());
        if (split.length <= 0) {
            logger.error("使用换行符 {} 获取分割ip失败", System.lineSeparator());
        }
        String[] split2 = split[0].split(",");
        String ip = split2[0].split(":")[0];
        String port = split2[0].split(":")[1];
        long expireTime = Long.parseLong(split2[split2.length - 1]);
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(expireTime, 0, ZoneOffset.ofHours(8));

        return ProxyIp.builder()
                .host(ip)
                .port(Integer.parseInt(port))
                .expireTime(dateTime)
                .userName(dailiCloudConfig.getUsername())
                .password(dailiCloudConfig.getPassword())
                .build();
    }

}
