package com.dataeye.proxy.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/7/21 15:01
 * @description
 */
public class MapUtils {

    /**
     * map排序
     *
     * @param map  源数据
     * @param desc 是否降序
     * @return
     */
    public static LinkedHashMap<String, AtomicInteger> sort(ConcurrentHashMap<String, AtomicInteger> map, boolean desc) {
        return map.entrySet().stream()
                .sorted((entry1, entry2) -> {
                    if (desc) {
                        return entry2.getValue().get() - entry1.getValue().get();
                    } else {
                        return entry1.getValue().get() - entry2.getValue().get();
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }


}
