package com.dataeye.proxy.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.excel.bean.IpUseCount;
import com.dataeye.proxy.excel.bean.IpUseLog;
import com.dataeye.proxy.excel.listener.IpUseCountListener;
import com.dataeye.proxy.excel.listener.IpUseLogListener;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.*;

/**
 * @author jaret
 * @date 2022/8/10 14:46
 * @description
 */
@SpringBootTest
public class ExcelTest {

    public static void main(String[] args) {
        ipUseLog();
//        ipUseLog2();
//        rolaCountryCode();
    }

    static void rolaCountryCode() {
        String path = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\gitlab\\tunnel-proxy\\src\\main\\resources\\rola\\country_code.xlsx";
        EasyExcel.read(new File(path), RolaCountryCode.class, new RolaCountryCodeListener()).sheet(0).doRead();
    }

    /**
     * 芝麻ip套餐使用情况
     */
    static void ipUseLog() {
        List<String> list = Arrays.asList(
                "C:\\Users\\caiguanghui\\Desktop\\DataEye\\芝麻用量\\2022-11-27 (2).xlsx",
                "C:\\Users\\caiguanghui\\Desktop\\DataEye\\芝麻用量\\2022-11-27 (3).xlsx",
                "C:\\Users\\caiguanghui\\Desktop\\DataEye\\芝麻用量\\2022-11-27 (4).xlsx"
        );
        Set<String> whiteIpList = new HashSet<>();
        for (String path : list) {
            EasyExcel.read(new File(path), IpUseLog.class, new IpUseLogListener(whiteIpList)).sheet(0).doRead();
            System.out.println("------------------------ split ------------------------");
        }
        System.out.println("不属于隧道的机器列表：" + JSON.toJSONString(whiteIpList,true));
    }

    static void ipUseLog2() {
        String path = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\2022-10-07芝麻拉取情况.xlsx";
        EasyExcel.read(new File(path), IpUseCount.class, new IpUseCountListener()).sheet(0).doRead();
    }

}
