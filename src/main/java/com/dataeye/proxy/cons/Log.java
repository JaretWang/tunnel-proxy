package com.dataeye.proxy.cons;

import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/11/2 11:07
 * @description
 */
public class Log {

    public static final Logger SERVER = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

}
