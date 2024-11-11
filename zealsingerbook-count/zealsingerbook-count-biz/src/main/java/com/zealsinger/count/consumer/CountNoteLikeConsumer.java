package com.zealsinger.count.consumer;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.constants.RedisKeyConstants;
import com.zealsinger.count.domain.dto.CountNoteLikeUnlikeNoteMqDTO;
import com.zealsinger.count.domain.entity.NoteCount;
import com.zealsinger.count.domain.enums.NoteLikeTypeEnum;
import com.zealsinger.count.domain.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsinger_group"+ MQConstant.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstant.TOPIC_COUNT_NOTE_LIKE
)
public class CountNoteLikeConsumer implements RocketMQListener<String> {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String message) {
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtil.ObjToJsonString(bodys));
        List<CountNoteLikeUnlikeNoteMqDTO> list = bodys.stream().map(s -> JsonUtil.JsonStringToObj(s, CountNoteLikeUnlikeNoteMqDTO.class)).toList();
        Map<Long, List<CountNoteLikeUnlikeNoteMqDTO>> bodysMap = list.stream().collect(Collectors.groupingBy(CountNoteLikeUnlikeNoteMqDTO::getNoteId));
        Map<Long,Integer> countMap = new HashMap<>();
        bodysMap.forEach((nodeId,likeUnlikeOptions)->{
            int endCOunt = 0;
            for (CountNoteLikeUnlikeNoteMqDTO likeUnlikeOption : likeUnlikeOptions) {
                NoteLikeTypeEnum typeEnum = NoteLikeTypeEnum.getByCode(likeUnlikeOption.getLikeStatus());
                if(typeEnum==null){
                    continue;
                }
                switch (typeEnum) {
                    case LIKE: endCOunt++; break;
                    case UNLIKE: endCOunt--; break;
                }
            }
            countMap.put(nodeId,endCOunt);
        });
        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtil.ObjToJsonString(bodysMap));

        // 将数据添加到redis缓存中
        countMap.forEach((k,v)->{
            String countNoteLikeUnlikeRedisKey = RedisKeyConstants.buildCountNoteKey(k);
            Boolean isExisted = redisTemplate.hasKey(countNoteLikeUnlikeRedisKey);
            if(Boolean.TRUE.equals(isExisted)){
                // 缓存存在就新增 不存在则不会操作
                redisTemplate.opsForHash().increment(countNoteLikeUnlikeRedisKey,RedisKeyConstants.FIELD_LIKE_TOTAL,v);
            }
        });
        // 异步计数落库
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(bodys)).build();
        rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_NOTE_LIKE_2_DB,message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：笔记点赞数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：笔记点赞数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
