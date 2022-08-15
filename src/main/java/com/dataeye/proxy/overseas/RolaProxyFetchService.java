package com.dataeye.proxy.overseas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RolaStaticIp;
import com.dataeye.proxy.bean.enums.RolaProxyInfo;
import com.dataeye.proxy.bean.enums.RolaProxyType;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.RolaConfig;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import com.dataeye.proxy.utils.TimeUtils;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/8/10 11:05
 * @description
 */
public abstract class RolaProxyFetchService {

    public static final String PASSWORD = "DataEye123$%^";
    public static final int ACCOUNT_NUM = 500;
    // "http://refresh.rola.info/refresh?user=dataeye_{0}&country={1}&state={2}&city={3}"
    public static final String CHINA_REFRESH_IP_URL = "http://refresh.rola.info/refresh?user={0}&country={1}";
    public static final String SGP_REFRESH_IP_URL = "http://refreshsg.rola.info/refresh?user={0}&country={1}";
    public static final String US_REFRESH_IP_URL = "http://refreshus2.rola.info/refresh?user={0}&country={1}";
    public static final Logger LOGGER = MyLogbackRollingFileUtil.getLogger("RolaProxyFetchService");

    /**
     * 国家代码
     */
    public static JSONObject COUNTRY_CODE = new JSONObject(0);

    /**
     * 初始化ip池
     */
    public abstract void initIpPool();

    /**
     * 构建 rola 动态住宅，动态机房ip池
     *
     * @param proxyType  代理类型
     * @param proxyInfos 代理配置
     * @param ipSelector ip选择器
     */
    public void buildIpPool(RolaProxyType proxyType, RolaProxyInfo[] proxyInfos, IpSelector ipSelector) {
        if (ipSelector == null) {
            LOGGER.error("buildIpPool error, ipSelector is null");
            return;
        }
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipSelector.getProxyIpPool();
        if (proxyIpPool == null) {
            LOGGER.error("buildIpPool error, proxyIpPool is null");
            return;
        }
        // 200年以后
        LocalDateTime expireTime = LocalDateTime.of(2222, 1, 1, 0, 0, 0, 0);
        String nowTime = TimeUtils.formatLocalDate(LocalDateTime.now());
        for (RolaProxyInfo proxyInfo : proxyInfos) {
            String host = proxyInfo.getHost();
            int port = proxyInfo.getPort();
            ConcurrentLinkedQueue<ProxyIp> ipPool = new ConcurrentLinkedQueue<>();
            ProxyIp proxyCfg = ProxyIp.builder()
                    .host(host)
                    .port(port)
                    .userName(proxyType.getAccountPrefix())
                    .password(proxyType.getPassword())
                    .createTime(nowTime)
                    .updateTime(nowTime)
                    .expireTime(expireTime)
                    .valid(new AtomicBoolean(true))
                    .useTimes(new AtomicLong(0))
                    .okTimes(new AtomicLong(0))
                    .errorTimes(new AtomicLong(0))
                    .rolaAccountNum(new AtomicLong(1))
                    .build();
            ipPool.offer(proxyCfg);

//            for (int i = 1; i <= proxyType.getAccountNum(); i++) {
//                ProxyIp proxyCfg = ProxyIp.builder()
//                        .host(host)
//                        .port(port)
//                        .userName(proxyType.getAccountPrefix() + i)
//                        .password(proxyType.getPassword())
//                        .createTime(nowTime)
//                        .updateTime(nowTime)
//                        .expireTime(expireTime)
//                        .valid(new AtomicBoolean(true))
//                        .useTimes(new AtomicLong(0))
//                        .okTimes(new AtomicLong(0))
//                        .errorTimes(new AtomicLong(0))
//                        .build();
//                ipPool.offer(proxyCfg);
//            }
            String key = proxyType.getTunnelAlias() + ":" + host + ":" + port;
            proxyIpPool.put(key, ipPool);
        }
    }

