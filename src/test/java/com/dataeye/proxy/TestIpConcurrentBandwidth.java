package com.dataeye.proxy;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.utils.OkHttpTool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description 测试ip并发上限，和带宽上限
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class TestIpConcurrentBandwidth {

    private static final String pageUrl = "https://www.baidu.com";
//    private static final String pageUrl = "https://www.baidu.com/home/xman/data/tipspluslist?indextype=manht&_req_seqid=0xd4aa61fc0001fd21&asyn=1&t=1650376933156&sid=36310_31254_34813_35912_36165_34584_36121_36195_35802_36234_26350_36061";
//    private static final String pageUrl = "https://www.taobao.com";
//    private static final String pageUrl = "https://www.taobao.com";

    // 距离ip过期的分钟数
    int minIpValidMinute = 1;
    // 单个ip测试轮数
    int round = 5;
    // 每个线程运行任务的个数
    int taskNumPerThread = 2;
    // 直连ip
    String directGetUrl = "http://webapi.http.zhimacangku.com/getip?num=1&type=2&pro=&city=0&yys=0&port=1&pack=228695&ts=1&ys=0&cs=0&lb=1&sb=0&pb=4&mr=1&regions=";
    // 独享ip
    String exclusiveGetUrl = "http://http.tiqu.letecs.com/getip3?num=1&type=2&pro=&city=0&yys=0&port=1&pack=228695&ts=1&ys=0&cs=0&lb=1&sb=0&pb=4&mr=1&regions=&gm=4&time=2";
    @Autowired
    private ApnProxyRemoteChooser apnProxyRemoteChooser;
    @Resource
    private TunnelInitMapper tunnelInitMapper;

    /**
     * 并发数阶级测试
     *
     * @throws InterruptedException
     */
    @Test
    public void test() throws InterruptedException {
        for (int i = 20; i <= 100; i += 10) {
            System.out.println("----------------------- 并发数：" + i + " ------------------------");
            long begin = System.currentTimeMillis();
//            ApnProxyRemote proxyConfig = directGetIp(directGetUrl);
            singleConcurrent(i);
            long cost = (System.currentTimeMillis() - begin) / 1000;
            System.out.println("并发数：" + i + ", 耗时：" + cost + "s");
        }
    }

    /**
     * 测试单次指定并发数
     *
     * @param concurrency
     */
    void singleConcurrent(int concurrency) throws InterruptedException {
//    void singleConcurrent(int concurrency, ApnProxyRemote proxyConfig) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        List<String> resultList = new LinkedList<>();

        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        for (int i = 0; i < round; i++) {
            ApnProxyRemote proxyConfig = apnProxyRemoteChooser.getProxyConfig(tunnelInstances.get(0));
            // 保证测试过程 ip 都在有效期内
            LocalDateTime expireTime = proxyConfig.getExpireTime();
            LocalDateTime validTime = LocalDateTime.now().plusMinutes(minIpValidMinute);
            boolean valid = validTime.isBefore(expireTime);
            String result = "";
            if (valid) {
                result = testConcurrentBandwidth(proxyConfig, concurrency, executorService);
            } else {
                System.out.println("ip " + proxyConfig.getRemote() + " 有效期小于" + minIpValidMinute + "分钟，重新拉取");
            }
            if (StringUtils.isNotBlank(result)) {
                resultList.add(result);
            }
        }
        resultList.forEach(System.out::println);
        executorService.shutdown();
    }

    /**
     * 测试某个ip的并发上限和带宽上限
     *
     * @param proxyConfig ip配置
     * @throws InterruptedException 异常
     */
    String testConcurrentBandwidth(ApnProxyRemote proxyConfig, int concurrency, ExecutorService executorService) throws InterruptedException {
        int errorTotal = 0;
        int okTotal = 0;
        int totalTask = concurrency * taskNumPerThread;
        ConcurrentHashMap<String, ReqCount> map = new ConcurrentHashMap<>();
        CountDownLatch countDownLatch = new CountDownLatch(totalTask);
        AtomicInteger bandwidthTotal = new AtomicInteger(0);

        String remoteHost = proxyConfig.getRemoteHost();
        int remotePort = proxyConfig.getRemotePort();
        String remoteAddr = proxyConfig.getRemote();
        String username = "dataeye";
        String password = "dataeye++123";

        AtomicLong total = new AtomicLong(0);
        for (int i = 0; i < totalTask; i++) {
            executorService.submit(() -> {
                try {
                    Response response = sendByOkHttp(remoteHost, remotePort, username, password);
                    int length = Objects.requireNonNull(response.body()).string().getBytes().length;
                    int size = length / 1024;
                    bandwidthTotal.addAndGet(size);
                    total.incrementAndGet();
//                    System.out.println("第" + total.get() + "个请求, 报文大小=" + size + " kb");
                    int code = response.code();
                    record(code == 200, Thread.currentThread().getName(), map);
                    countDownLatch.countDown();
                } catch (Throwable e) {
                    countDownLatch.countDown();
                    total.incrementAndGet();
//                    System.out.println("第" + total.get() + "个请求，异常=" + e.getMessage() + ", IP=" + remoteAddr);
                    record(false, Thread.currentThread().getName(), map);
                }
            });
        }
        countDownLatch.await();
        for (Map.Entry<String, ReqCount> entry : map.entrySet()) {
            ReqCount value = entry.getValue();
            AtomicInteger error = value.getError();
            AtomicInteger ok = value.getOk();
            errorTotal += error.get();
            okTotal += ok.get();
        }
        return "IP=" + remoteAddr + ", 并发=" + concurrency + ", 成功=" + okTotal + ", 失败=" + errorTotal + ", " +
                "失败率=" + getPercent(errorTotal, totalTask) + "%, 平均响应报文大小=" + (bandwidthTotal.get() / totalTask) + " kb";
    }

    void record(boolean status, String threadName, ConcurrentHashMap<String, ReqCount> map) {
        if (status) {
            if (map.containsKey(threadName)) {
                ReqCount reqCount = map.get(threadName);
                reqCount.getOk().incrementAndGet();
            } else {
                ReqCount reqCount = new ReqCount(new AtomicInteger(1), new AtomicInteger(0));
                map.putIfAbsent(threadName, reqCount);
            }
        } else {
            if (map.containsKey(threadName)) {
                ReqCount reqCount = map.get(threadName);
                reqCount.getError().incrementAndGet();
            } else {
                ReqCount reqCount = new ReqCount(new AtomicInteger(0), new AtomicInteger(1));
                map.putIfAbsent(threadName, reqCount);
            }
        }
    }

    Response sendByOkHttp(String ip, int port, String username, String password) throws IOException {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        Authenticator authenticator = (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
        clientBuilder.proxyAuthenticator(authenticator);

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port));
        clientBuilder.proxy(proxy);
        clientBuilder.connectTimeout(60, TimeUnit.SECONDS);
        clientBuilder.callTimeout(60, TimeUnit.SECONDS);

        Request request = new Request.Builder()
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
//                .addHeader("Connection", "Keep-Alive")
                .addHeader("Connection", "close")
                .build();

        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    String getPercent(float num1, float num2) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);
        float devide = num1 / num2;
        return numberFormat.format(devide * 100);
    }

    /**
     * 直接从芝麻代理官网拉取ip，有效时间：25min-3h
     */
    ApnProxyRemote directGetIp(String url) {
        ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
        String json = OkHttpTool.doGet(url, new HashMap<>(0), true);
        JSONObject jsonObject = JSONObject.parseObject(json);
        boolean success = jsonObject.getBooleanValue("success");
        if (success) {
            JSONArray data = jsonObject.getJSONArray("data");
            if (data.size() > 0) {
                String ipItem = data.get(0).toString();
                JSONObject ipElement = JSONObject.parseObject(ipItem);
                String ip = ipElement.getString("ip");
                int port = ipElement.getIntValue("port");
                String expire_time = ipElement.getString("expire_time");
                apPlainRemote.setAppleyRemoteRule(true);
                apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
                apPlainRemote.setRemoteHost(ip);
                apPlainRemote.setRemotePort(port);
                apPlainRemote.setProxyUserName("");
                apPlainRemote.setProxyPassword("");
                LocalDateTime parse = LocalDateTime.parse(expire_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                apPlainRemote.setExpireTime(parse);
            }
        } else {
            throw new RuntimeException("从芝麻代理官网拉取ip失败，原因：" + json);
        }
        return apPlainRemote;
    }

    @Data
    @AllArgsConstructor
    static
    class ReqCount {
        AtomicInteger ok;
        AtomicInteger error;
    }

}
