package com.dataeye.proxy.zhima;

import org.apache.commons.io.FileUtils;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author jaret
 * @date 2022/10/10 17:10
 * @description
 */
public class ProxyIpUseCheck {

    public static void main(String[] args) throws IOException {
        String edxMachine = "C:\\Users\\caiguanghui\\Desktop\\DataEye\\gitlab\\tunnel-proxy\\src\\test\\java\\com\\dataeye\\proxy\\zhima\\edx-machine.txt";
        String json = "{\"47.106.91.101\":675,\"47.106.220.225\":672,\"120.25.202.242\":629,\"120.24.47.16\":439,\"120.78.220.91\":434,\"47.106.91.86\":429,\"120.76.195.162\":426,\"120.79.133.21\":424,\"120.77.147.157\":421,\"47.106.169.235\":412,\"120.77.238.36\":406,\"120.76.158.30\":402,\"120.76.219.50\":402,\"120.78.229.110\":398,\"120.77.54.159\":396,\"39.108.176.191\":394,\"120.78.219.56\":383,\"120.79.94.169\":24}";

        List<String> lines = FileUtils.readLines(new File(edxMachine), StandardCharsets.UTF_8);
        for (String line : lines) {
            StringJoiner joiner = new StringJoiner(",");
            for (String s : line.trim().split(" ")) {
                if (StringUtils.isNotBlank(s.trim())) {
                    joiner.add(s.trim());
                }
            }
            String[] split = joiner.toString().split(",");
            String ip = split[0];
            String name = split[1];
            json = json.replaceAll(ip,name);
        }
        System.out.println(json);
    }

}
