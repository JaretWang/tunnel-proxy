package com.dataeye.proxy.bean;

import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.selector.zhima.ZhiMaCustomIpSelector;
import com.dataeye.proxy.selector.dailiyun.DaiLiYun7DaysIpSelector;
import com.dataeye.proxy.selector.dailiyun.DaiLiYunExclusiveIpSelector;
import com.dataeye.proxy.selector.zhima.ZhiMaOrdinaryIpSelector;
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

    DOMESTIC(1, "普通芝麻套餐2", ZhiMaOrdinaryIpSelector.class),
    ZHIMA_DINGZHI(2, "芝麻定制ip", ZhiMaCustomIpSelector.class),
    OVERSEA(3, "海外rola", OverseaIpSelector.class),
    DAILIYUN_EXCLUSIVE(4, "代理云独享ip", DaiLiYunExclusiveIpSelector.class),
    DAILIYUN_7_DAYS(5, "代理云7天临时ip", DaiLiYun7DaysIpSelector.class);

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
