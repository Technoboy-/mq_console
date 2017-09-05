package com.alibaba.rocketmq.task;

import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.admin.ConsumeStats;
import com.alibaba.rocketmq.common.protocol.body.BrokerStatsData;
import com.alibaba.rocketmq.common.protocol.body.ConsumerConnection;
import com.alibaba.rocketmq.common.protocol.body.GroupList;
import com.alibaba.rocketmq.config.ConfigureInitializer;
import com.alibaba.rocketmq.store.stats.BrokerStatsManager;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.dfire.sm.bo.DDUser;
import com.dfire.sm.enumClass.MsType;
import com.dfire.sm.service.IDingDingService;
import com.dfire.sm.service.IMsService;
import com.twodfire.util.UuidUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.alibaba.rocketmq.task.CommonTpsTask.topics;

@Slf4j
@Component
public class AlertMonitorTask implements InitializingBean {

    @Autowired
    public DefaultMQAdminExt defaultMQAdminExt;

    @Autowired
    public ConfigureInitializer configureInitializer;

    @Resource
    IDingDingService dingService;

    @Resource
    IMsService msService;

    //当消息堆积报警时，计数发短信，防止连续重复发送
    Map<String, Long> mapCache = new ConcurrentHashMap<>();

    //计数，防止tps不准
    Map<String, Long> countCache = new ConcurrentHashMap<>();

    @Resource
    ConTopicTpsTask conTopicTpsTask;

    @Resource
    CommonTpsTask commonTpsTask;

    @Resource
    ProTopicTpsTask proTopicTpsTask;

    private AtomicLong aLong = new AtomicLong(0);

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    private static Properties properties = new Properties();
    
