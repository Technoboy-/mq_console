package com.alibaba.rocketmq.task;

import com.alibaba.rocketmq.service.TopicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * INFO:这里的方法将 每 5 分钟抓取 Topic、Group，这里时间定义长点
 */
@Slf4j
@Component
public class CommonTpsTask {

    @Autowired
    public TopicService topicService;

    public static List<String> topics;
    public static List<String> topicGroups;

    /**
     * 抓取所有的 Topic
     */
    public void fetchAllTopic() {
        log.error("get topic list");
        topics = topicService.fetchAllTopic();
        fetchGroupByTopic();
    }


    public void fetchGroupByTopic() {
        topicGroups = topicService.fetchGroupByTopic();
    }

}
