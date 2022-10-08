package com.dataeye.proxy.excel;

import com.alibaba.excel.EasyExcel;
import com.dataeye.proxy.excel.bean.IpUseCount;
import com.dataeye.proxy.excel.bean.IpUseLog;
import com.dataeye.proxy.excel.listener.IpUseCountListener;
import com.dataeye.proxy.excel.listener.IpUseLogListener;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

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

    static void ipUseLog() {
        String path = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\228695-20221007用量.xlsx";
        EasyExcel.read(new File(path), IpUseLog.class, new IpUseLogListener()).sheet(0).doRead();
    }

    static void ipUseLog2() {
        String path = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\2022-10-07芝麻拉取情况.xlsx";
        EasyExcel.read(new File(path), IpUseCount.class, new IpUseCountListener()).sheet(0).doRead();
    }

}
