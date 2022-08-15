package com.dataeye.proxy.overseas;

import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.enums.TunnelType;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.RolaConfig;
import com.dataeye.proxy.overseas.impl.DynamicHomeIpServiceImpl;
import com.dataeye.proxy.overseas.impl.DynamicMachineRoomIpServiceImpl;
import com.dataeye.proxy.overseas.impl.StaticMachineRoomIpServiceImpl;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/8/12 17:31
 * @description
 */
@Service
public class RolaInitService {

    public static final Logger LOGGER = MyLogbackRollingFileUtil.getLogger("RolaInitService");

    @Autowired
    RolaConfig rolaConfig;
    @Autowired
    DynamicHomeIpServiceImpl dhi;
    @Autowired
    DynamicMachineRoomIpServiceImpl dmri;
    @Autowired
    StaticMachineRoomIpServiceImpl smri;
    @Autowired
    TunnelInitService tunnelInitService;
    @Autowired
    ProxyServerConfig proxyServerConfig;

    @PostConstruct
    void init() throws IOException {
        if (!proxyServerConfig.isEnable() || tunnelInitService.getDefaultTunnel().getType() == TunnelType.domestic.seq) {
            return;
        }
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        LOGGER.info("海外隧道 - 添加白名单");
        dhi.addIpWhiteList(rolaConfig, defaultTunnel.getOutIp(), "海外隧道-" + defaultTunnel.getAlias());
        LOGGER.info("海外隧道 - 初始化国家代号");
        initCountryCode();
        LOGGER.info("海外隧道 - 初始化动态住宅ip池");
        dhi.initIpPool();
        LOGGER.info("海外隧道 - 初始化动态机房ip池");
        dmri.initIpPool();
        LOGGER.info("海外隧道 - 初始化静态态机房ip池");
        smri.initIpPool();
    }

    public void initCountryCode() throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("rola/country_code.json");
        assert inputStream != null;
        String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (Objects.nonNull(jsonObject) && !jsonObject.isEmpty()) {
            RolaProxyFetchService.COUNTRY_CODE = jsonObject;
        }
        LOGGER.info("init rola country code={}", RolaProxyFetchService.COUNTRY_CODE.toJSONString());
    }

}
