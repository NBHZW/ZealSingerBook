package com.zealsinger.count.consumer;

import cn.hutool.core.collection.CollUtil;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.constants.RedisKeyConstants;
import com.zealsinger.count.domain.dto.AggregationCountCollectUncollectNoteMqDTO;
import com.zealsinger.count.domain.dto.CountCollectUnCollectNoteMqDTO;
import com.zealsinger.count.domain.enums.NoteCollectionStatusEnum;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ MQConstant.TOPIC_COUNT_NOTE_COLLECT,
        topic = MQConstant.TOPIC_COUNT_NOTE_COLLECT
)
public class CountNoteCollectConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

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
        log.info("==> 【笔记收藏数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合消息, {}", JsonUtil.ObjToJsonString(bodys));
        if(CollUtil.isNotEmpty(bodys)){
            List<CountCollectUnCollectNoteMqDTO> countCollectUnCollectNoteMqDTOS = bodys.stream().map(s -> JsonUtil.JsonStringToObj(s, CountCollectUnCollectNoteMqDTO.class)).toList();
            Map<Long, List<CountCollectUnCollectNoteMqDTO>> groupMap= countCollectUnCollectNoteMqDTOS.stream().collect(Collectors.groupingBy(CountCollectUnCollectNoteMqDTO::getNoteId));
            List<AggregationCountCollectUncollectNoteMqDTO> countList = new ArrayList<>();
            for (Map.Entry<Long, List<CountCollectUnCollectNoteMqDTO>> entry : groupMap.entrySet()) {
                List<CountCollectUnCollectNoteMqDTO> list = entry.getValue();
                // 最终的计数值，默认为 0
                int finalCount = 0;
                Long creator = null;
                for (CountCollectUnCollectNoteMqDTO countCollectUnCollectNoteMqDTO : list) {
                    creator = countCollectUnCollectNoteMqDTO.getCreatorId();
                    // 获取操作类型
                    Integer type = countCollectUnCollectNoteMqDTO.getStatus();
                    if(Objects.equals(type, NoteCollectionStatusEnum.COLLECTION.getCode())){
                        finalCount++;
                    }
                    if(Objects.equals(type, NoteCollectionStatusEnum.UNCOLLECTION.getCode())){
                        finalCount--;
                    }
                }
                // 将分组后统计出的最终计数，存入 countMap 中
                countList.add(AggregationCountCollectUncollectNoteMqDTO.builder()
                                .noteId(entry.getKey())
                                .creatorId(creator)
                                .count(finalCount)
                                .build());
            }
            log.info("## 【笔记收藏数】聚合后的计数数据: {}", JsonUtil.ObjToJsonString(countList));
            countList.forEach(item->{
                String redisHashKey = RedisKeyConstants.buildCountNoteKey(item.getNoteId());
                Boolean isExisted = redisTemplate.hasKey(redisHashKey);
                if(Boolean.TRUE.equals(isExisted)){
                    redisTemplate.opsForHash().increment(redisHashKey,RedisKeyConstants.FIELD_COLLECT_TOTAL, item.getCount());
                }

                String buildCountUserKey = RedisKeyConstants.buildCountUserKey(item.getCreatorId());
                isExisted = redisTemplate.hasKey(buildCountUserKey);
                if(Boolean.TRUE.equals(isExisted)){
                    redisTemplate.opsForHash().increment(buildCountUserKey,RedisKeyConstants.FIELD_COLLECT_TOTAL,item.getCount());
                }
            });

            // 异步MQ落库
            Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(countList)).build();
            // 落库
            rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_NOTE_COLLECT_2_DB, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("==> 【计数服务：笔记收藏数入库】MQ 发送成功，SendResult: {}", sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("==> 【计数服务：笔记收藏数入库】MQ 发送异常: ", throwable);
                }
            });
        }
    }
}
