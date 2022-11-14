package com.dataeye.proxy.bean.dto;

import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * @author jaret
 * @date 2022/11/7 16:55
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VpsInstance extends Model<VpsInstance> implements Serializable {
    private static final long serialVersionUID = -29307840965887930L;
    /**
     * 自增id
     */
    private Integer id;
    /**
     * 1有效 0无效
     */
    private Integer valid;
    /**
     * 1国内 2海外
     */
    private Integer type;
    /**
     * vps主机ip
     */
    private String ip;
    /**
     * vps主机SSH连接端口
     */
    private Integer port;
    /**
     * vps账号
     */
    private String username;
    /**
     * vps密码
     */
    private String password;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 获取主键值
     *
     * @return 主键值
     */
    @Override
    public Serializable pkVal() {
        return this.id;
    }



}