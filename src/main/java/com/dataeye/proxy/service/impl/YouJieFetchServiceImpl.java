package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.config.YouJieConfig;
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
 * @description 游杰ip获取
 */
@Service
public class YouJieFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("YouJieFetchServiceImpl");

    @Resource
    YouJieConfig youJieConfig;

    @Override
    public ProxyIp getOne() throws InterruptedException {
        String ipFectchUrl = youJieConfig.getIpFectchUrl();
        String data = OkHttpTool.doGet(ipFectchUrl, Collections.emptyMap(), false);
        if (StringUtils.isBlank(data)) {
            logger.error("请求游杰代理ip为空，3秒后重试");
            Thread.sleep(3000L);
            return getOne();
        }
        JSONArray array = JSONObject.parseArray(data);
        if (array.size() <= 0) {
            throw new RuntimeException("游杰代理获取ip列表为空");
        }
        for (Object obj : array) {
            JSONObject jsonObject = JSONObject.parseObject(obj.toString());
            String ipAddr = jsonObject.getString("ip");
            String[] split = ipAddr.split(":");
            String ip = split[0];
            int port = Integer.parseInt(split[1]);
            // get one
            return ProxyIp.builder()
                    .host(ip)
                    .port(port)
                    .expireTime(LocalDateTime.of(2023, 1, 1, 1, 1, 1))
                    .userName("")
                    .password("")
                    .build();
        }
        return null;
    }

}
