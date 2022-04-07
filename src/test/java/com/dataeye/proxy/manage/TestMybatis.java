package com.dataeye.proxy.manage;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author jaret
 * @date 2022/3/28 19:36
 * @description 测试芝麻代理接口
 */
@Slf4j
@SpringBootTest(classes = TestMybatis.class)
@ComponentScan(basePackages = "com.dataeye.proxy")
public class TestMybatis {

    @Resource
    TunnelInitMapper tunnelInitMapper;

    @Test
    public void test() {
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        String s = JSON.toJSONString(tunnelInstances);
        System.out.println(s);
    }

}