    private List<DDUser> ddUsers = new ArrayList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
    	final InputStream in = AlertMonitorTask.class.getResourceAsStream("/config.properties");
    	properties.clear();
        properties.load(in);
        buildDingList();
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    long andIncrement = aLong.getAndIncrement();
                    boolean ifDo = andIncrement % 5 == 0 || andIncrement == 0;
                    if (ifDo) {
                        commonTpsTask.fetchAllTopic();
                    }
                    monitor();
                    if (ifDo) {
                        proTopicTpsTask.getSats();
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private String getMobileList(){
    	return  properties.getProperty("mobile_list");
    }
    
    private void buildDingList(){
    	String dingList = properties.getProperty("dingding_list");
    	for(String id : dingList.split(";")){
    		DDUser ddUser = new DDUser();
    		ddUser.setId(id);
            ddUsers.add(ddUser);
    	}
    }

    @Data
    private class MessageDiff {
        String topic;
        String consumerGroup;
        long diff;
        long time;

        @Override
        public String toString() {
            return "RocketMQ出现消息堆积，topic=" + topic + "；消费组=" + consumerGroup + "；堆积量="
                    + diff + "；预估消费时间=" + time / 60 + "分钟。";
        }
    }

    public void monitor() {
        if (Boolean.TRUE.toString().equals(getProperty("stop"))) return;
        /**
         * 检查各consumerGroup堆积量
         */
        defaultMQAdminExt.setInstanceName(Long.toString(System.currentTimeMillis()));
        //把消费出现堆积的topic、消费组、堆积数都记录下来，一起发送短信
        List<MessageDiff> sendList = new ArrayList<>();
        try {
            for (String topic : topics) {
                if (!topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) { // 不是以那个前缀开头
                    GroupList list = defaultMQAdminExt.queryTopicConsumeByWho(topic); // 得到该 Topic 下的所有消费者列表
                    if (list == null) {
                        continue;
                    }
                    HashSet<String> groupList = list.getGroupList();
                    if (groupList == null || groupList.size() == 0) {
                        continue;
                    }
                    for (Object aGroupList : groupList) {
                        String consumerGroup = String.valueOf(aGroupList);
                        try {
                            ConsumeStats consumeStats = null;
                            try {
                                consumeStats = defaultMQAdminExt.examineConsumeStats(consumerGroup);
                            } catch (Exception e) {
                                log.warn("examineConsumeStats exception, " + consumerGroup, e);
                            }
                            ConsumerConnection cc = null;
                            try {
                                cc = defaultMQAdminExt.examineConsumerConnectionInfo(consumerGroup);
                            } catch (Exception e) {
                                log.warn("examineConsumerConnectionInfo exception, " + consumerGroup, e);
                            }
                            String topicGroup = topic + "@" + consumerGroup;
                            BrokerStatsData brokerStatsData = defaultMQAdminExt.ViewBrokerStatsData(configureInitializer.getBrokerAddr(),
                                    BrokerStatsManager.GROUP_GET_NUMS, topicGroup);
                            Double tps = Math.max(brokerStatsData.getStatsMinute().getTps(), consumeStats.getConsumeTps());
                            ConTopicTpsTask.cache.put(topicGroup, brokerStatsData);
                            if (tps <= 0 || cc.getConnectionSet().size() == 0) {
                                continue;
                            }
                            long diff = consumeStats.computeTotalDiff();
                            Double time = Double.valueOf(diff) / tps;
                            if (time > 25 * 60 || diff > 150_0000) {
                                long now = System.currentTimeMillis();
                                //20分钟内不重复报警
                                if (mapCache.containsKey(consumerGroup) && (now - mapCache.get(consumerGroup)) < 20 * 60 * 1000) {
                                    continue;
                                }
                                //计数2次再报警
                                if (countCache.containsKey(consumerGroup) && (now - countCache.get(consumerGroup)) < 1.5 * 60 * 1000) {
                                    countCache.remove(consumerGroup);
                                } else {
                                    countCache.put(consumerGroup, now);
                                    continue;
                                }
                                MessageDiff m = new MessageDiff();
                                m.setTopic(topic);
                                m.setConsumerGroup(consumerGroup);
                                m.setDiff(diff);
                                m.setTime(time.intValue());
                                sendList.add(m);
                                mapCache.put(consumerGroup, now);
                                log.error(m.toString());
                            }
                        } catch (Exception e) {
                            log.warn("examineConsumeStats or examineConsumerConnectionInfo exception, "
                                    + consumerGroup, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        if (!"true".equals(getProperty("monitor"))) return;
        if (!sendList.isEmpty()) {
            for (MessageDiff m : sendList) {
                if (m.getDiff() < 50000) continue;

                //发送短信
                String alertMsg = m.toString();
                Map<String, String> map = new HashMap();
                map.put("alertInfo", alertMsg);
                if (m.getConsumerGroup().endsWith("hbase") || m.getConsumerGroup().contains("realTime")) {
                    msService.sendNote(UuidUtil.getUUID(), "DS_31477626171", "18821273863", map, MsType.BUSINESS, 0);
                    continue;
                }
                if (m.getConsumerGroup().contains("solr")) {
                    msService.sendNote(UuidUtil.getUUID(), "DS_31477626171", "15868113480", map, MsType.BUSINESS, 0);
                    continue;
                }
                String member = getProperty("member_list");
                if (StringUtils.isNotBlank(member)) {
                    String[] memberList = StringUtils.splitByWholeSeparator(member, "|");
                    for (String caller : memberList) {
                        String[] detail = StringUtils.splitByWholeSeparator(caller, ":");
                        if (m.getConsumerGroup().endsWith(detail[0])) {
                            msService.sendNote(UuidUtil.getUUID(), "DS_31477626171", detail[1], map, MsType.BUSINESS, 0);
                        }
                    }
                }
                msService.sendNote(UuidUtil.getUUID(), "DS_31477626171", getMobileList(), map, MsType.BUSINESS, 0);
                dingService.sendMsgText(ddUsers, "警告：" + alertMsg, "");
            }
        }
        conTopicTpsTask.getSats();
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

}
