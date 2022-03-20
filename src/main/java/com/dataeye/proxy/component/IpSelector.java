package com.dataeye.proxy.component;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyRemote;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.Global;
import com.dataeye.proxy.utils.Md5Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/3/18 15:21
 * @description 代理ip选择器
 */
@Slf4j
@Component
public class IpSelector {

    @Resource
    private ProxyServerConfig proxyServerConfig;

    public String getProxyIp(String originalHost, int originalPort) throws IOException {
        String connect = originalHost.trim() + ":" + originalPort;
        String uuid = Md5Utils.md5Encode(connect);
        String proxyIp;
        if (Global.PROXY_IP_MAPPING.containsKey(uuid)) {
            // 存在代理ip，就复用原来的ip
            proxyIp = Global.PROXY_IP_MAPPING.get(uuid);
        } else {
            // 随机选择一个代理ip
            proxyIp = getRandomProxyIp();
            Global.PROXY_IP_MAPPING.put(uuid, proxyIp);
        }
        return proxyIp;
    }

    /**
     * 构建真实ip和代理ip的映射关系,并获取代理ip
     *
     * @param originalHost 原始ip
     * @param originalPort 原始端口
     * @return 代理ip
     */
    public ProxyRemote getProxyAddress(String originalHost, int originalPort) throws IOException {
        String connect = originalHost.trim() + ":" + originalPort;
        String uuid = Md5Utils.md5Encode(connect);
        String proxyIp;
        if (Global.PROXY_IP_MAPPING.containsKey(uuid)) {
            // 存在代理ip，就复用原来的ip
            proxyIp = Global.PROXY_IP_MAPPING.get(uuid);
        } else {
            // 随机选择一个代理ip
            proxyIp = getRandomProxyIp();
            Global.PROXY_IP_MAPPING.put(uuid, proxyIp);
        }
        return ProxyRemote.builder()
                .remoteHost(proxyIp)
                .remotePort(originalPort)
                .build();
    }

    /**
     * 随机获取代理ip
     *
     * @return
     */
    public String getRandomProxyIp() throws IOException {
        String proxyIp = "";
//        String url = proxyServerConfig.getDirectIpAccessLink();
        //27.38.143.94
        String url = proxyServerConfig.getTunnelIpAccessLink();
        HttpResponse httpResponse = Request.Get(url)
                .execute()
                .returnResponse();
        HttpEntity entity = httpResponse.getEntity();
        String content = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            log.error("请求第三方ip代理商出现异常，错误码：{}", statusCode);
            return proxyIp;
        }
        log.info("返回结果：{}", content);
        // {"code":113,"data":[],"msg":"请添加白名单163.125.61.145","success":false}
        JSONObject jsonObject = JSONObject.parseObject(content);
        String codeStr = jsonObject.getOrDefault("code", "").toString();
        if (StringUtils.isNotBlank(codeStr)) {
            int code = Integer.parseInt(codeStr);
            if (code != 200) {
                String errorPharse = jsonObject.getOrDefault("msg", "").toString();
                log.error("获取代理ip失败，原因: {}", errorPharse);
                return proxyIp;
            }
        } else {
            log.error("第三方代理商返回的code不存在");
        }

        // get proxy ip
        Object data = jsonObject.getOrDefault("data", "");
        if (Objects.nonNull(data) && data instanceof JSONArray) {
            JSONArray array = (JSONArray) data;
            if (array.isEmpty()) {
                return proxyIp;
            }
            proxyIp = array.get(0).toString();
        } else {
            log.error("获取代理ip失败，原因：不存在data字段，或data类型不是一个数组");
        }
        return proxyIp;
    }

}
