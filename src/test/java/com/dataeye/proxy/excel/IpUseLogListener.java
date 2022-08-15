package com.dataeye.proxy.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/8/2 15:38
 * @description
 */
@Slf4j
public class IpUseLogListener extends AnalysisEventListener<IpUseLog> {

    ConcurrentHashMap<String, AtomicInteger> ipUseCount = new ConcurrentHashMap<>();
    HashMap<String, String> tunnels = new HashMap<String, String>() {
        {
            put("120.78.14.57", "adx爬虫-优量广告");
            put("39.108.108.148", "adx爬虫-穿山甲");
            put("120.79.193.46", "adx爬虫-腾讯视频");
            put("47.107.231.88", "adx爬虫-抖音中心");
            put("120.25.155.236", "adx爬虫-腾讯系列");
            put("120.24.219.158", "adx爬虫-字节系列");
            put("120.25.236.214", "adx爬虫-百度系列");
            put("120.24.232.149", "adx爬虫-阿里系列");
            put("120.24.50.80", "adx爬虫-搜狐新浪系列");
            put("112.74.108.116", "adx爬虫-低素材量系列");
        }
    };
    private AtomicInteger total = new AtomicInteger(0);
    private AtomicInteger tunnel = new AtomicInteger(0);
    private AtomicInteger other = new AtomicInteger(0);

    @Override
    public void invoke(IpUseLog data, AnalysisContext context) {
        if (data != null) {
            String whiteIIp = data.getWhiteIIp();
            if (tunnels.containsKey(whiteIIp)) {
                tunnel.incrementAndGet();
                whiteIIp = tunnels.get(whiteIIp);
            } else {
                other.incrementAndGet();
            }
            AtomicInteger value = ipUseCount.putIfAbsent(whiteIIp, new AtomicInteger(1));
            if (value != null) {
                value.incrementAndGet();
                total.incrementAndGet();
                ipUseCount.put(whiteIIp, value);
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        String s = "total=" + total.get() + ", " +
                "tunnel=" + tunnel.get() + ", " +
                "other=" + other.get();
        System.out.println(s);
        System.out.println(JSON.toJSONString(MapUtils.sort(ipUseCount, true)));
    }

}
