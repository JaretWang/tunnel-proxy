package com.dataeye.proxy.manage;

import com.dataeye.proxy.TunnelProxyApplication;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.starter.httpclient.HttpClientResponse;
import com.dataeye.starter.httpclient.ResponseEntityType;
import com.dataeye.starter.httpclient.common.CommonHttpClient;
import com.dataeye.starter.httpclient.simple.SimpleHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.Resource;
import java.util.StringJoiner;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jaret
 * @date 2022/3/28 19:36
 * @description 测试芝麻代理接口
 */
@Slf4j
@SpringBootTest(classes = TunnelProxyApplication.class)
@ComponentScan(basePackages = "com.dataeye.proxy")
public class TestMybatis {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ReqMonitorUtils");

    //    public static final TunnelInstance TUNNEL_INSTANCE = TunnelInstance.builder()
//            .alias("youliang")
//            .location("localhost")
//            .enable(1)
//            .domain("127.0.0.1")
//            .port(21332)
//            .username("dataeye")
//            .password("dataeye++123")
//            .bossThreadSize(1)
//            .workerThreadSize(10)
//            .concurrency(100)
//            .maxNetBandwidth(4)
//            .maxSlowReqSize(3)
//            .coreIpSize(3)
//            .maxIpSize(10)
//            .checkIpPoolIntervalSeconds(5)
//            .minSuccessPercentForRemoveIp(50)
//            .minUseTimesForRemoveIp(100)
//            .maxFetchIpNumEveryDay(5000)
//            .connectTimeoutMillis(10000)
//            .retryCount(3)
//            .lastModified(TimeUtils.formatLocalDate(LocalDateTime.now()))
//            .createTime(TimeUtils.formatLocalDate(LocalDateTime.now()))
//            .description("优量")
//            .build();
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("getAvailableIpPerUnitTime-"), new ThreadPoolExecutor.AbortPolicy());
    @Autowired
    ReqMonitorUtils reqMonitorUtils;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchServiceImpl;
    @Resource
    SendMailService sendMailService;
    @Autowired
    SimpleHttpClient simpleHttpClient;
    @Autowired
    CommonHttpClient commonHttpClient;
    @Resource
    TunnelInitService tunnelInitService;
    @Autowired
    TunnelInitMapper tunnelInitMapper;

    /**
     * 查询每个隧道的请求监控记录
     */
    @Test
    public void getMonitorLog() throws InterruptedException {
//        CountDownLatch countDownLatch = new CountDownLatch(1);
//        SCHEDULE_EXECUTOR.scheduleAtFixedRate(() -> {
//            try {
//                int ipSelector = this.ipSelector.getAvailableIpPerUnitTime(MyLogbackRollingFileUtil.getLogger("IpSelector"), null);
//                System.out.println("ipSelector-->" + ipSelector);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }, 0, 3, TimeUnit.SECONDS);
//        System.out.println("waiting...");
//        countDownLatch.await();
    }

    @Test
    public void test2() {
        String url = "http://c.gdt.qq.com/gdt_click.fcg?viewid=M0sGpdOnGU10ovCRMNMfAiHO1LdGFeMekAbf_kA1jnVczzkBLsiksdUj9ahEtza1D5k!5EzORkZeIlQ2x_cMMJ7ftB6Aq36u7hZDj5iciBBdnitMfEZzuzqgHIVS6EfnXMS0wtEI1hlwKQ5U573IiXD5umaROWFnEJIzcXPT!OP9lvtTEMtpUTO5LyNP18h8ZMDASVBt_i!G7toT6rfeCl!PS4mEz7oSeP6cCCvKtvlRynPrUkVsSw&jtype=0&i=1&os=2&s_lp=101&acttype=__ACT_TYPE__&ch=__CHANNEL_ID__&seq=__SEQ__&aseq=__ABS_SEQ__&rt=__RETURN_TYPE__&s=%7B%22da%22%3A%22__WIDTH__%22%2C%22db%22%3A%22__HEIGHT__%22%2C%22down_x%22%3A%22__DOWN_X__%22%2C%22down_y%22%3A%22__DOWN_Y__%22%2C%22up_x%22%3A%22__UP_X__%22%2C%22up_y%22%3A%22__UP_Y__%22%7D&lpp=click_ext%3DeyJhdWlkIjoiMjM0OTQxNTQiLCJleHBfcGFyYW0iOiJKdW1wVXNlSnNBcGk6MSx3aXRoUGxheUNvbXBvbmVudDoxMSIsImxvbmdfcG9zX2lkIjoiNzA3MDgzMDIyNTQyMDY1MCIsIm1lZGl1bV9pZCI6IjQwODAzMzQzMjgwNjA5In0%253D&clklpp=__CLICK_LPP__&nxjp=1&xp=2&conn_type=__NET_STATUS__&vto=__VIDEO_PLAY_TIME__&tl=1";

//        HttpClientResponse response = commonHttpClient.doGet(url, null, ResponseEntityType.STRING_UTF8, null);
        HttpClientResponse response = simpleHttpClient.doGet(url, null, null, ResponseEntityType.STRING_UTF8, true);
//        System.out.println(response.getStatusCode());
        System.out.println(response.getRedirectLocations());
        System.out.println(response.getRealRequestUrl());
//        System.out.println(response.getResponseContent());
//        System.out.println(response.getContentType());
//        System.out.println(response.getContentLength());
    }

    @Test
    public void test() throws InterruptedException {
//        List<TunnelInstance> tunnelInstances = tunnelInitService.getTunnelList();
//        log.warn(JSON.toJSONString(tunnelInstances));

//        TunnelInstance newTunnel = zhiMaFetchServiceImpl.buildNewTunnelInstance(TUNNEL_INSTANCE, 99, 199);
//        int count = tunnelInitService.updateTunnel(newTunnel);
//        System.out.println("count-->" + count);


//        String subject = "隧道 youliang IP拉取数量告警";
//        sendMailService.sendMail(subject, getAlarmContent());

//        int surplusIpSize = zhiMaFetchServiceImpl.getSurplusIpSize();
//        System.out.println(surplusIpSize);

//        reqMonitorUtils.dynamicAdjustIpPool(logger,"87.23",1, TimeUnit.MINUTES);

        tunnelInitService.updateUsedIp("youliang", 123);
        tunnelInitService.updateSuccessRate("youliang", 11, 22);
    }

    String getAlarmContent() {
        String content = new StringJoiner(", ")
                .add("隧道=优量")
                .add("告警等级=3级")
                .add("已拉取ip数=10")
                .add("每日最大拉取ip数限制=10000").toString();
        String alarmRule = new StringJoiner(System.lineSeparator())
                .add("告警规则:")
                .add("最少需要ip数 = (每日剩余分钟数/5min) * ip池的大小(ps: 套餐内ip的有效时间都是1-5min) + 安全保障ip数(500) (当日不使用劣质ip剔除机制后需要的ip数)")
                .add("最大容错ip数 = 总限制数 - 当日不使用劣质ip剔除机制后需要的最少ip数 (最大容错ip数是为了保证套餐内一天下来肯定会有ip可用)")
                .add("TPS = 每秒处理的连接数")
                .add("ip每秒被使用次数 = TPS/ip池大小")
                .add("ip移除前最少使用次数 = (ip每秒被使用次数 * ip质量检测时间间隔) * 5(使用轮数)")
                .add("ip使用数达到总限制数70%: 1级告警 (主动降低移除ip的最低成功率为原来的75%, 且调大ip移除前最少使用次数为原来的25%)")
                .add("ip使用数达到总限制数80%: 2级告警 (主动降低移除ip的最低成功率为原来的50%, 且调大ip移除前最少使用次数为原来的50%)")
                .add("ip使用数达到总限制数90%: 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除机制, 只根据ip是否过期来剔除)")
                .add("ip使用数达到最大容错ip数: 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除规则, 只根据ip是否过期来剔除)")
                .add("套餐剩余ip总数小于所有隧道的当日最少需要ip数之和 -> 3级告警 (主动降低移除ip的最低成功率为0%, 即完全取消劣质ip剔除规则, 只根据ip是否过期来剔除)")
                .toString();
        return content + System.lineSeparator() + System.lineSeparator() + alarmRule;
    }

}
