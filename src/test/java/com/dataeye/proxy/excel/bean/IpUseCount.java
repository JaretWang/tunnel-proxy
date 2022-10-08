package com.dataeye.proxy.excel.bean;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/8/2 15:41
 * @description 芝麻IP使用记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpUseCount {

    @ExcelProperty(index = 0)
    String time;
    @ExcelProperty(index = 1)
    String ourIp;
    @ExcelProperty(index = 2)
    String proxyIp;
    @ExcelProperty(index = 3)
    String proxyPort;

}
