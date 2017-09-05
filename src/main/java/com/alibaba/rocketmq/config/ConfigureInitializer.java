package com.alibaba.rocketmq.config;


import com.alibaba.rocketmq.common.MixAll;


/**
 * 把需要补充的初始化环境变量正式的放入系统属性中
 * 
 * @author yankai913@gmail.com
 * @date 2014-2-10
 * 
 */
public class ConfigureInitializer {
    // nameSer 的地址
    private String namesrvAddr;

    // broker 地址
    private String brokerAddr;


    public String getBrokerAddr() {
        return brokerAddr;
    }

    public void setBrokerAddr(String brokerAddr) {
        this.brokerAddr = brokerAddr;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public void init() {
        System.setProperty(MixAll.NAMESRV_ADDR_PROPERTY, namesrvAddr);
    }
}
