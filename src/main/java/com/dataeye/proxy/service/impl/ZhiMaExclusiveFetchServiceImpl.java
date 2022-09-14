package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ZhiMaConfig;
import com.dataeye.proxy.selector.custom.ZhiMaCustomIpSelector;
import com.dataeye.proxy.service.ProxyFetchService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 芝麻独享ip拉取
 *
 * @author jaret
 * @date 2022/4/1 19:30
 * @description
 */
@Service
public class ZhiMaExclusiveFetchServiceImpl implements ProxyFetchService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ZhiMaExclusiveFetchServiceImpl");
    private static final ConcurrentLinkedQueue<ProxyIp> IP_POOL = new ConcurrentLinkedQueue<>();
    @Resource
    ZhiMaConfig zhiMaConfig;
    @Autowired
    ZhiMaCustomIpSelector zhiMaCustomIpSelector;

    @PostConstruct
    void init() {
        for (int i = 1; i <= 19; i++) {
            String realUrl = "http://47.103.37.73:8001/v1/data?username=seonzhang&ipid=dx340700_92__zm_" + i;
            ProxyIp proxyIp = zhiMaCustomIpSelector.getIpByNetCardSeq(realUrl);
            if (proxyIp != null) {
                IP_POOL.offer(proxyIp);
            }
        }
        logger.info("初始化ip池完成, size={}, data={}", IP_POOL.size(), JSON.toJSONString(IP_POOL));
    }

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws InterruptedException {
        ProxyIp poll = IP_POOL.poll();
        if (poll == null) {
            logger.error("ip is null from queue, quit");
        }
        return poll;

//        List<ProxyIp> ipList = getIpList(1, tunnelInstance, false);
//        if (CollectionUtils.isEmpty(ipList)) {
//            return null;
//        }
//        return ipList.get(0);
    }

    public List<ProxyIp> getIpList(int num, TunnelInstance tunnelInstance, boolean init) throws InterruptedException {

        /**
         * http://ahtl2x-ljx-1.upaix.cn:120/addip?user=user&passwd=user1&ip= 添加 白名单 在ip=的后面添加自己的ip
         * http://47.103.37.73:8001/v1/info?username=seonzhang 提取链接
         * http://47.103.37.73:8001/v1/data?username=seonzhang&ipid=dx340700_92__zm_1 指定 提取网卡 ip信息 只可修改最后一个数字 1-19
         * http://47.103.37.73:8001/v1/control?username=seonzhang9&ipid=dx340700_92__zm_1 指定 网卡重拨 只可修改最后一个数字 1-19
         */
        String url = "http://47.103.37.73:8001/v1/info?username=seonzhang";
        String json = OkHttpTool.doGet(url);
        if (StringUtils.isBlank(json)) {
            logger.error("请求结果为空：{}", json);
            return null;
        }

        JSONObject jsonObject = JSONObject.parseObject(json);
        int code = jsonObject.getIntValue("code");
        if (code != 0) {
            logger.error("响应码错误,原因: {}", json);
            return null;
        }

        /**
         * code: 0,
         *                 data: [
         *         {
         *             city: "黄石市",
         *                     expiry_time: "1662797525",
         *                 ipid: "lt420201_7__zm_38",
         *                 open_time: "1662711125",
         *                 province: "湖北省",
         *                 remark: "",
         *                 s5_ip: "183.92.197.150",
         *                 s5_port: "4216",
         *                 s5_pwd: "",
         *                 s5_user: ""
         *         };
         */
        JSONArray data = jsonObject.getJSONArray("data");
        for (Object datum : data) {
            if (datum instanceof JSONObject) {
                JSONObject element = (JSONObject) datum;
                String city = element.getString("city");
                String expiry_time = element.getString("expiry_time");
                String ipid = element.getString("ipid");
                String open_time = element.getString("open_time");
                String province = element.getString("province");
                String remark = element.getString("remark");
                String s5_ip = element.getString("s5_ip");
                String s5_port = element.getString("s5_port");
                String s5_user = element.getString("s5_user");
                String s5_pwd = element.getString("s5_pwd");

            }
        }
        return null;
    }

}
