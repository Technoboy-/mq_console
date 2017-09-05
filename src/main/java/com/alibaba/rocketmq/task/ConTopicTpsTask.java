package com.alibaba.rocketmq.task;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.rocketmq.common.protocol.body.BrokerStatsData;
import com.alibaba.rocketmq.common.protocol.body.BrokerStatsItem;
import com.alibaba.rocketmq.config.ConfigureInitializer;
import com.alibaba.rocketmq.service.ConTopicTpsService;
import com.alibaba.rocketmq.store.stats.BrokerStatsManager;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.alibaba.rocketmq.vo.ConTopicTpsVo;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.alibaba.rocketmq.task.AlertMonitorTask.getProperty;

/**
 * Created by zwj on 2016/1/28.
 * INFO:消费者 TPS 统计
 *
 * @Scheduled(cron = "1 * *  * * ? ") 表示每  1 分钟执行一次
 */
@Component
public class ConTopicTpsTask extends CommonTpsTask {

    private static final Logger logger = LoggerFactory.getLogger(ConTopicTpsTask.class);
    @Autowired
    private ConTopicTpsService conTopicTpsService;

    @Autowired
    public DefaultMQAdminExt defaultMQAdminExt;

    @Autowired
    public ConfigureInitializer configureInitializer;

    static ConcurrentHashMap<String, BrokerStatsData> cache = new ConcurrentHashMap(200);

    public void getSats() {
        if ("pre".equals(getProperty("monitor"))) return;
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
        }
        if (null == topicGroups || topicGroups.size() == 0 || null == defaultMQAdminExt) {
            logger.warn("topicGroups is null ");
            return;
        }
        ConTopicTpsVo conTopicTpsVo = new ConTopicTpsVo();
        conTopicTpsVo.setConTopic("total@total");
        for (String arg : topicGroups) {
            try {
                BrokerStatsData brokerStatsData = cache.get(arg);
                conTopicTpsService.insert(bulidTopicTpsVo(brokerStatsData, arg, conTopicTpsVo));
                logger.info(arg + ":" + brokerStatsData.toJson());
            } catch (Exception e) {
                logger.warn(arg + " consumer get tps error ");
            }
        }
        conTopicTpsService.insert(conTopicTpsVo);
    }


    public ConTopicTpsVo bulidTopicTpsVo(BrokerStatsData b, String topic, ConTopicTpsVo topicTpsVo) {
        ConTopicTpsVo conTopicTpsVo = new ConTopicTpsVo();
        conTopicTpsVo.setConAvgpt(b.getStatsMinute().getAvgpt());
        conTopicTpsVo.setConSum(b.getStatsMinute().getSum());
        conTopicTpsVo.setConTps(b.getStatsMinute().getTps());
        conTopicTpsVo.setConTopic(topic);
        topicTpsVo.setConSum(topicTpsVo.getConSum() + conTopicTpsVo.getConSum());
        topicTpsVo.setConAvgpt(topicTpsVo.getConAvgpt() + conTopicTpsVo.getConAvgpt());
        topicTpsVo.setConTps(topicTpsVo.getConTps() + conTopicTpsVo.getConTps());
        return conTopicTpsVo;
    }


}
