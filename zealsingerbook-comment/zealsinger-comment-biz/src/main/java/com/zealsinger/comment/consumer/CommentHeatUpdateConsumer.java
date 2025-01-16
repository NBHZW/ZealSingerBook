package com.zealsinger.comment.consumer;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Sets;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.comment.constant.MQConstants;
import com.zealsinger.comment.domain.entry.Comment;
import com.zealsinger.comment.mapper.CommentMapper;
import com.zealsinger.comment.util.HeatUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ MQConstants.TOPIC_COMMENT_HEAT_UPDATE,
        topic = MQConstants.TOPIC_COMMENT_HEAT_UPDATE
)
@Slf4j
public class CommentHeatUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private CommentMapper commentMapper;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(300)   // 一批次最多聚合 300 条
            .linger(Duration.ofSeconds(2)) // 多久聚合一次（2s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    private void consumeMessage(List<String> bodys) throws JsonProcessingException {
        Set<Long> commentIds = Sets.newHashSet();
        for (String body : bodys) {
            Set<Long> set = JsonUtil.parseSet(body, Long.class);
            commentIds.addAll(set);
        }
        log.info("去重后要更新热值评论ID；列表===> {}", commentIds);
        LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Comment::getId, commentIds);
        List<Comment> comments = commentMapper.selectList(queryWrapper);
        comments.forEach(comment -> comment.setHeat(HeatUtil.calculateHeat(comment.getLikeTotal(),comment.getChildCommentTotal())));
        commentMapper.updateById(comments);
    }


    @Override
    public void onMessage(String message) {
        bufferTrigger.enqueue(message);
    }
}
