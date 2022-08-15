package com.dataeye.proxy.bean.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum TunnelType {
    /**
     * 国内隧道
     */
    domestic(1, "国内隧道"),
    /**
     * 海外隧道
     */
    oversea(2, "海外隧道");

    public int seq;
    public String description;
}