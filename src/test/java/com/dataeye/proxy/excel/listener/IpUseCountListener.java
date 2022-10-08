package com.dataeye.proxy.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.excel.bean.IpUseCount;
import com.dataeye.proxy.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/8/2 15:38
 * @description
 */
@Slf4j
public class IpUseCountListener extends AnalysisEventListener<IpUseCount> {

    ConcurrentHashMap<String, AtomicInteger> ipUseCount = new ConcurrentHashMap<>();
    List<String> tunnels = Arrays.asList(
            "39.108.108.148",
            "120.78.14.57",
            "120.79.193.46",
            "47.107.231.88",
            "120.25.155.236",
            "120.24.219.158",
            "120.25.236.214",
            "120.24.232.149",
            "120.24.50.80",
            "112.74.108.116",
            "120.77.156.128",
            "120.77.177.137",
            "39.108.4.144",
            "39.108.1.49",
            "39.108.1.49",
            "39.108.4.144",
            "39.108.210.98"
    );

    List<String> crawl = Arrays.asList(
            "1111",
            "1111",
            "1111",
            "1111"
    );

    @Override
    public void invoke(IpUseCount data, AnalysisContext context) {
        if (data != null) {
            String ourIp = data.getOurIp().trim();
            if (tunnels.contains(ourIp)) {
                return;
            }
            AtomicInteger old = ipUseCount.putIfAbsent(ourIp, new AtomicInteger(1));
            if (old != null) {
                old.incrementAndGet();
                ipUseCount.put(ourIp, old);
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        System.out.println(JSON.toJSONString(MapUtils.sort(ipUseCount, true)));
    }

}
