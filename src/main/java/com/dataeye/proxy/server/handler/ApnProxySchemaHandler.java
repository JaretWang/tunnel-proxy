package com.dataeye.proxy.server.handler;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.bean.RolaProxyConfig;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.enums.RolaProxyInfo;
import com.dataeye.proxy.bean.enums.RolaProxyType;
import com.dataeye.proxy.bean.enums.TunnelType;
import com.dataeye.proxy.cons.GlobalParams;
import com.dataeye.proxy.overseas.RolaProxyFetchService;
import com.dataeye.proxy.server.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.server.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.server.service.RequestDistributeService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.ReqMonitorUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxySchemaHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.schema";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final ApnHandlerParams apnHandlerParams;
    private final RequestMonitor requestMonitor = new RequestMonitor();
    private final AtomicBoolean isGetRolaAccount = new AtomicBoolean(true);

    public ApnProxySchemaHandler(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("schema channelActive");
        if (apnHandlerParams.getTunnelInstance().getType() == TunnelType.oversea.seq) {
            return;
        }
        getZhiMaIp(ctx);
        IpMonitorUtils.invoke(true, requestMonitor, true, HANDLER_NAME);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        logger.debug("schema channelRead");
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest httpRequest = (FullHttpRequest) msg;
            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            } else {
                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
            }
            // 海外隧道获取请求头配置参数
            if (apnHandlerParams.getTunnelInstance().getType() == TunnelType.oversea.seq && isGetRolaAccount.get()) {
                getRolaAccount(ctx, httpRequest);
                IpMonitorUtils.invoke(true, requestMonitor, true, HANDLER_NAME);
                isGetRolaAccount.set(false);
            }
            apnHandlerParams.getRequestMonitor().setRequestType(httpRequest.method().name());
            apnHandlerParams.getRequestMonitor().setTargetAddr(httpRequest.uri());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("schema exceptionCaught: {}", cause.getMessage());
        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), HANDLER_NAME, cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }

    /**
     * 获取芝麻ip
     *
     * @param ctx
     * @throws InterruptedException
     */
    void getZhiMaIp(ChannelHandlerContext ctx) throws InterruptedException {
        // 随时更新 tunnelInstance
        TunnelInitService tunnelInitService = apnHandlerParams.getTunnelInitService();
        if (Objects.nonNull(tunnelInitService)) {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            if (Objects.nonNull(defaultTunnel)) {
                apnHandlerParams.setTunnelInstance(defaultTunnel);
            }
        }
        // 分配ip
        ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
        RequestDistributeService requestDistributeService = apnHandlerParams.getRequestDistributeService();
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
        if (Objects.isNull(apnProxyRemote)) {
            requestDistributeService.handleProxyIpIsEmpty(ctx);
        }
        logger.debug("分配ip结果：{}", apnProxyRemote);
        ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);

        // ip, 请求监控
        requestMonitor.setTunnelName(tunnelInstance.getAlias());
        requestMonitor.setBegin(System.currentTimeMillis());
        requestMonitor.setProxyAddr(apnProxyRemote.getIpAddr());
        requestMonitor.setExpireTime(apnProxyRemote.getExpireTime());
        requestMonitor.setSuccess(true);
        apnHandlerParams.setRequestMonitor(requestMonitor);
    }

    /**
     * 获取海外rola账号
     */
    void getRolaAccount(ChannelHandlerContext ctx, FullHttpRequest httpRequest) {
        RolaProxyConfig rolaProxyConfig = getRolaProxyConfigFromHeaders(httpRequest);
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = apnHandlerParams.getIpSelector().getProxyIpPool();
        //String alias = apnHandlerParams.getTunnelInstance().getAlias();
        String alias = "";
        // alias
        for (RolaProxyType value : RolaProxyType.values()) {
            if (value.getIpType() == rolaProxyConfig.getIpType()) {
                alias = value.getTunnelAlias();
                break;
            }
        }
        if (StringUtils.isBlank(alias)) {
            alias = RolaProxyType.DYNAMIC_HOME.getTunnelAlias();
            logger.warn("alias is empty, so set default value [{}]", alias);
        }

        // host
        String serverLocation = rolaProxyConfig.getServerLocation();
        boolean status = RolaProxyFetchService.COUNTRY_CODE.containsValue(serverLocation.trim());
        if (!status) {
            serverLocation = "us";
            logger.error("非法国家代号 {}, 设置默认值为: {}", serverLocation, serverLocation);
        }
        String protocol = rolaProxyConfig.getProtocol() == 1 ? RolaProxyInfo.US_SOCKS5.getProtocol() : RolaProxyInfo.US_HTTP.getProtocol();
        RolaProxyInfo rolaProxyInfo = null;
        for (RolaProxyInfo value : RolaProxyInfo.values()) {
            if (value.getServerLocationCode().equalsIgnoreCase(serverLocation)
                    && value.getProtocol().equalsIgnoreCase(protocol)) {
                rolaProxyInfo = value;
            }
        }
        if (rolaProxyInfo == null) {
            rolaProxyInfo = RolaProxyInfo.US_HTTP;
            logger.warn("rolaProxyInfo is null, so set default value [{}]", rolaProxyInfo.toString());
        }

        // get ip
        String key;
        if (rolaProxyConfig.getIpType() == RolaProxyType.STATIC_MACHINE_ROOM.getIpType()) {
            key = alias;
        } else {
            key = alias + "_" + rolaProxyInfo.getHost();
        }
        ConcurrentLinkedQueue<ProxyIp> proxyIps = proxyIpPool.get(key);
        if (proxyIps == null) {
            logger.error("ip queue is null, key={}", key);
            return;
        }
        ProxyIp poll = proxyIps.poll();
        if (poll == null) {
            logger.error("proxyIp from queue is null, key={}", key);
            return;
        }
        ApnProxyRemote apnProxyRemote;
        // 静态ip
        if (rolaProxyConfig.getIpType() == RolaProxyType.STATIC_MACHINE_ROOM.getIpType()) {
            apnProxyRemote = apnHandlerParams.getApnProxyRemoteChooser().adapte(poll);
        } else {
            apnProxyRemote = apnHandlerParams.getApnProxyRemoteChooser().adapteRolaDynamicIp(poll);
        }
        ctx.channel().attr(GlobalParams.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);
        logger.info("请求头配置={}, rola账号={}", rolaProxyConfig, apnProxyRemote);
    }

    /**
     * 海外隧道获取请求头配置参数
     */
    RolaProxyConfig getRolaProxyConfigFromHeaders(FullHttpRequest httpRequest) {
        RolaProxyConfig rolaProxyConfig = new RolaProxyConfig();
        if (httpRequest == null) {
            logger.error("httpRequest is null");
            return rolaProxyConfig;
        }
        int type = apnHandlerParams.getTunnelInstance().getType();
        if (type == 0) {
            logger.error("获取隧道类型失败");
            return rolaProxyConfig;
        }
        int seq = TunnelType.oversea.seq;
        if (type == seq) {
            RolaProxyFetchService rolaProxyFetchService = apnHandlerParams.getRolaProxyFetchService();
            if (Objects.isNull(rolaProxyFetchService)) {
                logger.error("get oversea tunnel params error, rolaProxyFetchService is nul");
                return rolaProxyConfig;
            }
            HttpHeaders headers = httpRequest.headers();
            if (headers.isEmpty()) {
                logger.warn("headers is empty");
                return rolaProxyConfig;
            }
            for (Map.Entry<String, String> header : headers) {
                String key = header.getKey();
                if (key.equalsIgnoreCase("proxy-config")) {
                    String value = header.getValue();
                    if (StringUtils.isNotBlank(value)) {
                        // 解析base64
                        byte[] decode = Base64.getDecoder().decode(value);
                        if (decode != null) {
                            String json = new String(decode);
                            if (JSON.isValid(json)) {
                                rolaProxyConfig = JSONObject.parseObject(json, RolaProxyConfig.class);
                            }
                        }
                    }
                }
            }
        }
        return rolaProxyConfig;
    }

}
