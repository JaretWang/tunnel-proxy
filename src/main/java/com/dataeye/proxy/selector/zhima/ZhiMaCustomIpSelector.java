package com.dataeye.proxy.selector.zhima;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.CustomIpAllocate;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.config.ZhiMaDingZhiConfig;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaExclusiveFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.NetUtils;
import com.dataeye.proxy.utils.OkHttpTool;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/8/17 23:02
 * @description 芝麻定制ip选择器
 */
@Component
public class ZhiMaCustomIpSelector implements CommonIpSelector {

    public static final ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> IP_POOL = new ConcurrentHashMap<>();
    private static final Logger log = MyLogbackRollingFileUtil.getLogger("ZhiMaCustomIpSelector");
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("ZhiMaCustomIpSelector"), new ThreadPoolExecutor.AbortPolicy());
    private static List<Integer> netCardSeqList;

    @Value("${server.port}")
    int port;
    @Autowired
    TunnelInitService tunnelInitService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    ZhiMaDingZhiConfig zhiMaDingZhiConfig;
    @Autowired
    ZhiMaExclusiveFetchServiceImpl zhiMaExclusiveFetchService;

    @Override
    public ProxyIp getOne() {
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("get ip error, tunnelInstance is null");
            return null;
        }
        ConcurrentLinkedQueue<ProxyIp> proxyCfgsQueue = IP_POOL.get(tunnelInstance.getAlias());
        if (Objects.isNull(proxyCfgsQueue)) {
            log.error("queue is not exist");
            return null;
        }
        ProxyIp poll = proxyCfgsQueue.poll();
        if (Objects.isNull(poll)) {
            log.error("the ip from queue is null");
            return null;
        }
        // 只取有效的
        boolean valid = poll.getValid().get();
        if (!valid) {
            log.info("ip={} is invalid and will be removed", poll.getIpAddr());
            return getOne();
        }
        // 取了需要再放进去
        proxyCfgsQueue.offer(poll);
        return poll;
    }

    @Override
    public List<ProxyIp> getIpList(int count) throws InterruptedException {
        LinkedList<ProxyIp> ips = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            ProxyIp proxyIp = getOne();
            if (proxyIp != null) {
                ips.add(proxyIp);
            }
        }
        return ips;
    }

    @Override
    public void addWhiteList() {

    }

    @Override
    public void healthCheck() {

    }

    @Override
    public void successPercentStatistics() {

    }

    @Override
    public void addFixedNumIp(int num) {

    }

    @Override
    public void removeIp(String ip, int port) {

    }

    @Override
    public ConcurrentLinkedQueue<ProxyIp> getIpPool() {
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("tunnelInstance is null");
            return null;
        }
        return IP_POOL.get(tunnelInstance.getAlias());
    }

    public boolean isStart() {
        String innerIp = tunnelInitService.getEth0Inet4InnerIp();
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        return defaultTunnel.getType() == TunnelType.ZHIMA_DINGZHI.getId()
                && defaultTunnel.getLocation().equalsIgnoreCase(innerIp.trim())
                && defaultTunnel.getEnable() == 1;
    }

    @Override
    public void init() {
        if (!proxyServerConfig.isEnable() || !isStart()) {
            return;
        }

        log.info("芝麻定制ip - 初始化ip池");
        zhiMaExclusiveFetchService.init();

        String eth0Inet4InnerIp = tunnelInitService.getEth0Inet4InnerIp();
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
        ConcurrentLinkedQueue<ProxyIp> proxyIps = IP_POOL.get(alias);
        log.info("初始化ip池, processSeq={}, alias={}, ipPool={}", JSON.toJSONString(netCardSeqList), alias, JSON.toJSONString(proxyIps));
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(() -> {
            checkNetCard();
            handleInvalidIp(log, proxyIps);
            printIpPool(log, IP_POOL);
        }, 1, 5, TimeUnit.MINUTES);
    }

    /**
     * 拨号网卡心跳检查
     */
    void checkNetCard() {
        String testUrl = "https://www.baidu.com";
        for (Integer seq : netCardSeqList) {
            ProxyIp proxyIp = getIpByNetCardSeq(seq);
            Response response = null;
            String resp = null;
            try {
                response = OkHttpTool.sendGetByProxy2(testUrl, proxyIp.getHost(), proxyIp.getPort(), proxyIp.getUserName(), proxyIp.getPassword(), null, null);
                if (response != null && response.code() == 200) {
                    resp = response.body().string();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                OkHttpTool.closeResponse(response);
            }

            if (StringUtils.isNotBlank(resp)) {
                continue;
            }

            // 重新拨号
            log.info("检测到网卡 [{}] 不可用, 即将重新拨号");
            exchangeIp(seq);

            // 剔除旧ip，更换新ip
            String alias = tunnelInitService.getDefaultTunnel().getAlias();
            ConcurrentLinkedQueue<ProxyIp> queue = IP_POOL.get(alias);
            if (IP_POOL == null || queue == null) {
                log.error("拨号网卡心跳检查失败, {} 的ip池为空", alias);
                continue;
            }
            String oldHost = proxyIp.getHost();
            int oldPort = proxyIp.getPort().intValue();
            for (ProxyIp ip : queue) {
                String host = ip.getHost();
                int port = ip.getPort().intValue();
                if (host.equalsIgnoreCase(oldHost) && oldPort == port) {
                    ip.getValid().set(false);
                    // 追加新的ip
                    ProxyIp newIp = getIpByNetCardSeq(seq);
                    queue.offer(newIp);
                    break;
                }
            }
        }

    }

    /**
     * 定时重播，更换ip
     */
    @Scheduled(cron = "0 0 0/1 * * ?")
    public void exchange() {
        if (!proxyServerConfig.isEnable() || tunnelInitService.getDefaultTunnel().getType() != TunnelType.ZHIMA_DINGZHI.getId()) {
            return;
        }
        // 废除原来的ip
        String alias = tunnelInitService.getDefaultTunnel().getAlias();
        ConcurrentLinkedQueue<ProxyIp> proxyIps = IP_POOL.get(alias);
        for (ProxyIp proxyIp : proxyIps) {
            proxyIp.getValid().set(false);
        }
        for (Integer seq : netCardSeqList) {
            exchangeIp(seq);
            // 更新ip池
            addOne(seq);
        }
        log.info("定时重播更换ip, processSeq={}, ipPool={}", JSON.toJSONString(netCardSeqList), JSON.toJSONString(proxyIps));
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
        ProxyIp proxyIp = getIpByNetCardSeq(seq);

        String alias = tunnelInitService.getDefaultTunnel().getAlias();
        ConcurrentLinkedQueue<ProxyIp> queue = new ConcurrentLinkedQueue<>();
        queue.offer(proxyIp);
        ConcurrentLinkedQueue<ProxyIp> old = IP_POOL.putIfAbsent(alias, queue);
        if (old != null) {
            old.offer(proxyIp);
            IP_POOL.put(alias, old);
        }
    }

    public ProxyIp getIpByNetCardSeq(int seq) {
        String realUrl = zhiMaDingZhiConfig.getGetFixedNumIpUrl() + seq;
        return getIpByNetCardSeq(realUrl);
    }

    public ProxyIp getIpByNetCardSeq(String realUrl) {
        if (StringUtils.isBlank(realUrl)) {
            throw new RuntimeException("realUrl is blank");
        }
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
            return null;
        }
        JSONArray array = (JSONArray) data;
        if (array.isEmpty()) {
            log.error("获取ip列表失败, data is empty");
            return null;
        }

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
                return proxyIp;
            }
        }
        return null;
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


}
