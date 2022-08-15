package com.dataeye.proxy.excel;

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
public class IpUseLog {

    @ExcelProperty(index = 0)
    String id;
    @ExcelProperty(index = 1)
    String username;
    @ExcelProperty(index = 2)
    String whiteIIp;
    @ExcelProperty(index = 3)
    String userIpPort;
    @ExcelProperty(index = 4)
    String menu;
    @ExcelProperty(index = 5)
    String time;

}
