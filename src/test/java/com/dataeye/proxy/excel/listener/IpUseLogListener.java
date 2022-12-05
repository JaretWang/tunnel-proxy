package com.dataeye.proxy.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.dataeye.proxy.excel.bean.IpUseLog;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/8/2 15:38
 * @description
 */
@NoArgsConstructor
@Slf4j
public class IpUseLogListener extends AnalysisEventListener<IpUseLog> {

    ConcurrentHashMap<String, AtomicInteger> ipUseCount = new ConcurrentHashMap<>();
    Map<String,String> tunnelIp = new HashMap<String,String>() {{
        put("120.78.14.57","adx-proxy-000");
        put("39.108.108.148","adx-proxy-001");
        put("120.79.193.46","adx-proxy-002");
        put("47.107.231.88","adx-proxy-003");
        put("120.25.155.236","adx-proxy-004");
        put("120.24.219.158","adx-proxy-005");
        put("120.25.236.214","adx-proxy-006");
        put("120.24.232.149","adx-proxy-007");
        put("120.24.50.80","adx-proxy-008");
        put("112.74.108.116","adx-proxy-009");
        put("120.77.156.128","adx-proxy-010");
        put("120.77.177.137","adx-proxy-011");
        put("39.108.4.144","adx-proxy-012");
        put("39.108.1.49","adx-proxy-013");
        put("39.108.210.98","adx-proxy-014");
        put("39.108.14.29","adx-proxy-015");
        put("39.108.164.31","adx-proxy-016");
    }};
    Map<String,String> edxIp = new HashMap<String,String>() {{
        put("47.106.220.225","edx-app-001");
        put("120.25.202.242","edx-app-002");
        put("47.106.91.101","edx-app-003");
        put("120.78.229.110","edx-app-004");
        put("120.76.195.162","edx-app-005");
        put("120.76.158.30","edx-app-006");
        put("120.76.219.50","edx-app-007");
        put("47.106.169.235","edx-app-008");
        put("120.79.133.21","edx-app-009");
        put("120.78.219.56","edx-app-010");
        put("120.24.47.16","edx-app-011");
        put("120.77.54.159","edx-app-012");
        put("112.74.83.44","edx-app-013");
        put("120.77.54.48","edx-app-014");
        put("39.108.176.191","edx-app-015");
        put("47.106.91.86","edx-app-016");
        put("120.77.238.36","edx-app-017");
        put("120.77.147.157","edx-app-018");
        put("120.78.220.91","edx-app-019");
        put("120.79.210.232","edx-app-020");
        put("39.108.91.78","edx-app-021");
    }};
    Map<String,String> edxCrawlIp = new HashMap<String,String>() {{
        put("120.79.94.169","edxpro-crawl001");
        put("39.108.156.151","edx-crawllab-002");
        put("39.108.14.61","edx-crawllab-004");
        put("39.108.163.39","edx-crawllab-005");
    }};
    String menu1 = "185013";
    String menu2 = "228695";
    Set<String> whiteIpList;
//    private AtomicInteger total = new AtomicInteger(0);
//    private AtomicInteger tunnel = new AtomicInteger(0);
//    private AtomicInteger other = new AtomicInteger(0);

    public IpUseLogListener(Set<String> whiteIpList) {
        this.whiteIpList = whiteIpList;
    }

    @Override
    public void invoke(IpUseLog data, AnalysisContext context) {
        if (data != null) {
            String menu = data.getMenu();
            // 套餐二：228695 套餐一：185013
            if (menu.equals(menu2)) {
                String whiteIp = data.getWhiteIIp();
//                if (tunnels.contains(whiteIIp)) {
//                    tunnel.incrementAndGet();
//                } else {
//                    other.incrementAndGet();
//                }
                if (tunnelIp.containsKey(whiteIp.trim())) {
                    return;
                }
                // 非隧道ip使用记录
                AtomicInteger value = ipUseCount.putIfAbsent(whiteIp, new AtomicInteger(1));
                if (value != null) {
                    value.incrementAndGet();
                }
//                total.incrementAndGet();
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
//        String s = "total=" + total.get() + ", " +
//                "tunnel=" + tunnel.get() + ", " +
//                "other=" + other.get();
//        System.out.println(s);
//        System.out.println(JSON.toJSONString(MapUtils.sort(ipUseCount, true)));
        Set<String> collect = new HashSet<>(ipUseCount.keySet());
        if (!collect.isEmpty()) {
            Set<String> filter = collect.stream().map(ip -> {
                if (edxIp.containsKey(ip.trim())) {
                    return ip + "(" + edxIp.get(ip) + ")";
                }
                if (edxCrawlIp.containsKey(ip.trim())) {
                    return ip + "(" + edxCrawlIp.get(ip) + ")";
                }
                return ip;
            }).collect(Collectors.toSet());
            whiteIpList.addAll(filter);
        }
    }

}
