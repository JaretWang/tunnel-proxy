package com.dataeye.proxy.component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.bean.IpTimer;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.HandlerCons;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.ProxyService;
import com.dataeye.proxy.service.ZhiMaProxyService;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/3/18 15:21
 * @description 代理ip选择器
 */
@Data
//@Slf4j
@Component
public class IpSelector {

    private static final Logger log = LogbackRollingFileUtil.getLogger("IpSelector");

    @Resource
    private ProxyServerConfig proxyServerConfig;
    @Resource
    private TunnelInitMapper tunnelInitMapper;
    @Autowired
    private ProxyService proxyService;
    @Autowired
    private ZhiMaProxyService zhiMaProxyService;

    /**
     * 代理ip池
     * proxy-server -> ip池
     * todo 或许用环形队列实现，但是就不会区分代理类型了。
     */
    public final ConcurrentHashMap<String, List<IpTimer>> scheduleProxyIpPool = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * 定时更新ip池
     * 每个 proxy server 使用一个ip池
     */
//    @PostConstruct
    public void scheduleUpdateIpPool() {
        int timerDuration = proxyServerConfig.getTimerDuration();
        long time = timerDuration * 1000;

        log.info("定时更新ip池，定时时间：{} s", timerDuration);
        String ipAccessLink = proxyServerConfig.getDirectIpAccessLink();
        executorService.submit((Runnable) () -> {
            while (true) {
                log.warn("循环检查ip池，每 {}s 更新", timerDuration);
                try {
                    List<TunnelInstance> tunnelInstanceList = tunnelInitMapper.queryAll();
                    for (TunnelInstance tunnelInstance : tunnelInstanceList) {

                        IpTimer ipTimer = new IpTimer(HandlerCons.ip, HandlerCons.port,null,null,
                                new AtomicInteger(0), new TimeCountDown(proxyServerConfig.getTimerDuration()));
                        scheduleProxyIpPool.put(tunnelInstance.toString(), Collections.singletonList(ipTimer));

//                        log.warn("暂时使用云代理， {}", proxyCfg);
//                        ProxyCfg proxyCfg = proxyService.getOne().get();
//                        IpTimer ipTimer = new IpTimer(proxyCfg.getHost(), proxyCfg.getPort(),
//                                proxyCfg.getUserName(), proxyCfg.getPassword(), null, null);
//                        scheduleProxyIpPool.put(tunnelInstance.toString(), Collections.singletonList(ipTimer));

//                        log.warn("暂时使用隧道快代理");
//                        IpTimer ipTimer = new IpTimer(proxyServerConfig.getRemoteHost(), proxyServerConfig.getRemotePort(),
//                                proxyServerConfig.getProxyUserName(), proxyServerConfig.getProxyPassword(), new AtomicInteger(0),
//                                new TimeCountDown(900));
//                        scheduleProxyIpPool.put(tunnelInstance.toString(), Collections.singletonList(ipTimer));

//                        changeIpForZhiMa(tunnelInstance);

//                        // 因为对方接口限流
//                        Thread.sleep(1200L);
//                        initIpForSingleProxyServer(tunnelInstance, ipAccessLink);
                    }
                    Thread.sleep(time);
                } catch (Throwable e) {
                    log.error("定时更新ip池出现异常，原因：{}", e.getCause().getMessage());
                }
            }
        });
    }

    @Deprecated
    public void changeIpForZhiMa(TunnelInstance tunnelInstance) {
        String id = tunnelInstance.toString();

        ProxyCfg proxyCfg = zhiMaProxyService.getOne().get();
        log.warn("使用芝麻代理获取ip， {}", proxyCfg.toString());
        IpTimer ipTimer = new IpTimer(proxyCfg.getHost(), proxyCfg.getPort(),
                proxyCfg.getUserName(), proxyCfg.getPassword(), new AtomicInteger(0), new TimeCountDown(proxyServerConfig.getTimerDuration()));

        List<IpTimer> proxyIpList = new LinkedList<>();
        proxyIpList.add(ipTimer);
        scheduleProxyIpPool.put(id, proxyIpList);
    }

