package com.dataeye.proxy.component;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyRemote;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.Global;
import com.dataeye.proxy.utils.Md5Utils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author jaret
 * @date 2022/3/18 15:21
 * @description 代理ip选择器
 */
@Data
@Slf4j
@Component
public class IpSelector {

    @Resource
    private ProxyServerConfig proxyServerConfig;

    private static final String CRON = "0 0/5 * * * ?";
    public static final ConcurrentMap<String, String> PROXY_IP_PORT = new ConcurrentHashMap<>(4);
    public static List<String> PROXY_IP_LIST;

    /**
     * 随机获取代理ip
     *
     * @return
     */
    public static String randomGetProxyIp() {
//            String hostPort = proxyIpList.get(new Random().nextInt(proxyIpList.size() -1)+1);
        // todo 应该用负载均衡算法，使用这个代理ip
        if (ObjectUtils.isEmpty(PROXY_IP_LIST)) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.info("获取代理ip时，休眠中断异常");
            }
        }
        int index = (int) (Math.random() * (PROXY_IP_LIST.size()));
        String hostPort = PROXY_IP_LIST.get(index);
        if (StringUtils.isBlank(hostPort)) {
            throw new RuntimeException("随机获取的代理ip和port为空");
        }
        return hostPort;
    }

    /**
     * 定时获取代理列表
     *
     * @return
     */
    @Scheduled(cron = CRON)
    @PostConstruct
    public void getProxyListBySchedule() throws IOException {
        String directIpAccessLink = proxyServerConfig.getDirectIpAccessLink();
        PROXY_IP_LIST = getRandomProxyIpList(directIpAccessLink);
        log.info("定时更新(每5分钟)代理 ip 列表: {}", PROXY_IP_LIST.toString());
    }

    /**
     * 获取单个代理ip
     *
     * @param originalHost 原始ip
     * @param originalPort 原始端口
     * @return 代理ip
     * @throws IOException
     */
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
     * @return 随机代理ip
     */
    public String getRandomProxyIp() throws IOException {
        String proxyIp = "";
        String url = proxyServerConfig.getDirectIpAccessLink();
        HttpResponse httpResponse = Request.Get(url).execute().returnResponse();
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

    /**
     * 随机获取代理ip，有效期：5-25分钟
     *
     * @param proxyUrl 代理商地址
     * @return 随机代理ip组
     * @throws IOException
     */
    public List<String> getRandomProxyIpList(String proxyUrl) throws IOException {
        List<String> proxyList = new ArrayList<>(2);
        OkHttpClient client = new OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().get().url(proxyUrl).build();
        Response response = client.newCall(request).execute();
        String body = response.body().string();
        if (StringUtils.isNotBlank(body) && body.contains(":")) {
            String lineSeparator = System.lineSeparator();
            String[] split = body.split(lineSeparator);
            proxyList.addAll(Arrays.asList(split));
        }
        return proxyList;
    }

}
