package com.dataeye.proxy;

import com.dataeye.proxy.apn.bean.ProxyIp;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/26 14:33
 * @description ip池轮询测试
 */
public class TestIpPool {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws InterruptedException {
        int totalTask = 100;
        ConcurrentLinkedQueue<ProxyIp> ipPool = buildIpPool();
        CountDownLatch countDownLatch = new CountDownLatch(totalTask);
        for (int i = 0; i < totalTask; i++) {
            ProxyIp ip = ipPool.poll();
            executorService.submit(new SendReqTask(ip, countDownLatch));
            assert ip != null;
            AtomicLong useTimes = ip.getUseTimes();
            useTimes.incrementAndGet();
            ip.setUseTimes(useTimes);
            ipPool.offer(ip);
        }

        countDownLatch.await();
        for (ProxyIp proxyCfg : ipPool) {
            System.out.println("ip=" + proxyCfg.getHost() + ", useTimes=" + proxyCfg.getUseTimes());
        }
    }

    private static void sendRequest(ProxyIp ip) {
        System.out.println("模拟 ip=" + ip.getHost() + ", port=" + ip.getPort() + " 发送请求");
    }

    private static ConcurrentLinkedQueue<ProxyIp> buildIpPool() {
        ConcurrentLinkedQueue<ProxyIp> pool = new ConcurrentLinkedQueue<>();
        Random random = new Random();
        for (int i = 10; i < 20; i++) {
            int ip_val = random.nextInt(6553);
            int min_val = random.nextInt(5);
            int second_val = random.nextInt(60);
            ProxyIp build = ProxyIp.builder()
                    .host("10.10.10." + i)
                    .port(ip_val)
                    .expireTime(LocalDateTime.now().plusMinutes(min_val).plusSeconds(second_val))
                    .useTimes(new AtomicLong(0))
                    .build();
            pool.offer(build);
        }
        return pool;
    }

    static class SendReqTask implements Runnable {

        ProxyIp ip;
        CountDownLatch countDownLatch;

        public SendReqTask(ProxyIp ip, CountDownLatch countDownLatch) {
            this.ip = ip;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            sendRequest(ip);
            countDownLatch.countDown();
        }
    }

}
