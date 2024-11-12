package com.zealsinger.count.consumer;

import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.domain.entity.NoteCount;
import com.zealsinger.count.domain.entity.NoteLike;
import com.zealsinger.count.mapper.NoteCountMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsinger_group"+ MQConstant.TOPIC_COUNT_NOTE_LIKE_2_DB,
        topic = MQConstant.TOPIC_COUNT_NOTE_LIKE_2_DB
)
public class CountNoteLikeDBConsumer implements RocketMQListener<String> {
    @Resource
    private NoteCountMapper noteCountMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String message) {
        // 令牌限流
        rateLimiter.acquire();

        // 操作数据库

        Map<Long,Integer> countMap;
        try {
            countMap = JsonUtil.parseMap(message,Long.class,Integer.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if(!Objects.isNull(countMap)){
            countMap.forEach((k,v)->{
                LambdaQueryWrapper<NoteCount> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteCount::getNoteId,k);
                NoteCount noteCount = noteCountMapper.selectOne(queryWrapper);
                if(Objects.isNull(noteCount)){
                    noteCount = NoteCount.builder().noteId(k)
                            .likeTotal(Long.valueOf(v))
                            .collectTotal(0L)
                            .commentTotal(0L)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                }else{
                    noteCount.setLikeTotal(noteCount.getLikeTotal()+v);
                }
                noteCountMapper.insertOrUpdate(noteCount);
            });
        }
    }
}
