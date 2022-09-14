package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.DaiLiYunExclusiveIpResp;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.NetUtils;
import com.dataeye.proxy.utils.OkHttpTool;
import com.dataeye.proxy.utils.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/8/30 10:16
 * @description 代理云独享ip拉取服务
 */
@Service
public class DaiLiYunExclusiveFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("DaiLiYunExclusiveServiceImpl");
    private static String account;
    static {
        String innerIp = NetUtils.getEth0Inet4InnerIp();
        // adx-proxy-012  172.18.211.184  39.108.4.144
        if ("172.18.211.184".equalsIgnoreCase(innerIp)) {
            account = "17381585447";
        } else {
            account = "18922868909";
        }
    }

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws Exception {
        List<ProxyIp> ipList = getIpList(1);
        if (CollectionUtils.isEmpty(ipList)) {
            return null;
        }
        return ipList.get(0);
    }

    /**
     * 构建拉取ip的url
     *
     * @param count ip数量
     * @return
     */
    String buildFetchUrl(int count) {
        // http://17381585447.user.xiecaiyun.com/api/proxies?action=getText&key=NP87731636&count=1&word=&rand=true&norepeat=false&detail=false&ltime=0
        // http://18922868909.user.xiecaiyun.com/api/proxies?action=getJSON&key=NP7A76CFEF&count=1&word=&rand=false&norepeat=true&detail=true&ltime=10
        String url2 = "http://{0}.user.xiecaiyun.com/api/proxies?action={1}&key={2}&count={3}&word={4}&rand={5}&norepeat={6}&detail={7}&ltime={8}";
        return MessageFormat.format(url2, account, "getJSON", "NP7A76CFEF", String.valueOf(count), "", "false", "false", "true", "10");
    }

    public List<ProxyIp> getIpList(int count) {
        if (count <= 0) {
            return null;
        }
        String targetUrl = buildFetchUrl(count);
        logger.info("获取ip的url: {}", targetUrl);

        // send
        String resp = OkHttpTool.doGet(targetUrl);
        if (!JSON.isValid(resp)) {
            logger.error("响应内容不是json");
            return null;
        }
        JSONObject respObj = JSONObject.parseObject(resp);
        boolean success = respObj.getBooleanValue("success");
        if (!success) {
            logger.error("获取ip失败, 响应内容{}", resp);
            return null;
        }
        JSONArray result = respObj.getJSONArray("result");
        if (result == null || result.isEmpty()) {
            logger.error("ip列表为空, 响应内容={}", resp);
            return null;
        }
        List<ProxyIp> ipList = new LinkedList<>();
        for (Object obj : result) {
            if (obj instanceof JSONObject) {
                JSONObject element = (JSONObject) obj;
                DaiLiYunExclusiveIpResp ipResp = JSONObject.parseObject(element.toJSONString(), DaiLiYunExclusiveIpResp.class);
                String ip = ipResp.getIp();
                int port = ipResp.getPort();
                String time = TimeUtils.formatLocalDate(TimeUtils.second2LocalDateTime(ipResp.getFtime()));
                LocalDateTime expireTime = TimeUtils.second2LocalDateTime(ipResp.getLtime());
                ProxyIp proxyIp = ProxyIp.builder()
                        .host(ip)
                        .port(port)
                        .expireTime(expireTime)
                        .userName(account)
                        .password(account)
                        .valid(new AtomicBoolean(true))
                        .useTimes(new AtomicLong(0))
                        .okTimes(new AtomicLong(0))
                        .errorTimes(new AtomicLong(0))
                        .createTime(time)
                        .updateTime(time)
                        .build();
                ipList.add(proxyIp);
            }
        }
        return ipList;
    }

    String buildUrl(String url, Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            StringBuilder stringBuilder = new StringBuilder();
            params.keySet().forEach(res -> {
                if (StringUtils.isNotBlank(stringBuilder)) {
                    stringBuilder.append("&");
                } else {
                    stringBuilder.append("?");
                }
                try {
                    stringBuilder.append(String.format("%s=%s", res, URLEncoder.encode(params.get(res), "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            });
            // 拼接参数
            return url + stringBuilder;
        }
        return url;
    }

}
