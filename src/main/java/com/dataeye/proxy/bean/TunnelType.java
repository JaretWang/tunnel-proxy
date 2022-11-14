package com.dataeye.proxy.bean;

import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.selector.vps.VpsIpSelector;
import com.dataeye.proxy.selector.zhima.ZhiMaCustomIpSelector;
import com.dataeye.proxy.selector.dailiyun.DaiLiYunExclusiveIpSelector;
import com.dataeye.proxy.selector.zhima.ZhiMaOrdinaryIpSelector;
import com.dataeye.proxy.selector.oversea.OverseaIpSelector;
import com.dataeye.proxy.utils.SpringTool;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/8/18 11:33
 * @description 隧道类型 1芝麻普通 2芝麻定制 3海外rola 4代理云定制 5自建vps
 */
@AllArgsConstructor
public enum TunnelType {

    /**
     * 芝麻普通套餐ip选择器
     */
    ZHIMA(1, "普通芝麻套餐2", ZhiMaOrdinaryIpSelector.class),
    /**
     * 芝麻定制ip选择器
     */
    ZHIMA_DINGZHI(2, "芝麻定制ip", ZhiMaCustomIpSelector.class),
    /**
     * 海外rola ip选择器
     */
    OVERSEA(3, "海外rola", OverseaIpSelector.class),
    /**
     * 代理云独享ip选择器
     */
    DAILIYUN_EXCLUSIVE(4, "代理云独享ip", DaiLiYunExclusiveIpSelector.class),
    /**
     * vps隧道ip选择器
     */
    VPS(5, "vps隧道", VpsIpSelector.class);

    @Getter
    int id;
    @Getter
    String description;
    @Getter
    Class<? extends CommonIpSelector> clazz;
    private static final Map<Integer, Class<? extends CommonIpSelector>> TUNNEL_TYPE = Arrays.stream(TunnelType.values()).collect(Collectors.toMap(TunnelType::getId, TunnelType::getClazz, (e1, e2)->e1));

    public static CommonIpSelector getIpSelector(SpringTool springTool, int id) {
        Class<? extends CommonIpSelector> clazz = TUNNEL_TYPE.get(id);
        if (clazz == null) {
            throw new RuntimeException("clazz is null, id: " + id);
        }
        CommonIpSelector commonIpSelector = springTool.getBean(clazz);
        if (commonIpSelector == null) {
            throw new RuntimeException("get ip selector error, unknown id: " + id);
        }
        return commonIpSelector;
    }

}
