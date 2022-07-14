package com.dataeye.proxy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/6/2 9:15
 * @description 错误原因统计
 */
public class TestErrorCauseStatistics {

    public static void main(String[] args) throws IOException {
        String media_plan = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\media_plan.txt";
        String media_name = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\media_name.txt";
        HashMap<String, String> plan = get(media_plan);
        System.out.println("plan-->" + plan.size());
        HashMap<String, String> name = get(media_name);
        System.out.println("name-->" + name.size());
        HashMap<String, AtomicInteger> map = new HashMap<>();
        plan.forEach((k, v) -> {
            AtomicInteger value = new AtomicInteger(Integer.parseInt(v));
            if (name.containsKey(k)) {
                String str = name.getOrDefault(k, "");
                map.put(str, value);
            } else {
                map.put(k, value);
            }
        });
        System.out.println("map-->" + map.size());
        System.out.println(JSON.toJSONString(sortMapByDesc(map)));
    }

    public static LinkedHashMap<String, AtomicInteger> sortMapByDesc(Map<String, AtomicInteger> productNameCount) {
        if (productNameCount == null || productNameCount.isEmpty()) {
            return new LinkedHashMap<>(0);
        }
        return productNameCount.entrySet().stream()
                .sorted((entry1, entry2) -> entry2.getValue().get() - entry1.getValue().get())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }

    public static HashMap<String, String> get(String file) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        List<String> lines = FileUtils.readLines(new File(file), StandardCharsets.UTF_8);
        for (String line : lines) {
            String[] split = line.split("\\s");
            if (split.length == 2) {
                String media = split[0].trim();
                String count = split[1].trim();
                map.put(media, count);
            }
        }
        return map;
    }

    static JSONObject parseErrorList(String json) {
        JSONObject errorList = JSONObject.parseObject(json);
        int total = 0;
        int failConnect = 0;
        int timeout = 0;
        int readtimeout = 0;
        int connecttimeout = 0;
        int unexpectedEndOfStream = 0;
        int connectionReset = 0;
        int okhttpFail = 0;
        JSONObject other = new JSONObject();
        for (Map.Entry<String, Object> entry : errorList.entrySet()) {
            String reason = entry.getKey();
            int count = Integer.parseInt(entry.getValue().toString());
            total += count;
            if (reason.contains("Failed to connect to")) {
                failConnect = +count;
            } else if (reason.contains("timeout")) {
                timeout = +count;
            } else if (reason.contains("Read timed out")) {
                readtimeout = +count;
            } else if (reason.contains("connect timed out")) {
                connecttimeout = +count;
            } else if (reason.contains("unexpected end of stream on")) {
                unexpectedEndOfStream = +count;
            } else if (reason.contains("Connection reset")) {
                connectionReset = +count;
            }
//            else if (reason.contains("ok http send fail")) {
//                okhttpFail = +count;
//            }
            else {
                other.put(reason, count);
            }
        }

        JSONObject result = new JSONObject();
        result.put("Failed to connect to", failConnect);
        result.put("timeout", timeout);
        result.put("Read timed out", readtimeout);
        result.put("connect timed out", connecttimeout);
        result.put("unexpected end of stream on", unexpectedEndOfStream);
        result.put("Connection reset", connectionReset);
//        result.put("ok http send fail", okhttpFail);
        result.putAll(other);
        System.out.println("累计错误个数=" + total);
        return result;
    }

}
