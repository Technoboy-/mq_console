package com.alibaba.rocketmq.task;

import com.alibaba.rocketmq.common.protocol.body.BrokerStatsData;
import com.alibaba.rocketmq.config.ConfigureInitializer;
import com.alibaba.rocketmq.service.ProTopicTpsService;
import com.alibaba.rocketmq.store.stats.BrokerStatsManager;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.alibaba.rocketmq.vo.ProTopicTpsVo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.alibaba.rocketmq.task.AlertMonitorTask.getProperty;

/**
 * INFO:统计生产者的 TPS
 * @Scheduled(cron = "1 * *  * * ? ") 表示每  1 分钟执行一次
 */
@Slf4j
@Component
public class ProTopicTpsTask extends CommonTpsTask {
    private static final Logger logger = LoggerFactory.getLogger(ConTopicTpsTask.class);

    @Autowired
    private ProTopicTpsService proTopicTpsService;

    @Autowired
    public DefaultMQAdminExt defaultMQAdminExt;

    @Autowired
    public ConfigureInitializer configureInitializer;

    public void getSats() {
        if (Boolean.TRUE.toString().equals(getProperty("stop"))) return;
        logger.error("----get producer tps");
        if ("pre".equals(getProperty("monitor"))) return;
        if (null == topics || topics.size() == 0 || null == defaultMQAdminExt) {
            logger.warn("topics is null -");
            return;
        }
        ProTopicTpsVo tpsVo = new ProTopicTpsVo();
        tpsVo.setProTopic("total");
        for (String arg : topics) {
            try {
                BrokerStatsData brokerStatsData = defaultMQAdminExt.ViewBrokerStatsData(configureInitializer.getBrokerAddr(), BrokerStatsManager.TOPIC_PUT_NUMS, arg);
                proTopicTpsService.insert(bulidTopicTpsVo(brokerStatsData, arg, tpsVo));
                logger.info(arg + ":" + brokerStatsData.toJson());
            } catch (Exception e) {
                logger.warn(arg + " producer get tps error ");
            }
        }
        proTopicTpsService.insert(tpsVo);
    }


    public ProTopicTpsVo bulidTopicTpsVo(BrokerStatsData b, String topic, ProTopicTpsVo tpsVo) {
        ProTopicTpsVo proTopicTpsVo = new ProTopicTpsVo();
        proTopicTpsVo.setProAvgpt(b.getStatsMinute().getAvgpt());
        proTopicTpsVo.setProSum(b.getStatsMinute().getSum());
        proTopicTpsVo.setProTps(b.getStatsMinute().getTps());
        proTopicTpsVo.setProTopic(topic);
        tpsVo.setProAvgpt(tpsVo.getProAvgpt() + proTopicTpsVo.getProAvgpt());
        tpsVo.setProSum(tpsVo.getProSum() + proTopicTpsVo.getProSum());
        tpsVo.setProTps(tpsVo.getProTps() + proTopicTpsVo.getProTps());
        return proTopicTpsVo;
    }


}
