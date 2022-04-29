package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.YiNiuCloudConfig;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author jaret
 * @date 2022/4/1 19:30
 * @description 亿牛云ip获取
 */
@Service
public class YiniuCloudFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("YiniuCloudFetchServiceImpl");

    @Resource
    YiNiuCloudConfig yiNiuCloudConfig;

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) {
        String ipFectchUrl = yiNiuCloudConfig.getIpFectchUrl();
        String json = OkHttpTool.doGet(ipFectchUrl, Collections.emptyMap(), false);
        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("status");
        if (success) {
            JSONArray data = jsonObject.getJSONArray("proxy");
            if (data.size() > 0) {
                String ipItem = data.get(0).toString();
                JSONObject ipElement = JSONObject.parseObject(ipItem);
                String ip = ipElement.getString("ip");
                int port = ipElement.getIntValue("port");
                // 临时测试设置时间
                LocalDateTime localDateTime = LocalDateTime.of(2023, 1, 1, 1, 1, 1);
                return ProxyIp.builder()
                        .host(ip)
                        .port(port)
                        .expireTime(localDateTime)
                        .userName("")
                        .password("")
                        .build();
            }
        } else {
            throw new RuntimeException("从亿牛云拉取ip失败，原因：" + json);
        }
        return null;
    }

    /**
     * 获取多个ip
     */
    public List<ProxyIp> getMultiple(int num) {
        List<ProxyIp> result = new LinkedList<>();
        String ipFectchUrl = yiNiuCloudConfig.getIpFectchWithCustomQuantity();
        String url = ipFectchUrl + "&count="+num;
        String json = OkHttpTool.doGet(url, Collections.emptyMap(), false);
        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("status");
        if (success) {
            JSONArray data = jsonObject.getJSONArray("proxy");
            if (data.size() > 0) {
                for (Object datum : data) {
                    JSONObject ipElement = JSONObject.parseObject(datum.toString());
                    String ip = ipElement.getString("ip");
                    int port = ipElement.getIntValue("port");
                    // 临时测试
                    LocalDateTime localDateTime = LocalDateTime.of(2023, 1, 1, 1, 1, 1);
                    ProxyIp proxyCfg = ProxyIp.builder()
                            .host(ip)
                            .port(port)
                            .expireTime(localDateTime)
                            .userName("")
                            .password("")
                            .build();
                    result.add(proxyCfg);
                }
            }
            return result;
        } else {
            throw new RuntimeException("从亿牛云拉取ip失败，原因：" + json);
        }
    }

    public List<ApnProxyRemote> getMany(int num){
        List<ApnProxyRemote> result = new LinkedList<>();
        List<ProxyIp> multiple = getMultiple(num);
        for (ProxyIp one : multiple) {
            ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
            apPlainRemote.setAppleyRemoteRule(true);
            apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
            apPlainRemote.setRemoteHost(one.getHost());
            apPlainRemote.setRemotePort(one.getPort());
            apPlainRemote.setProxyUserName(one.getUserName());
            apPlainRemote.setProxyPassword(one.getPassword());
            apPlainRemote.setExpireTime(one.getExpireTime());
            result.add(apPlainRemote);
        }
        return result;
    }


}