    /**
     * 构建 rola 静态机房ip池
     *
     * @param rolaConfig 代理类型
     * @param proxyType  代理配置
     * @param ipSelector ip选择器
     */
    public void buildStaticIpPool(RolaConfig rolaConfig, RolaProxyType proxyType, IpSelector ipSelector) {
        if (ipSelector == null) {
            LOGGER.error("buildIpPool error, ipSelector is null");
            return;
        }
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipSelector.getProxyIpPool();
        if (proxyIpPool == null) {
            LOGGER.error("buildStaticIpPool error, proxyIpPool is null");
            return;
        }
        List<RolaStaticIp> staticIpList = getStaticIpList(rolaConfig);
        ConcurrentLinkedQueue<ProxyIp> ipPool = new ConcurrentLinkedQueue<>();
        String nowTime = TimeUtils.formatLocalDate(LocalDateTime.now());
        for (RolaStaticIp r : staticIpList) {
            String port = r.getPort();
            // 部分端口是这样的  56787/57413
            if (port.contains("/")) {
                String[] split = port.split("/");
                port = split[0].trim();
            }
            ProxyIp proxyIp = ProxyIp.builder()
                    .host(r.getAddress())
                    .port(Integer.parseInt(port))
                    .userName(r.getOpUserName())
                    .password(r.getOpPassword())
                    .createTime(r.getCreateTime())
                    .updateTime(nowTime)
                    .expireTime(TimeUtils.str2LocalDate(r.getExpireTime()))
                    .valid(new AtomicBoolean(true))
                    .useTimes(new AtomicLong(0))
                    .okTimes(new AtomicLong(0))
                    .errorTimes(new AtomicLong(0))
                    .build();
            ipPool.offer(proxyIp);
        }
        String key = proxyType.getTunnelAlias();
        proxyIpPool.put(key, ipPool);
    }

    /**
     * 获取静态机房ip列表 目前已购买370个ip
     *
     * @param rolaConfig
     * @return
     */
    public List<RolaStaticIp> getStaticIpList(RolaConfig rolaConfig) {
        List<RolaStaticIp> result = new LinkedList<>();
        String getStaticIpListUrl = rolaConfig.getGetStaticIpListUrl();
        String resp = OkHttpTool.doGet(getStaticIpListUrl, null, false);
        if (!JSONObject.isValid(resp)) {
            LOGGER.error("获取静态ip列表失败, 非法json: {}", resp);
            return result;
        }
        JSONObject jsonObject = JSONObject.parseObject(resp);
        String code = jsonObject.getOrDefault("code", "").toString();
        if (!"0".equals(code)) {
            LOGGER.error("获取静态ip列表失败: {}", resp);
            return result;
        }
        Object data = jsonObject.get("data");
        if (data instanceof JSONObject) {
            JSONObject ipList = (JSONObject) data;
            JSONArray longtimeIpList = ipList.getJSONArray("longtime_ip_list");
            //int valid_longtime_ip_count = ipList.getIntValue("valid_longtime_ip_count");
            for (Object obj : longtimeIpList) {
                if (obj instanceof JSONObject) {
                    JSONObject element = (JSONObject) obj;
                    String json = element.toJSONString();
                    RolaStaticIp rolaStaticIp = JSONObject.parseObject(json, RolaStaticIp.class);
                    result.add(rolaStaticIp);
                }
            }
        }
        return result;
    }

    /**
     * 添加ip白名单
     *
     * @param rolaConfig 从netty http header中获取配置
     * @param ip         目标ip
     * @param remark     备注
     */
    public void addIpWhiteList(RolaConfig rolaConfig, String ip, String remark) {
        String realUrl = MessageFormat.format(rolaConfig.getAddIpWhiteListUrl(), rolaConfig.getToken(), remark, ip);
        String resp = OkHttpTool.doGet(realUrl, null, false);
        LOGGER.info("添加ip白名单结果: {}", resp);
    }

    /**
     * 获取订单key
     *
     * @param rolaConfig 从netty http header中获取配置
     * @return 订单id
     */
    public String getOrderKey(RolaConfig rolaConfig) {
        String key = "";
        String realUrl = MessageFormat.format(rolaConfig.getGetOrderKeyUrl(), rolaConfig.getToken());
        String resp = OkHttpTool.doGet(realUrl, null, false);
        if (!JSONObject.isValid(resp)) {
            LOGGER.error("获取订单key失败, 非法json: {}", resp);
            return key;
        }
        JSONObject jsonObject = JSONObject.parseObject(resp);
        String code = jsonObject.getOrDefault("code", "").toString();
        if (!"0".equals(code)) {
            LOGGER.error("获取订单key失败: {}", resp);
            return key;
        }
        key = jsonObject.getOrDefault("data", "").toString();
        LOGGER.info("获取订单key: {}", resp);
        return key;
    }

}
