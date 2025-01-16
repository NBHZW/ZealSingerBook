package com.zealsinger.comment.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.comment.constant.MQConstants;
import com.zealsinger.comment.constant.RedisKeyConstants;
import com.zealsinger.comment.domain.dto.CountPublishCommentMqDTO;
import com.zealsinger.comment.domain.entry.Comment;
import com.zealsinger.comment.mapper.CommentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Component
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group_first_chirld" + MQConstants.TOPIC_COUNT_NOTE_COMMENT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT // 主题 Topic
)
@Slf4j
public class OneLevelCommentFirstReplyCommentIdUpdateConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;


    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【一级评论 first_reply_comment_id 更新】聚合消息, size: {}", bodys.size());
        log.info("==> 【一级评论 first_reply_comment_id 更新】聚合消息, {}", JsonUtil.ObjToJsonString(bodys));

        // 将聚合后的消息体 Json 转 List<CountPublishCommentMqDTO>
        List<Comment> commentList = Lists.newArrayList();
        bodys.forEach(body -> {
            try {
                List<Comment> list = JsonUtil.parseList(body, Comment.class);
                commentList.addAll(list);
            } catch (Exception e) {
                log.error("", e);
            }
        });
        // 过滤出二级评论的 parent_id（即一级评论 ID），并去重，需要更新对应一级评论的 first_reply_comment_id
        List<Long> parentIds = commentList.stream().filter(countPublishCommentMqDTO -> countPublishCommentMqDTO.getLevel() == 2).map(Comment::getParentId).distinct().toList();

        if (CollUtil.isEmpty(parentIds)) return;

        // 构建 Redis Key
        List<String> keys = parentIds.stream()
                .map(RedisKeyConstants::buildHaveFirstReplyCommentKey).toList();

        // 批量查询 Redis
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        // 提取 Redis 中不存在的评论 ID
        List<Long> missingCommentIds = Lists.newArrayList();
        for (int i = 0; i < values.size(); i++) {
            if (Objects.isNull(values.get(i))) {
                missingCommentIds.add(parentIds.get(i));
            }
        }

        // 存在的一级评论 ID，说明表中对应记录的 first_reply_comment_id 已经有值
        if (CollUtil.isNotEmpty(missingCommentIds)) {
            // TODO: 不存在的，则需要进一步查询数据库来确定，是否要更新记录对应的 first_reply_comment_id 值
            LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(Comment::getId, missingCommentIds);
            List<Comment> comments = commentMapper.selectList(queryWrapper);
            List<Comment> sqlHaveCommentIds = comments.stream().filter(comment -> comment.getFirstReplyCommentId() != 0).toList();
            List<Comment> sqlMissingCommentIds = comments.stream().filter(comment -> comment.getFirstReplyCommentId() == 0).toList();
            threadPoolTaskExecutor.execute(() -> synchronizeCache(sqlHaveCommentIds));
            sqlMissingCommentIds.forEach(comment -> {
                LambdaQueryWrapper<Comment> query = new LambdaQueryWrapper<>();
                query.eq(Comment::getParentId,comment.getId());
                query.eq(Comment::getLevel,2);
                query.orderByAsc(Comment::getCreateTime);
                query.last("limit 1");
                Comment one = commentMapper.selectOne(query);
                comment.setFirstReplyCommentId(one.getId());
                commentMapper.updateById(comment);
            });
            synchronizeCache(sqlMissingCommentIds);
        }
    }

    private void synchronizeCache(List<Comment> commentList){
        ValueOperations<String, Object> operations = redisTemplate.opsForValue();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            commentList.forEach(comment -> {
                String key = RedisKeyConstants.buildHaveFirstReplyCommentKey(comment.getId());
                operations.set(key, 1, RandomUtil.randomInt(5 * 60 * 60), TimeUnit.SECONDS);
            });
            return null;
        });
    }

}