    /**
     * 为单个 proxy server 初始化ip
     */
    public void initIpForSingleProxyServer(TunnelInstance tunnelInstance, String ipAccessLink) throws IOException {
        String id = tunnelInstance.toString();
        List<IpTimer> proxyIpList;
        if (scheduleProxyIpPool.containsKey(id)) {
            proxyIpList = scheduleProxyIpPool.get(id);
        } else {
            proxyIpList = new LinkedList<>();
        }
        checkAndUpdateIpList(proxyIpList, ipAccessLink);
        scheduleProxyIpPool.put(id, proxyIpList);
        String server = tunnelInstance.getIp() + ":" + tunnelInstance.getPort() +
                "(" + tunnelInstance.getAlias() + ")";
        log.info("ip池更新完成, server: {}, pool: {}", server, JSON.toJSONString(proxyIpList));
    }

    /**
     * 检查和更新代理IP列表
     *
     * @param proxyIpList
     * @param ipAccessLink
     * @throws IOException
     */
    public void checkAndUpdateIpList(List<IpTimer> proxyIpList, String ipAccessLink) throws IOException {
        log.info("检查和更新代理IP列表");
        if (ObjectUtils.isEmpty(proxyIpList)) {
            initIpList(proxyIpList, ipAccessLink);
            return;
        }
        for (IpTimer ipTimer : proxyIpList) {
            // 检查每个ip是否过期
            // todo 失效检查有待改进，使用别人返回的有效时间检测，而不用自己去维护
            TimeCountDown timeCountDown = ipTimer.getTimeCountDown();
            if (!timeCountDown.isEffective()) {
                log.warn("存在失效ip {}，移除并重新插入", ipTimer.getIp());
                proxyIpList.remove(ipTimer);
                initIpList(proxyIpList, ipAccessLink);
            }
        }
    }

    /**
     * 初始化ip池
     *
     * @param proxyIpList
     * @param ipAccessLink
     * @throws IOException
     */
    public void initIpList(List<IpTimer> proxyIpList, String ipAccessLink) throws IOException {
        List<String> randomProxyIpList = getRandomProxyIpList(ipAccessLink);
        if (ObjectUtils.isEmpty(randomProxyIpList)) {
            log.error("从代理商获取代理ip的结果为空，请检查");
            return;
        }

        // 检查是否有有异常信息
        String response = randomProxyIpList.get(0);
        if (response.contains("code")) {
            int code = JSONObject.parseObject(response).getInteger("code");
            if (code != 200) {
                log.error("获取代理ip异常, 原因：{}", response);
                return;
            }
        }

        // 插入新的ip
        for (String item : randomProxyIpList) {
            String[] split = item.split(":");
            if (split.length == 2) {
                String ip2 = split[0].trim();
                String portStr = split[1].trim();
                log.info("拆分后的 ip={}, port={}", ip2, portStr);
                int port2 = Integer.parseInt(portStr);
                TimeCountDown timeCountDown = new TimeCountDown(proxyServerConfig.getTimerDuration());
                IpTimer ipTimer2 = IpTimer.builder().ip(ip2).port(port2)
                        .referenceCount(new AtomicInteger(0)).timeCountDown(timeCountDown).build();
                proxyIpList.add(ipTimer2);
            }
        }
    }

    /**
     * 从芝麻代理随机获取代理ip，有效期：5-25分钟
     * todo 可以根据传递的参数ts=1，返回这个ip的有效时间
     *
     * @param proxyUrl 代理商地址
     * @return 随机代理ip组
     * @throws IOException
     */
    public static List<String> getRandomProxyIpList(String proxyUrl) throws IOException {
        long begin = System.currentTimeMillis();
        List<String> proxyList = new ArrayList<>(2);
        okhttp3.Request request = new okhttp3.Request.Builder().get().url(proxyUrl).build();
        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(request).execute();
        String body = Objects.requireNonNull(response.body()).string();
        if (StringUtils.isNotBlank(body) && body.contains(":")) {
            String lineSeparator = System.lineSeparator();
            String[] split = body.split(lineSeparator);
            proxyList.addAll(Arrays.asList(split));
        }
        log.debug("从芝麻代理随机获取代理 ip 耗时：{} ms", (System.currentTimeMillis() - begin));
        return proxyList;
    }

}
