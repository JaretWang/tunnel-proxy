package com.dataeye.proxy.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/4/7 13:15
 * @description
 */
@Component
public class SpringTool implements ApplicationContextAware {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 服务器启动，Spring容器初始化时，当加载了当前类为bean组件后，
     * 将会调用下面方法注入ApplicationContext实例
     */
    @Override
    public void setApplicationContext(@NotNull ApplicationContext arg0) throws BeansException {
        this.applicationContext = arg0;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 外部调用这个getBean方法就可以手动获取到bean
     * 用bean组件的name来获取bean
     *
     * @param beanName
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    public <T> T getBean(Class<T> c) {
        return applicationContext.getBean(c);
    }

}
