package com.zealsinger.count.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.constants.RedisKeyConstants;
import com.zealsinger.count.domain.dto.AggregationCountCollectUncollectNoteMqDTO;
import com.zealsinger.count.domain.dto.CountCollectUnCollectNoteMqDTO;
import com.zealsinger.count.domain.entity.NoteCollection;
import com.zealsinger.count.domain.entity.NoteCount;
import com.zealsinger.count.domain.entity.UserCount;
import com.zealsinger.count.domain.enums.NoteCollectionStatusEnum;
import com.zealsinger.count.mapper.NoteCountMapper;
import com.zealsinger.count.mapper.UserCountMapper;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
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

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private UserCountMapper userCountMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 笔记收藏数入库】, {}...", body);
        List<AggregationCountCollectUncollectNoteMqDTO> countList = new ArrayList<>();
        try {
            countList = JsonUtil.parseList(body, AggregationCountCollectUncollectNoteMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if(CollUtil.isNotEmpty(countList)){
            countList.forEach(item -> {
                Long creatorId = item.getCreatorId();
                Long noteId = item.getNoteId();
                Integer count = item.getCount();
                transactionTemplate.execute(status ->{
                    try{
                        LambdaQueryWrapper<NoteCount> queryWrapper = new LambdaQueryWrapper<>();
                        queryWrapper.eq(NoteCount::getNoteId,noteId);
                        NoteCount noteCount = noteCountMapper.selectOne(queryWrapper);
                        if(Objects.isNull(noteCount)){
                            noteCount = NoteCount.builder().noteId(noteId)
                                    .likeTotal(0L)
                                    .collectTotal(Long.valueOf(count))
                                    .commentTotal(0L)
                                    .createTime(LocalDateTime.now())
                                    .updateTime(LocalDateTime.now())
                                    .build();
                        }else{
                            noteCount.setCollectTotal(noteCount.getCollectTotal()+count);
                        }
                        noteCountMapper.insertOrUpdate(noteCount);

                        LambdaQueryWrapper<UserCount> queryWrapper1 = new LambdaQueryWrapper<>();
                        queryWrapper1.eq(UserCount::getUserId,creatorId);
                        UserCount userCount = userCountMapper.selectOne(queryWrapper1);
                        if(Objects.isNull(userCount)){
                            userCount= UserCount.builder()
                                    .userId(creatorId)
                                    .fansTotal(0L)
                                    .followingTotal(0L)
                                    .noteTotal(0L)
                                    .likeTotal(0L)
                                    .collectTotal(Long.valueOf(count))
                                    .build();
                        }else{
                            userCount.setCollectTotal(userCount.getCollectTotal()+count);
                        }
                        userCountMapper.insertOrUpdate(userCount);

                    }catch (Exception e){
                        status.setRollbackOnly(); // 标记事务为回滚
                        log.error("", e);
                    }
                    return false;
                });
            });
        }
    }
}
