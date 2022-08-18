package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.CustomIpAllocate;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ZhiMaDingZhiConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.NetUtils;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/8/3 16:24
 * @description 芝麻定制高质量ip
 */
@Service
public class ZhiMaDingZhiServiceImpl implements ProxyFetchService {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("ZhiMaDingZhiServiceImpl");

    /**
     * key：进程ip:port
     * value：网卡序号
     */
    public static final ConcurrentHashMap<String, Integer> PROCESS_SEQ = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> IP_POOL = new ConcurrentHashMap<>();
    /**
     * 最大网卡序号
     */
    private static final int MAX_NETWORK_CARD_SEQ = 39;
    /**
     * 每个进程分配的ip数
     */
    private static final int IP_NUM_EVERY_PROCESS = 3;
    private static List<Integer> netCardSeqList;

    @Value("${server.port}")
    int port;
    @Autowired
    IpSelector ipSelector;
    @Autowired
    TunnelInitService tunnelInitService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    ZhiMaDingZhiConfig zhiMaDingZhiConfig;

    public boolean isStart() {
        String innerIp = tunnelInitService.getInnerIp();
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        return defaultTunnel.getType() == TunnelType.HIGH_QUALITY.getId()
                && defaultTunnel.getLocation().equalsIgnoreCase(innerIp.trim())
                && defaultTunnel.getEnable() == 1;
    }

    public void init() {
        if (!proxyServerConfig.isEnable() || !isStart()) {
            return;
        }

        log.info("芝麻定制ip - 初始化ip池");
        String eth0Inet4InnerIp = tunnelInitService.getInnerIp();
        CustomIpAllocate customIpAllocate = tunnelInitService.getCustomIpAllocate(eth0Inet4InnerIp, port);
        if (customIpAllocate == null) {
            throw new RuntimeException("customIpAllocate is null, ip=" + eth0Inet4InnerIp + ", port=" + port);
        }
        // 添加ip白名单
        addWhiteList(customIpAllocate);
        // 获取拨号需要的网卡序号
        netCardSeqList = getRealNetworkCardSeq(customIpAllocate);
        // 初始化ip池
        netCardSeqList.forEach(this::addOne);
        String alias = tunnelInitService.getDefaultTunnel().getAlias();
        ConcurrentLinkedQueue<ProxyIp> proxyIps = ipSelector.getProxyIpPool().get(alias);
        log.info("初始化ip池, processSeq={}, alias={}, ipPool={}", netCardSeqList.toString(), alias, proxyIps.toString());
    }

    /**
     * 定时重播，更换ip
     */
    @Scheduled(cron = "0 0 0/1 * * ?")
    public void exchange() {
        if (!proxyServerConfig.isEnable()) {
            return;
        }
        // 废除原来的ip
        String alias = tunnelInitService.getDefaultTunnel().getAlias();
        ConcurrentLinkedQueue<ProxyIp> proxyIps = ipSelector.getProxyIpPool().get(alias);
        for (ProxyIp proxyIp : proxyIps) {
            proxyIp.getValid().set(false);
        }
        for (Integer seq : netCardSeqList) {
            exchangeIp(seq);
            // 更新ip池
            addOne(seq);
        }
        log.info("定时重播更换ip, processSeq={}, ipPool={}", netCardSeqList.toString(), proxyIps.toString());
    }

    String getInnerIp() {
        String eth0Inet4InnerIp = NetUtils.getEth0Inet4InnerIp();
        if (StringUtils.isBlank(eth0Inet4InnerIp)) {
            throw new RuntimeException("获取本机eth0网卡的ip失败");
        }
        return eth0Inet4InnerIp;
    }

    /**
     * 获取拨号器序号列表
     *
     * @return
     */
    List<Integer> getRealNetworkCardSeq(CustomIpAllocate customIpAllocate) {
        String netCardSeq = customIpAllocate.getNetCardSeq();
        String[] split = netCardSeq.split(",");
        return Arrays.stream(split).map(e -> Integer.parseInt(e.trim())).collect(Collectors.toList());
    }

