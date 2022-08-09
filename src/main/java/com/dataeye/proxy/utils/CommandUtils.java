package com.dataeye.proxy.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author jaret
 * @date 2022/8/9 10:30
 * @description 命令行执行工具
 */
public class CommandUtils {

    /**
     * 执行命令行
     *
     * @param command 命令
     */
    public static String exec(String command) {
        String[] cmds = new String[]{"sh", "-c", command};
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            cmds = new String[]{"cmd", "/k", command};
        }

        Process exec = null;
        InputStreamReader ips = null;
        BufferedReader br = null;
        StringBuilder builder = new StringBuilder();
        try {
            exec = Runtime.getRuntime().exec(cmds);
            ips = new InputStreamReader(exec.getInputStream());
            br = new BufferedReader(ips);
            String line;
            while ((line = br.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (exec != null) {
                exec.destroy();
            }
            try {
                if (ips != null) {
                    ips.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return builder.toString();
    }

}
