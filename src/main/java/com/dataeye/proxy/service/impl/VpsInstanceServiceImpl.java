package com.dataeye.proxy.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.dao.VpsInstanceMapper;
import com.dataeye.proxy.service.VpsInstanceService;
import org.springframework.stereotype.Service;

/**
 * (VpsInstance)表服务实现类
 *
 * @author makejava
 * @since 2022-11-07 17:27:57
 */
@Service("vpsInstanceService")
public class VpsInstanceServiceImpl extends ServiceImpl<VpsInstanceMapper, VpsInstance> implements VpsInstanceService {

}

