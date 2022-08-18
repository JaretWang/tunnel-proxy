package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * @author jaret
 * @date 2022/8/18 11:33
 * @description
 */
@AllArgsConstructor
public enum TunnelType {

    DOMESTIC(1,"国内隧道"),
    HIGH_QUALITY(2,"芝麻高质量隧道"),
    OVERSEA(3,"海外隧道");

    @Getter
    int id;
    @Getter
    String description;

}
