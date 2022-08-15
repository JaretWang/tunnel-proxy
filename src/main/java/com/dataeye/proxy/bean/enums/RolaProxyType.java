package com.dataeye.proxy.bean.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/8/11 14:46
 * @description
 */
@NoArgsConstructor
@AllArgsConstructor
public enum RolaProxyType {
    /**
     * 动态住宅ip
     */
    DYNAMIC_HOME(1,"oversea_dynamic_home", "dataeye_", "DataEye123$%^", 100000),
    /**
     * 动态机房ip
     */
    DYNAMIC_MACHINE_ROOM(2,"oversea_dynamic_machine_room", "dataeye_dc_", "DataEye123$%^", 100000),
    /**
     * 静态机房ip
     */
    STATIC_MACHINE_ROOM(3,"oversea_static_machine_room", "", "", 370),
    /**
     * 手机ip (账号注册了就有，没有买，按流量计费，非常贵，所以不用，暂时不管)
     */
    PHONE(4,"oversea_phone", "dataeye*4g_", "DataEye123$%^", 100000);
    @Getter
    int ipType;
    @Getter
    String tunnelAlias;
    @Getter
    String accountPrefix;
    @Getter
    String password;
    /**
     * 子账号序号（理论上是无限数量，但此处默认设置为10w）
     */
    @Getter
    int accountNum;
}