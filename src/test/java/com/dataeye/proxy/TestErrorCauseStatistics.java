package com.dataeye.proxy;

import com.alibaba.fastjson.JSONObject;

import java.util.Map;

/**
 * @author jaret
 * @date 2022/6/2 9:15
 * @description 错误原因统计
 */
public class TestErrorCauseStatistics {

    public static void main(String[] args) {
        String json = "{\n" +
                "    \"Failed to connect to /27.191.26.49:4241\":64,\n" +
                "    \"Failed to connect to /183.165.224.135:4245\":1,\n" +
                "    \"Failed to connect to /218.6.106.25:4213\":76,\n" +
                "    \"Failed to connect to /122.4.52.250:4264\":47,\n" +
                "    \"ok http send fail, code=403, reason=Not Allowed For api\\n\":2,\n" +
                "    \"Failed to connect to /123.189.209.98:4213\":3,\n" +
                "    \"Failed to connect to /42.177.141.211:4285\":5,\n" +
                "    \"Read timed out\":66,\n" +
                "    \"Failed to connect to /117.26.88.126:4237\":44,\n" +
                "    \"Failed to connect to /113.241.138.164:4213\":33,\n" +
                "    \"unexpected end of stream on http://pangolin.snssdk.com/...\":279,\n" +
                "    \"connect timed out\":4137,\n" +
                "    \"Failed to connect to /121.206.5.175:4232\":82,\n" +
                "    \"Failed to connect to /113.218.238.34:4231\":3,\n" +
                "    \"Failed to connect to /121.205.215.179:4213\":18,\n" +
                "    \"Failed to connect to /1.195.204.38:4267\":19,\n" +
                "    \"unexpected end of stream\":35,\n" +
                "    \"Failed to connect to /125.87.82.202:4278\":4,\n" +
                "    \"Failed to connect to /120.42.188.65:4212\":30,\n" +
                "    \"unexpected end of stream on http://api-access.pangolin-sdk-toutiao-b.com/...\":16,\n" +
                "    \"Connection reset\":171,\n" +
                "    \"Failed to connect to /175.0.112.122:4231\":3,\n" +
                "    \"timeout\":9663,\n" +
                "    \"Failed to connect to /110.85.169.11:4226\":29,\n" +
                "    \"Failed to connect to /222.77.214.195:4206\":16,\n" +
                "    \"unexpected end of stream on http://is.snssdk.com/...\":1,\n" +
                "    \"Failed to connect to /117.27.24.227:4225\":78,\n" +
                "    \"Failed to connect to /117.92.124.142:4213\":37,\n" +
                "    \"ok http send fail, code=502, reason=\\r\\n\\r\\n502 Bad Gateway\\r\\n\\r\\n502 Bad Gateway\\r\\nPowered by dsa-nginxtengine\\r\\n\\r\\n\\r\\n\":5,\n" +
                "    \"Failed to connect to /114.99.1.249:4225\":4,\n" +
                "    \"ok http send fail, code=401, reason=ip:39.108.108.148 Auth Failed\":2622,\n" +
                "    \"Failed to connect to /113.237.186.36:4213\":3,\n" +
                "    \"Failed to connect to /175.147.119.170:4285\":19,\n" +
                "    \"unexpected end of stream on http://api-access.pangolin-sdk-toutiao.com/...\":104,\n" +
                "    \"Failed to connect to /183.165.251.150:4254\":41,\n" +
                "    \"Failed to connect to /27.148.204.19:4213\":2,\n" +
                "    \"Failed to connect to /121.206.143.145:4213\":9,\n" +
                "    \"Failed to connect to /106.116.212.182:4241\":52\n" +
                "}";
        JSONObject result = parseErrorList(json);
        System.out.println("5 min内,错误列表=" + result.toJSONString());
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
