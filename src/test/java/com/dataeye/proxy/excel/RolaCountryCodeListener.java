package com.dataeye.proxy.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author jaret
 * @date 2022/8/2 15:38
 * @description
 */
@Slf4j
public class RolaCountryCodeListener extends AnalysisEventListener<RolaCountryCode> {

    JSONObject jsonObject = new JSONObject();

    @Override
    public void invoke(RolaCountryCode data, AnalysisContext context) {
        if (data != null) {
            jsonObject.put(data.name, data.code);
        }
    }

    @SneakyThrows
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        String path = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\gitlab\\tunnel-proxy\\src\\main\\resources\\rola\\country_code.json";
        System.out.println("country size = " + jsonObject.size());
        FileUtils.writeStringToFile(new File(path), JSON.toJSONString(jsonObject, true), StandardCharsets.UTF_8, true);
    }

}