    /**
     * 添加ip白名单
     */
    void addWhiteList(CustomIpAllocate customIpAllocate) {
        String outIp = customIpAllocate.getOutIp();
        if (StringUtils.isBlank(outIp)) {
            throw new RuntimeException("添加白名单失败, outIp is empty");
        }
        String addWhilteListUrl = zhiMaDingZhiConfig.getAddWhilteListUrl() + outIp;
        String addResp = OkHttpTool.doGet(addWhilteListUrl, null, false);
        log.info("添加ip白名单, config={}, result={}", customIpAllocate, addResp);
    }

    /**
     * 添加单个ip
     *
     * @param seq
     */
    void addOne(int seq) {
        String realUrl = zhiMaDingZhiConfig.getGetFixedNumIpUrl() + seq;
        String resp = OkHttpTool.doGet(realUrl, null, false);
        if (StringUtils.isBlank(resp)) {
            throw new RuntimeException("添加单个ip失败");
        }
        JSONObject respData = JSONObject.parseObject(resp);
        String code = respData.getOrDefault("code", "").toString();
        String msg = respData.getOrDefault("msg", "").toString();
        Object data = respData.get("data");
        int retry = 0;
        //boolean noData = !"0".equals(code) || data == null;
        while ((!"0".equals(code) || data == null) && retry < 5) {
            retry++;
            try {
                // 必须3秒。因为接口有2秒钟的限流
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.error("添加单个ip失败, 重试次数={}, code={}, msg={}, data={}", retry, code, msg, data);
            String resp2 = OkHttpTool.doGet(realUrl, null, false);
            if (StringUtils.isNotBlank(resp2)) {
                JSONObject respData2 = JSONObject.parseObject(resp2);
                code = respData2.getOrDefault("code", "").toString();
                msg = respData2.getOrDefault("msg", "").toString();
                data = respData2.get("data");
            }
        }
        if (data == null) {
            log.error("添加单个ip失败, data is null");
            return;
        }
        JSONArray array = (JSONArray) data;
        if (array.isEmpty()) {
            log.error("获取ip列表失败, data is empty");
            return;
        }

        String alias = tunnelInitService.getDefaultTunnel().getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipSelector.getProxyIpPool();
        for (Object obj : array) {
            JSONObject element = (JSONObject) obj;
            String expireTime = element.getOrDefault("expiry_time", "").toString();
            String host = element.getOrDefault("s5_ip", "").toString();
            String port = element.getOrDefault("s5_port", "").toString();
            if (StringUtils.isNotBlank(expireTime) && StringUtils.isNotBlank(host) & StringUtils.isNotBlank(port)) {
                String millis = expireTime + "000";
                Instant instant = new Date(Long.parseLong(millis)).toInstant();
                LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                ProxyIp proxyIp = ProxyIp.builder()
                        .host(host)
                        .port(Integer.parseInt(port))
                        .userName("")
                        .password("")
                        .expireTime(dateTime)
                        .valid(new AtomicBoolean(true))
                        .build();
                ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
                queue.offer(proxyIp);
                ConcurrentLinkedQueue<ProxyIp> old = proxyIpPool.putIfAbsent(alias, queue);
                if (old != null) {
                    old.offer(proxyIp);
                    proxyIpPool.put(alias, old);
                }
//                if (proxyIpPool.containsKey(alias)) {
//                    ConcurrentLinkedQueue<ProxyIp> queue = proxyIpPool.get(alias);
//                    queue.offer(proxyIp);
//                } else {
//                    ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
//                    queue.offer(proxyIp);
//                    proxyIpPool.put(alias, queue);
//                }
            }
        }
    }

    /**
     * 重新拨号
     *
     * @param seq
     */
    void exchangeIp(int seq) {
        String realUrl = zhiMaDingZhiConfig.getChangeIpUrl() + seq;
        String resp = OkHttpTool.doGet(realUrl, null, false);
        if (StringUtils.isBlank(resp)) {
            throw new RuntimeException("重播失败, resp is empty");
        }
        JSONObject respData = JSONObject.parseObject(resp);
        String code = respData.getOrDefault("code", "").toString();
        String msg = respData.getOrDefault("msg", "").toString();
        String ipid = respData.getOrDefault("ipid", "").toString();
        if (!"0".equals(code)) {
            log.error("重播失败, code={}, msg={}, 网卡={}", code, msg, ipid);
            return;
        }
        log.info("重播成功, code={}, msg={}, 网卡={}", code, msg, ipid);
    }

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws Exception {
        return null;
    }
}
