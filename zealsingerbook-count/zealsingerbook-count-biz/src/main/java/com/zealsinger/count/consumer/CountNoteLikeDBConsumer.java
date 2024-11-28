package com.zealsinger.count.consumer;

import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.domain.dto.AggregationCountLikeUnlikeNoteMqDTO;
import com.zealsinger.count.domain.entity.NoteCount;
import com.zealsinger.count.domain.entity.NoteLike;
import com.zealsinger.count.domain.entity.UserCount;
import com.zealsinger.count.mapper.NoteCountMapper;
import com.zealsinger.count.mapper.UserCountMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private UserCountMapper userCountMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String message) {
        // 令牌限流
        rateLimiter.acquire();

        // 操作数据库

        List<AggregationCountLikeUnlikeNoteMqDTO> countList = null;
        try {
            countList = JsonUtil.parseList(message, AggregationCountLikeUnlikeNoteMqDTO.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if(!Objects.isNull(countList)){
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
                                    .likeTotal(Long.valueOf(count))
                                    .collectTotal(0L)
                                    .commentTotal(0L)
                                    .createTime(LocalDateTime.now())
                                    .updateTime(LocalDateTime.now())
                                    .build();
                        }else{
                            noteCount.setLikeTotal(noteCount.getLikeTotal()+count);
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
                                    .likeTotal(Long.valueOf(count))
                                    .collectTotal(0L)
                                    .build();
                        }else{
                            userCount.setLikeTotal(userCount.getLikeTotal()+count);
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
