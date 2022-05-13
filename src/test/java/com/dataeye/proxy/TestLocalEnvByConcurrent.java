package com.dataeye.proxy;

import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.OkHttpTool;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
public class TestLocalEnvByConcurrent {

    private static final String pageUrl = "https://www.baidu.com/s?wd=%E7%BC%85%E6%80%80%E5%9C%A8%E5%8C%97%E7%BA%A6%E8%BD%B0%E7%82%B8%E4%B8%AD%E7%89%BA%E7%89%B2%E7%9A%843%E5%90%8D%E7%83%88%E5%A3%AB&sa=fyb_n_homepage&rsv_dl=fyb_n_homepage&from=super&cl=3&tn=baidutop10&fr=top1000&rsv_idx=2&hisfilter=1";
//    private static final String pageUrl = "http://mi.gdt.qq.com/gdt_mview.fcg?ext=%7B%22req%22:%20%7B%22deep_link_version%22:%201,%20%22tmpallpt%22:%20true,%20%22conn%22:%201,%20%22max_duration%22:%20181,%20%22device_ext%22:%20%7B%22qaid_info%22:%20%7B%22dn_h%22:%20%229aa40912650332f225b3fcd00de3df78%22%7D,%20%22attri_info%22:%20%7B%22iv%22:%20%2298ebc299-86de-4a5c-b9fd-9fe88ce690d8%22%7D%7D,%20%22carrier%22:%202,%20%22m5%22:%20%22052D9A45-F11B-44F5-9718-8ADAB3AB6518%22,%20%22c_dpi%22:%20320,%20%22c_ori%22:%200,%20%22sdk_st%22:%201,%20%22c_w%22:%201242,%20%22wx_installed%22:%20true,%20%22placement_type%22:%209,%20%22s_hd%22:%201,%20%22support_container%22:%20true,%20%22support_features%22:%20636,%20%22sdk_src%22:%20%22%22,%20%22c_h%22:%202688,%20%22c_pkgname%22:%20%22com.chenhuainan.jincunba%22,%20%22prld%22:%200,%20%22support_c2s%22:%202,%20%22hostver%22:%20%2211.19.2%22,%20%22sdkver%22:%20%224.13.20%22,%20%22c_isjailbroken%22:%20false,%20%22m_ch%22:%2014,%20%22lng%22:%200,%20%22sdk_cnl%22:%201,%20%22scs%22:%20%2200019e4b66f5%22,%20%22muidtype%22:%202,%20%22appid%22:%20%22%22,%20%22ex_exp_info%22:%20%7B%7D,%20%22lat%22:%200,%20%22c_sdfree%22:%20163331833856,%20%22render_type%22:%201,%20%22c_device%22:%20%22iPhone12,5%22,%20%22support_component%22:%20%221,2,3%22,%20%22c_osver%22:%20%2215.4.1%22,%20%22c_devicetype%22:%201,%20%22opensdk_ver%22:%20%221.9.2%22,%20%22muid%22:%20%2282b7b6e81a76d495edc9a2da651e2bf7%22,%20%22c_os%22:%20%22ios%22,%20%22postype%22:%2011,%20%22support_tpl2%22:%201%7D%7D&posid=3011387378714381&r=0.503222481149987&adposcount=1&datatype=2&support_https=1&count=3&fc=1";
//    private static final String pageUrl = "http://mi.gdt.qq.com/gdt_mview.fcg?ext=%7B%22req%22:%20%7B%22deep_link_version%22:%201,%20%22tmpallpt%22:%20true,%20%22conn%22:%201,%20%22max_duration%22:%20181,%20%22device_ext%22:%20%7B%22qaid_info%22:%20%7B%22dn_h%22:%20%223df2d0fcd318e10e393ba9d42e0e29f4%22%7D,%20%22attri_info%22:%20%7B%22iv%22:%20%22ea4a188a-96b3-4b56-b2bc-6f925c51221a%22%7D%7D,%20%22carrier%22:%202,%20%22m5%22:%20%225568D5B1-C584-4349-A1A1-019744DBD496%22,%20%22c_dpi%22:%20320,%20%22c_ori%22:%200,%20%22sdk_st%22:%201,%20%22c_w%22:%201242,%20%22wx_installed%22:%20true,%20%22placement_type%22:%209,%20%22s_hd%22:%201,%20%22support_container%22:%20true,%20%22support_features%22:%20636,%20%22sdk_src%22:%20%22%22,%20%22c_h%22:%202688,%20%22c_pkgname%22:%20%22love.match.stars%22,%20%22prld%22:%200,%20%22support_c2s%22:%202,%20%22hostver%22:%20%2211.19.2%22,%20%22sdkver%22:%20%224.13.20%22,%20%22c_isjailbroken%22:%20false,%20%22m_ch%22:%2014,%20%22lng%22:%200,%20%22sdk_cnl%22:%201,%20%22scs%22:%20%220001f9cccc44%22,%20%22muidtype%22:%202,%20%22appid%22:%20%221110171605%22,%20%22ex_exp_info%22:%20%7B%7D,%20%22lat%22:%200,%20%22c_sdfree%22:%20163331833856,%20%22render_type%22:%201,%20%22c_device%22:%20%22iPhone12,5%22,%20%22support_component%22:%20%221,2,3%22,%20%22c_osver%22:%20%2215.4.1%22,%20%22c_devicetype%22:%201,%20%22opensdk_ver%22:%20%221.9.2%22,%20%22muid%22:%20%2253a447cd25416e41c87c031216818f8d%22,%20%22c_os%22:%20%22ios%22,%20%22postype%22:%206,%20%22support_tpl2%22:%202%7D%7D&posid=4011397103902716&r=0.503222481149987&adposcount=1&datatype=2&support_https=1&count=3&fc=1";
//    private static final String pageUrl = "https://www.baidu.com";
//    private static final String pageUrl = "https://ec.snssdk.com/product/lubanajaxstaticitem?id=3453524332989543254&b_type_new=0";
//    private static final String pageUrl = "https://mi.gdt.qq.com/gdt_mview.fcg?posid=5000155655915649&ext=%7B%22req%22%3A%7B%22deep_link_version%22%3A1%2C%22tmpallpt%22%3Atrue%2C%22conn%22%3A1%2C%22max_duration%22%3A181%2C%22device_ext%22%3A%7B%22qaid_info%22%3A%7B%22tz%22%3A%2228800%22%2C%22cy%22%3A%22CN%22%2C%22hd%22%3A%22255865737216%22%2C%22ma%22%3A%22iPhone12%2C5%22%2C%22dm%22%3A%22D431AP%22%2C%22pm%22%3A%223930734592%22%2C%22la%22%3A%22zh-Hans-CN%22%2C%22dn_h%22%3A%22867e57bd062c7169995dc03cc0541c19%22%2C%22st%22%3A%221650131933%22%2C%22sut%22%3A%221650131944.218369%22%7D%2C%22attri_info%22%3A%7B%22iv%22%3A%225B86775C-462F-4CB5-977D-AFFA2322BB13%22%7D%7D%2C%22carrier%22%3A2%2C%22m5%22%3A%2200000000-0000-0000-0000-000000000000%22%2C%22c_dpi%22%3A320%2C%22c_ori%22%3A0%2C%22sdk_st%22%3A1%2C%22c_w%22%3A1242%2C%22wx_installed%22%3Atrue%2C%22placement_type%22%3A9%2C%22s_hd%22%3A1%2C%22support_container%22%3Atrue%2C%22support_features%22%3A636%2C%22sdk_src%22%3A%22%22%2C%22c_h%22%3A2688%2C%22c_pkgname%22%3A%22com.qiushibaike.qiushibaike%22%2C%22prld%22%3A0%2C%22support_c2s%22%3A2%2C%22hostver%22%3A%2211.19.2%22%2C%22sdkver%22%3A%224.13.20%22%2C%22c_isjailbroken%22%3Afalse%2C%22m_ch%22%3A14%2C%22lng%22%3A0%2C%22sdk_cnl%22%3A1%2C%22scs%22%3A%220001fbb8b1ab%22%2C%22muidtype%22%3A2%2C%22appid%22%3A%221107835449%22%2C%22ex_exp_info%22%3A%7B%7D%2C%22lat%22%3A0%2C%22c_sdfree%22%3A163331833856%2C%22render_type%22%3A1%2C%22c_device%22%3A%22iPhone12%2C5%22%2C%22support_component%22%3A%221%2C2%2C3%22%2C%22c_osver%22%3A%2215.4.1%22%2C%22c_devicetype%22%3A1%2C%22opensdk_ver%22%3A%221.9.2%22%2C%22muid%22%3A%229f89c84a559f573636a47ff8daed0d34%22%2C%22c_os%22%3A%22ios%22%7D%7D&count=3&adposcount=1&datatype=2&support_https=1";
//    private static final String pageUrl = "https://www.baidu.com/home/xman/data/tipspluslist?indextype=manht&_req_seqid=0xd4aa61fc0001fd21&asyn=1&t=1650376933156&sid=36310_31254_34813_35912_36165_34584_36121_36195_35802_36234_26350_36061";
//    private static final String pageUrl = "https://www.taobao.com";

//    private static final String proxyIp = "tunnel-proxy-1.de123.net";
//    private static final String proxyIp = "tunnel-proxy-1-internet.de123.net";
    private static final String proxyIp = "127.0.0.1";
    private static final int proxyPort = 21332;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";
    // 本地限流阈值
    private static final int totalNum = 1;
    private static final int totalTask = totalNum * 1;
    private static final CountDownLatch countDownLatch = new CountDownLatch(totalTask);
    private static final ConcurrentHashMap<String, ReqCount> map = new ConcurrentHashMap<>();
    private static final HashMap<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36");
        put("Connection", "close");
    }};

    public static void main(String[] args) throws InterruptedException {
        send(pageUrl, proxyIp, proxyPort, null, null, headers, null);
    }

    static void send(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                     Map<String, String> headers, Map<String, String> params) throws InterruptedException {
        AtomicLong total = new AtomicLong(0);
        ExecutorService executorService = Executors.newFixedThreadPool(totalNum);
        for (int i = 0; i < totalTask; i++) {
            executorService.submit(() -> {
                try {
                    Response response = OkHttpTool.sendGetByProxy(targetUrl, proxyIp, proxyPort, username, password, headers, params);
                    String result = response.body().string();
                    System.out.println(result);
                    int length = result.getBytes().length;
                    System.out.println(length / 1024 + " kb");
                    total.incrementAndGet();
                    System.out.println("第" + total.get() + "个");
                    int code = response.code();
                    if (code == 200) {
                        record(true, Thread.currentThread().getName(), "");
                    } else {
                        record(false, Thread.currentThread().getName(), response.body().string());
                    }
                    countDownLatch.countDown();
                } catch (Throwable e) {
                    countDownLatch.countDown();
                    System.out.println(e.getMessage());
                    total.incrementAndGet();
                    System.out.println("第" + total.get() + "个");
                    record(false, Thread.currentThread().getName(), e.getMessage());
                }
            });
        }
        countDownLatch.await();
        int errorTotal = 0;
        int okTotal = 0;
        Map<String, Integer> errorStatics = new HashMap<>();
        for (Map.Entry<String, ReqCount> entry : map.entrySet()) {
            ReqCount value = entry.getValue();
            AtomicInteger error = value.getError();
            AtomicInteger ok = value.getOk();
            errorTotal += error.get();
            okTotal += ok.get();
            CopyOnWriteArrayList<String> reasonList = value.getReasonList();

            // 统计失败原因
            for (String msg : reasonList) {
                if (errorStatics.containsKey(msg)) {
                    int i = errorStatics.get(msg);
                    i++;
                    errorStatics.put(msg, i);
                } else {
                    errorStatics.put(msg, 1);
                }
            }
        }
        String percent = IpMonitorUtils.getPercent(errorTotal, totalTask);
        System.out.println("并发：" + totalNum + ", 成功=" + okTotal + ", 失败=" + errorTotal + ", 失败率=" + percent + "%, 错误原因列表：" + errorStatics.toString());
        executorService.shutdown();
    }

    static void record(boolean status, String threadName, String failReson) {
        if (status) {
            if (map.containsKey(threadName)) {
                ReqCount reqCount = map.get(threadName);
                reqCount.getOk().incrementAndGet();
            } else {
                ReqCount reqCount = new ReqCount(new AtomicInteger(1), new AtomicInteger(0), new CopyOnWriteArrayList<>());
                map.putIfAbsent(threadName, reqCount);
            }
        } else {
            if (map.containsKey(threadName)) {
                ReqCount reqCount = map.get(threadName);
                reqCount.getError().incrementAndGet();
                reqCount.getReasonList().addIfAbsent(failReson);
            } else {
                ReqCount reqCount = new ReqCount(new AtomicInteger(0), new AtomicInteger(1), new CopyOnWriteArrayList<>());
                reqCount.getReasonList().addIfAbsent(failReson);
                map.putIfAbsent(threadName, reqCount);
            }
        }
    }

    @Data
    @AllArgsConstructor
    static
    class ReqCount {
        AtomicInteger ok;
        AtomicInteger error;
        CopyOnWriteArrayList<String> reasonList;
    }
}
