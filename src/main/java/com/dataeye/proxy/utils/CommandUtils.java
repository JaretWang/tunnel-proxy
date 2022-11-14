package com.dataeye.proxy.utils;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.extra.ssh.JschUtil;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

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

    public static String exec(String command, String sshHost, int sshPort, String sshUser, String sshPass) throws JSchException {
        Session session = JschUtil.getSession(sshHost, sshPort, sshUser, sshPass);
        session.setTimeout(10000);
        String exec = JschUtil.exec(session, command, CharsetUtil.CHARSET_UTF_8);
        JschUtil.close(session);
        return exec;
    }

    public static void main(String[] args) throws Exception {
        Session session = JschUtil.getSession("154.37.50.4", 20097, "root", "d5d4cc42d9");
        session.setTimeout(10000);
        //String s = JschUtil.exec(session, "cd /root/tinyproxy-1.11.0-rc1 && ls", CharsetUtil.CHARSET_UTF_8);
        String s = JschUtil.exec(session, "ifconfig", CharsetUtil.CHARSET_UTF_8);
        System.out.println(s);
        JschUtil.closeAll();
    }

}
