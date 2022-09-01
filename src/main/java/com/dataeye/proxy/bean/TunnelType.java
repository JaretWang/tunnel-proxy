package com.dataeye.proxy.bean;

import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.selector.custom.ZhiMaCustomIpSelector;
import com.dataeye.proxy.selector.dailiyun.DaiLiYunExclusiveIpSelector;
import com.dataeye.proxy.selector.normal.ZhiMaOrdinaryIpSelector;
import com.dataeye.proxy.selector.oversea.OverseaIpSelector;
import com.dataeye.proxy.utils.SpringTool;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jaret
 * @date 2022/8/18 11:33
 * @description
 */
@AllArgsConstructor
public enum TunnelType {

    DOMESTIC(1, "普通芝麻ip", ZhiMaOrdinaryIpSelector.class),
    ZHIMA_DINGZHI(2, "芝麻定制ip", ZhiMaCustomIpSelector.class),
    OVERSEA(3, "海外rola", OverseaIpSelector.class),
    DAILIYUN_EXCLUSIVE(4, "代理云独享ip", DaiLiYunExclusiveIpSelector.class);

    @Getter
    int id;
    @Getter
    String description;
    @Getter
    Class<? extends CommonIpSelector> clazz;

    public static CommonIpSelector getIpSelector(SpringTool springTool, int id) {
        CommonIpSelector commonIpSelector = null;
        for (TunnelType type : TunnelType.values()) {
            if (type.getId() == id) {
                Class<? extends CommonIpSelector> clazz = type.getClazz();
                commonIpSelector = springTool.getBean(clazz);
                break;
            }
        }
        if (commonIpSelector == null) {
            throw new RuntimeException("获取ip选择器失败, 未知隧道id=" + id);
        }
        return commonIpSelector;
    }

}
