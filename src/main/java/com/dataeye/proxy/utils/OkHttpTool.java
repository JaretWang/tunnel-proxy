package com.dataeye.proxy.utils;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/4/20 14:50
 * @description
 */
public class OkHttpTool {

    private static final OkHttpClient OKHTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();
    private static final MediaType MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    public static void main(String[] args) {
        String url = "https://mi.gdt.qq.com/gdt_mview.fcg?posid=5000155655915649&ext=%7B%22req%22%3A%7B%22deep_link_version%22%3A1%2C%22tmpallpt%22%3Atrue%2C%22conn%22%3A1%2C%22max_duration%22%3A181%2C%22device_ext%22%3A%7B%22qaid_info%22%3A%7B%22tz%22%3A%2228800%22%2C%22cy%22%3A%22CN%22%2C%22hd%22%3A%22255865737216%22%2C%22ma%22%3A%22iPhone12%2C5%22%2C%22dm%22%3A%22D431AP%22%2C%22pm%22%3A%223930734592%22%2C%22la%22%3A%22zh-Hans-CN%22%2C%22dn_h%22%3A%22867e57bd062c7169995dc03cc0541c19%22%2C%22st%22%3A%221650131933%22%2C%22sut%22%3A%221650131944.218369%22%7D%2C%22attri_info%22%3A%7B%22iv%22%3A%225B86775C-462F-4CB5-977D-AFFA2322BB13%22%7D%7D%2C%22carrier%22%3A2%2C%22m5%22%3A%2200000000-0000-0000-0000-000000000000%22%2C%22c_dpi%22%3A320%2C%22c_ori%22%3A0%2C%22sdk_st%22%3A1%2C%22c_w%22%3A1242%2C%22wx_installed%22%3Atrue%2C%22placement_type%22%3A9%2C%22s_hd%22%3A1%2C%22support_container%22%3Atrue%2C%22support_features%22%3A636%2C%22sdk_src%22%3A%22%22%2C%22c_h%22%3A2688%2C%22c_pkgname%22%3A%22com.qiushibaike.qiushibaike%22%2C%22prld%22%3A0%2C%22support_c2s%22%3A2%2C%22hostver%22%3A%2211.19.2%22%2C%22sdkver%22%3A%224.13.20%22%2C%22c_isjailbroken%22%3Afalse%2C%22m_ch%22%3A14%2C%22lng%22%3A0%2C%22sdk_cnl%22%3A1%2C%22scs%22%3A%220001fbb8b1ab%22%2C%22muidtype%22%3A2%2C%22appid%22%3A%221107835449%22%2C%22ex_exp_info%22%3A%7B%7D%2C%22lat%22%3A0%2C%22c_sdfree%22%3A163331833856%2C%22render_type%22%3A1%2C%22c_device%22%3A%22iPhone12%2C5%22%2C%22support_component%22%3A%221%2C2%2C3%22%2C%22c_osver%22%3A%2215.4.1%22%2C%22c_devicetype%22%3A1%2C%22opensdk_ver%22%3A%221.9.2%22%2C%22muid%22%3A%229f89c84a559f573636a47ff8daed0d34%22%2C%22c_os%22%3A%22ios%22%7D%7D&count=3&adposcount=1&datatype=2&support_https=1";
        int len = url.getBytes().length / 1024;
        System.out.println("优量广告测试，请求大小：" + len + " kb");
        String data = doGet(url, Collections.emptyMap(), true);
        int dataLen = data.getBytes().length / 1024;
        System.out.println("优量广告测试，响应大小：" + dataLen + " kb");
    }

    /**
     * OKHTTP  POST 请求
     *
     * @param reqUrl     地址
     * @param header     头部信息
     * @param jsonObject 请求参数
     * @return
     */
    public static String doPost(String reqUrl, Map<String, String> header, JSONObject jsonObject) {
        RequestBody body = RequestBody.create(MEDIA_TYPE, jsonObject.toString());
        // 添加头部信息
        Request.Builder builder = new Request.Builder().url(reqUrl);
        if (header != null && !header.isEmpty()) {
            header.entrySet().forEach(res -> {
                builder.addHeader(res.getKey(), res.getValue());
            });
        }
        // 发送请求
        Request request = builder.post(body).build();
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("HTTP POST同步请求失败 URL:" + reqUrl, e);
        }
    }

    /**
     * OKHTTP GET 请求
     *
     * @param reqUrl 地址
     * @param params 参数
     * @param flag   地址中带有参数为 TRUE 没有参数 FALSE
     * @return
     */
    public static String doGet(String reqUrl, Map<String, String> params, boolean flag) {
        StringBuilder stringBuilder = new StringBuilder();
        //处理参数
        if (params != null && !params.isEmpty()) {
            params.keySet().forEach(res -> {
                if (StringUtils.isNotBlank(stringBuilder) || flag) {
                    stringBuilder.append("&");
                } else {
                    stringBuilder.append("?");
                }
                try {
                    stringBuilder.append(String.format("%s=%s", res, URLEncoder.encode(params.get(res), "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            });
        }
        // 拼接参数
        String requestUrl = reqUrl + stringBuilder;
        // 发送请求
        Request request = new Request.Builder()
                .url(requestUrl)
                .build();
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET同步请求失败 URL:" + reqUrl, e);
        }
    }
}
