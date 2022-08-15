package com.dataeye.proxy.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/8/10 14:49
 * @description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolaCountryCode {

    @ExcelProperty(index = 0)
    String name;
    @ExcelProperty(index = 1)
    String code;

}
