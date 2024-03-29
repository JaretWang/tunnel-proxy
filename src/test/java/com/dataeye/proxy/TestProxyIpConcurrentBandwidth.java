package com.dataeye.proxy;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.service.impl.DaiLiYunExclusiveFetchServiceImpl;
import com.dataeye.proxy.service.impl.DailiCloudFetchServiceImpl;
import com.dataeye.proxy.service.impl.ZhiMaExclusiveFetchServiceImpl;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.*;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description 测试ip并发上限，和带宽上限
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TunnelProxyApplication.class)
public class TestProxyIpConcurrentBandwidth {

    private static final String pageUrl = "https://www.baidu.com";
//    private static final String pageUrl = "https://www.baidu.com/home/xman/data/tipspluslist?indextype=manht&_req_seqid=0xd4aa61fc0001fd21&asyn=1&t=1650376933156&sid=36310_31254_34813_35912_36165_34584_36121_36195_35802_36234_26350_36061";
//    private static final String pageUrl = "https://www.taobao.com";
//    private static final String pageUrl = "https://www.taobao.com";

    // 距离ip过期的分钟数
    int minIpValidMinute = 1;
    // 单个ip测试轮数
    int round = 5;
    // 每个线程运行任务的个数
    int taskNumPerThread = 10;
    // 初始并发线程数
    int initThreadSize = 10;
    // 最大并发线程数
    int maxThreadSize = 50;
    // 线程数递增大小间隔
    int threadSizeIncremental = 10;
    // 每一轮间隔秒数
    int intervalSecondForEachRound = 2;

    @Autowired
    DaiLiYunExclusiveFetchServiceImpl daiLiYunExclusiveService;
    @Autowired
    DailiCloudFetchServiceImpl dailiCloudFetchService;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchService;
    @Autowired
    ZhiMaExclusiveFetchServiceImpl zhiMaExclusiveFetchService;

    /**
     * 测试芝麻独享ip
     */
    @Test
    public void testZhiMaExclusive() throws InterruptedException {
        for (int i = initThreadSize; i <= maxThreadSize; i += threadSizeIncremental) {
            System.out.println("----------------------- 并发数：" + i + " ------------------------");
            long begin = System.currentTimeMillis();
            ProxyIp proxyIp = zhiMaExclusiveFetchService.getOne(null);
            if (proxyIp == null) {
                System.out.println("代理ip为空, 并发：" + i);
            }
            singleConcurrent(i, proxyIp);
            long cost = (System.currentTimeMillis() - begin) / 1000;
            System.out.println("并发数：" + i + ", 耗时：" + cost + "s");
        }
    }

    /**
     * 测试代理云独享ip
     *
     * @throws InterruptedException
     */
    @Test
    public void testDailiCloudExclusive() throws Exception {
        for (int i = initThreadSize; i <= maxThreadSize; i += threadSizeIncremental) {
            System.out.println("----------------------- 并发数：" + i + " ------------------------");
            long begin = System.currentTimeMillis();
            ProxyIp proxyIp = daiLiYunExclusiveService.getOne(null);
            singleConcurrent(i, proxyIp);
            long cost = (System.currentTimeMillis() - begin) / 1000;
            System.out.println("并发数：" + i + ", 耗时：" + cost + "s");
        }
    }

    /**
     * 测试芝麻代理
     *
     * @throws InterruptedException
     */
    @Test
    public void testZhiMa() throws InterruptedException {
        for (int i = initThreadSize; i <= maxThreadSize; i += threadSizeIncremental) {
            System.out.println("----------------------- 并发数：" + i + " ------------------------");
            long begin = System.currentTimeMillis();
            ProxyIp proxyIp = zhiMaFetchService.getOne(null);
            singleConcurrent(i, proxyIp);
            long cost = (System.currentTimeMillis() - begin) / 1000;
            System.out.println("并发数：" + i + ", 耗时：" + cost + "s");
        }
    }

    /**
     * 测试代理云
     *
     * @throws InterruptedException
     */
    @Test
    public void testDailiCloud() throws InterruptedException {
        for (int i = initThreadSize; i <= maxThreadSize; i += threadSizeIncremental) {
            System.out.println("----------------------- 并发数：" + i + " ------------------------");
            long begin = System.currentTimeMillis();
            ProxyIp proxyIp = dailiCloudFetchService.getOne(null);
            singleConcurrent(i, proxyIp);
            long cost = (System.currentTimeMillis() - begin) / 1000;
            System.out.println("并发数：" + i + ", 耗时：" + cost + "s");
        }
    }

    /**
     * 单次并发测试
     *
     * @param concurrency 本次测试并发数
     * @param proxyConfig 代理ip配置
     * @throws InterruptedException
     */
    void singleConcurrent(int concurrency, ProxyIp proxyConfig) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        List<String> resultList = new LinkedList<>();

        for (int i = 0; i < round; i++) {
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
            // 每一轮时间间隔
            Thread.sleep(intervalSecondForEachRound * 1000L);
        }
        resultList.forEach(System.out::println);
//        resultList.forEach(logger::info);
        executorService.shutdown();
    }

    /**
     * 测试某个ip的并发上限和带宽上限
     *
     * @param proxyConfig ip配置
     * @throws InterruptedException 异常
     */
    String testConcurrentBandwidth(ProxyIp proxyConfig, int concurrency, ExecutorService executorService) throws InterruptedException {
        int errorTotal = 0;
        int okTotal = 0;
        int totalTask = concurrency * taskNumPerThread;
        ConcurrentHashMap<String, ReqCount> map = new ConcurrentHashMap<>();
        CountDownLatch countDownLatch = new CountDownLatch(totalTask);
        AtomicInteger bandwidthTotal = new AtomicInteger(0);

        String remoteHost = proxyConfig.getHost();
        int remotePort = proxyConfig.getPort();
        String remoteAddr = proxyConfig.getRemote();
        String username = proxyConfig.getUserName();
        String password = proxyConfig.getPassword();

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

    /**
     * 记录成功失败次数
     *
     * @param status     请求是否成功
     * @param threadName 线程名
     * @param map        保存记录
     */
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

    /**
     * 使用代理ip发送http请求
     *
     * @param ip       代理ip
     * @param port     代理端口
     * @param username 代理用户名
     * @param password 代理密码
     * @return
     * @throws IOException
     */
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
        clientBuilder.connectTimeout(10, TimeUnit.SECONDS);
        clientBuilder.callTimeout(30, TimeUnit.SECONDS);

        Request request = new Request.Builder()
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
//                .addHeader("Connection", "Keep-Alive")
                .addHeader("Connection", "close")
                .build();

        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * 获取百分比
     *
     * @param num1 除数
     * @param num2 被除数
     * @return
     */
    String getPercent(float num1, float num2) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);
        float devide = num1 / num2;
        return numberFormat.format(devide * 100);
    }

    @Data
    @AllArgsConstructor
    static
    class ReqCount {
        AtomicInteger ok;
        AtomicInteger error;
    }

}
