package com.zealsinger.count.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.constants.RedisKeyConstants;
import com.zealsinger.count.domain.dto.CountCollectUnCollectNoteMqDTO;
import com.zealsinger.count.domain.entity.NoteCollection;
import com.zealsinger.count.domain.entity.NoteCount;
import com.zealsinger.count.domain.enums.NoteCollectionStatusEnum;
import com.zealsinger.count.mapper.NoteCountMapper;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ MQConstant.TOPIC_COUNT_NOTE_COLLECT_2_DB,
        topic = MQConstant.TOPIC_COUNT_NOTE_COLLECT_2_DB
)
public class CountNoteCollectDBConsumer implements RocketMQListener<String> {
    @Resource
    private NoteCountMapper noteCountMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 笔记收藏数入库】, {}...", body);
        Map<Long,Integer> countMap = new HashMap<>();
        try {
            countMap = JsonUtil.parseMap(body,Long.class,Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if(CollUtil.isNotEmpty(countMap)){
            countMap.forEach((k,v)->{
                LambdaQueryWrapper<NoteCount> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteCount::getNoteId,k);
                NoteCount noteCount = noteCountMapper.selectOne(queryWrapper);
                if(Objects.isNull(noteCount)){
                    noteCount = NoteCount.builder().noteId(k)
                            .likeTotal(0L)
                            .collectTotal(Long.valueOf(v))
                            .commentTotal(0L)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                }else{
                    noteCount.setCollectTotal(noteCount.getCollectTotal()+v);
                }
                noteCountMapper.insertOrUpdate(noteCount);
            });
        }
    }
}
