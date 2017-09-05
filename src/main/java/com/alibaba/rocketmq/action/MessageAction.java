package com.alibaba.rocketmq.action;

import com.alibaba.fastjson.JSON;
import com.alibaba.rocketmq.client.consumer.DefaultMQPullConsumer;
import com.alibaba.rocketmq.client.consumer.PullResult;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.Table;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.alibaba.rocketmq.config.ConfigureInitializer;
import com.alibaba.rocketmq.remoting.exception.RemotingConnectException;
import com.alibaba.rocketmq.service.MessageService;
import org.apache.commons.cli.Option;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * @author yankai913@gmail.com
 * @date 2014-2-17
 */
@Controller
@RequestMapping("/message")
public class MessageAction extends AbstractAction {

    private Logger logger = LoggerFactory.getLogger(MessageAction.class);

    @Autowired
    MessageService messageService;

    @Autowired
    ConfigureInitializer configureInitializer;


    protected String getFlag() {
        return "message_flag";
    }


    /**
     * 通过 消息ID 来查询消息
     *
     * @param map
     * @param request
     * @param msgId
     * @return
     */
    @RequestMapping(value = "/queryMsgById.do", method = {RequestMethod.GET, RequestMethod.POST})
    public String queryMsgById(ModelMap map, HttpServletRequest request,
                               @RequestParam(required = false) String msgId) {

        Collection<Option> options = messageService.getOptionsForQueryMsgById();
        //Bug fix.
        putPublicAttribute(map, "queryMsgById", options, request);
        try {
            if (request.getMethod().equals(GET)) {

            }
            if (StringUtils.isNotBlank(msgId)) {
                checkOptions(options);
                Table table = messageService.queryMsgById(msgId.trim());
                putTable(map, table);
            } else if (request.getMethod().equals(POST)) {
                checkOptions(options);
                Table table = messageService.queryMsgById(msgId);
                putTable(map, table);
            }
        } catch (Throwable t) {
            if (t instanceof RemotingConnectException) {
                putAlertMsg(new Throwable("抱歉，无法查到您的MQ信息，您查询的msgId很可能是ONS消息。请确认是本环境的RocketMQ消息"), map);
            } else {
                putAlertMsg(t, map);
            }
        }
        return TEMPLATE;
    }


    /**
     * 通过 Key 来查询消息
     *
     * @param map
     * @param request
     * @param topic
     * @param msgKey
     * @param fallbackHours
     * @return
     */
    @RequestMapping(value = "/queryMsgByKey.do", method = {RequestMethod.GET, RequestMethod.POST})
    public String queryMsgByKey(ModelMap map, HttpServletRequest request,
                                @RequestParam(required = false) String topic, @RequestParam(required = false) String msgKey,
                                @RequestParam(required = false) String fallbackHours) {
        Collection<Option> options = messageService.getOptionsForQueryMsgByKey();
        putPublicAttribute(map, "queryMsgByKey", options, request);
        try {
            if (request.getMethod().equals(GET)) {

            } else if (request.getMethod().equals(POST)) {
                checkOptions(options);
                Table table = messageService.queryMsgByKey(topic.trim(), msgKey.trim(), fallbackHours);
                putTable(map, table);
            } else {
                throwUnknowRequestMethodException(request);
            }
        } catch (Throwable t) {
            if (t instanceof RemotingConnectException) {
                putAlertMsg(new Throwable("抱歉，无法查到您的MQ信息，您查询的msgId很可能是ONS消息。请确认是本环境的RocketMQ消息"), map);
            } else {
                putAlertMsg(t, map);
            }
        }
        return TEMPLATE;
    }


    /**
     * 通过 offset 来查看消息
     *
     * @param map
     * @param request
     * @param topic
     * @param brokerName
     * @param queueId
     * @param offset
     * @return
     */
    @RequestMapping(value = "/queryMsgByOffset.do", method = {RequestMethod.GET, RequestMethod.POST})
    public String queryMsgByOffset(ModelMap map, HttpServletRequest request,
                                   @RequestParam(required = false) String topic, @RequestParam(required = false) String brokerName,
                                   @RequestParam(required = false) String queueId, @RequestParam(required = false) String offset) {
        Collection<Option> options = messageService.getOptionsForQueryMsgByOffset();
        putPublicAttribute(map, "queryMsgByOffset", options, request);
        try {
            if (request.getMethod().equals(GET)) {

            } else if (request.getMethod().equals(POST)) {
                checkOptions(options);
                Table table = messageService.queryMsgByOffset(topic.trim(), brokerName.trim(), queueId.trim(), offset.trim());
                putTable(map, table);
            } else {
                throwUnknowRequestMethodException(request);
            }
        } catch (Throwable t) {
            if (t instanceof RemotingConnectException) {
                putAlertMsg(new Throwable("抱歉，无法查到您的MQ信息，您查询的msgId很可能是ONS消息。请确认是本环境的RocketMQ消息"), map);
            } else {
                putAlertMsg(t, map);
            }
        }
        return TEMPLATE;
    }

    @RequestMapping(value = "/queryMsgByTopicAndTag.do", method = {RequestMethod.GET, RequestMethod.POST})
    public String queryMsgByTopicAndTag(ModelMap map, HttpServletRequest request,
                                        @RequestParam(required = false) String topic, @RequestParam(required = false) String subExpression,
                                        @RequestParam(required = false) Integer lastTime) {
        Collection<Option> options = messageService.getOptionsForQueryMsgByTag();
        putPublicAttribute(map, "queryMsgByTopicAndTag", options, request);
        if (request.getMethod().equals(GET)) {

        } else if (request.getMethod().equals(POST)) {
            DefaultMQPullConsumer consumer = new DefaultMQPullConsumer(MixAll.TOOLS_CONSUMER_GROUP);
            try {
                checkOptions(options);
                if (lastTime == null || lastTime > 60 * 12) {
                    lastTime = 5;
                }
                ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(20);
                consumer.start();
                Set<MessageQueue> mqs = consumer.fetchSubscribeMessageQueues(topic);
                for (MessageQueue mq : mqs) {
                    long maxOffset = consumer.maxOffset(mq);
                    long minOffset = consumer.searchOffset(mq, System.currentTimeMillis() - lastTime * 60 * 1000);
                    READQ:
                    for (long offset = minOffset; offset <= maxOffset; ) {
                        try {
                            PullResult pullResult = consumer.pull(mq, subExpression, offset, 32);
                            offset = pullResult.getNextBeginOffset();
                            switch (pullResult.getPullStatus()) {
                                case FOUND:
                                    for (MessageExt msg : pullResult.getMsgFoundList()) {
                                        if (queue.offer(msg.getMsgId())) break READQ;
                                    }
                                    break;
                                case NO_MATCHED_MSG:
                                case NO_NEW_MSG:
                                case OFFSET_ILLEGAL:
                                    break READQ;
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }
                }
                int size = queue.size();
                Map<String, String> result = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    result.put("msgId " + i + " = ", queue.poll());
                }
                putTable(map, Table.Map2VTable(result));
            } catch (Throwable t) {
                if (t.getMessage().equals("invalid row or column")){
                    putAlertMsg(new Throwable("query result : none of them were found"), map);
                }else {
                    putAlertMsg(t, map);
                }
            }
            finally {
                consumer.shutdown();
            }
        }
        return TEMPLATE;
    }

    @Override
    protected String getName() {
        return "Message";
    }
}
