package com.dataeye.proxy.manage;

import com.dataeye.proxy.TunnelProxyApplication;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.Resource;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    ReqMonitorUtils reqMonitorUtils;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchServiceImpl;
    @Resource
    SendMailService sendMailService;
    @Resource
    TunnelInitService tunnelInitService;

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

        reqMonitorUtils.dynamicAdjustIpPool(logger,"87.23",1, TimeUnit.MINUTES);
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
