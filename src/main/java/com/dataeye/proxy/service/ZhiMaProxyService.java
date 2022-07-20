package com.dataeye.proxy.service;

import com.alibaba.fastjson.JSON;
import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.bean.ProxyResponseDto;
import com.dataeye.proxy.config.BizConfig;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.starter.httpclient.HttpClientResponse;
import com.dataeye.starter.httpclient.ResponseEntityType;
import com.dataeye.starter.httpclient.common.CommonHttpClient;
import org.slf4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 从专门的ip拉取服务获取芝麻代理ip
 */
//@Service
public class ZhiMaProxyService implements InitializingBean {

    private static final Logger LOG = MyLogbackRollingFileUtil.getLogger("ZhiMaProxyService");
    private static final ScheduledExecutorService REFRESH_EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final AtomicInteger INDEX = new AtomicInteger(0);
    private static final int INDEX_LIMIT = Integer.MAX_VALUE / 2;
    private static List<ProxyCfg> CONTAINER = new ArrayList<>();
    @Resource
    private BizConfig bizConfig;
    @Resource
    private CommonHttpClient commonHttpClient;

    private static String getEth0Inet4InnerIp() {
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return null;
        }

        NetworkInterface netInterface;
        while (networkInterfaces.hasMoreElements()) {
            netInterface = networkInterfaces.nextElement();
            if (null != netInterface && "eth0".equals(netInterface.getName())) {
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void afterPropertiesSet() {
//        try {
//            refresh();
//            LOG.info("init success");
//        } catch (Exception e) {
//            LOG.error("init fail :  ", e);
//        } finally {
//            REFRESH_EXECUTOR.scheduleAtFixedRate(this::refresh, 0, 5, TimeUnit.SECONDS);
//        }
    }

    public void refresh() {
        String proxyUrl = bizConfig.getProxyUrl();
        String queryParams = bizConfig.getQueryParams();
        LOG.info("url={}, params={}", proxyUrl, queryParams);
        HttpClientResponse response = commonHttpClient.doPost(proxyUrl, new HashMap<>(), queryParams, ResponseEntityType.STRING_UTF8, null);
        if (!response.codeIs200() || Objects.isNull(response.getResponseContent())) {
            LOG.error("refresh proxy error,code:{}", response.getStatusCode());
            return;
        }

        String content = (String) response.getResponseContent();
        ProxyResponseDto responseDto = JSON.parseObject(content, ProxyResponseDto.class);
        if (!responseDto.getSuccess()) {
            LOG.error("response get error");
            return;
        }
        List<ProxyCfg> proxyCfgs = responseDto.getData().stream()
                .filter(data -> LocalDateTime.parse(data.getExpireTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).isAfter(LocalDateTime.now()))
                .map(data -> {
                    String userName = "";
                    String password = "";
                    if (Objects.nonNull(data.getChannel()) && data.getChannel() == 4) {
                        userName = "15779457681";
                        password = "gjb970312";
                    } else {
                        userName = data.getUserName();
                        password = data.getPassword();
                    }
                    return ProxyCfg.builder()
                            .host(data.getIp())
                            .port(data.getPort())
                            .userName(userName)
                            .password(password)
                            .expireTime(LocalDateTime.parse(data.getExpireTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .build();
                })
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(proxyCfgs)) {
            LOG.error("proxy ip is null");
            return;
        }
        if (INDEX.get() > INDEX_LIMIT) {
            INDEX.set(0);
        }
        CONTAINER = proxyCfgs;
    }

    public Optional<ProxyCfg> getOne() {
        if (CollectionUtils.isEmpty(CONTAINER)) {
            return Optional.empty();
        }
        int index = INDEX.getAndIncrement();
        ProxyCfg proxyCfg = CONTAINER.get(index % CONTAINER.size());
        return Optional.of(proxyCfg);
    }

}
