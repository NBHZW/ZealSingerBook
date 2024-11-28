package com.zealsinger.count.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.constants.RedisKeyConstants;
import com.zealsinger.count.domain.dto.NoteOperateMqDTO;
import com.zealsinger.count.domain.entity.UserCount;
import com.zealsinger.count.mapper.UserCountMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group" + MQConstant.TOPIC_NOTE_OPERATE, // Group 组
        topic = MQConstant.TOPIC_NOTE_OPERATE // 主题 Topic
)
@Slf4j
public class CountNotePublishConsumer implements RocketMQListener<Message> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserCountMapper userCountMapper;

    @Override
    public void onMessage(Message message) {
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();

        log.info("==> CountNotePublishConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断笔记操作类型
        if (Objects.equals(tags, MQConstant.TAG_NOTE_PUBLISH)) { // 笔记发布
            handleTagMessage(bodyJsonStr, 1);
        } else if (Objects.equals(tags, MQConstant.TAG_NOTE_DELETE)) { // 笔记删除
            handleTagMessage(bodyJsonStr, -1);
        }
    }

    /**
     * 笔记发布、删除
     * @param bodyJsonStr
     */
    private void handleTagMessage(String bodyJsonStr, long count) {
        // 消息体 JSON 字符串转 DTO
        NoteOperateMqDTO noteOperateMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, NoteOperateMqDTO.class);

        if (Objects.isNull(noteOperateMqDTO)) return;

        // 笔记发布者 ID
        Long creatorId = noteOperateMqDTO.getCreatorId();

        // 更新 Redis 中用户维度的计数 Hash
        String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
        // 判断 Redis 中 Hash 是否存在
        boolean isCountUserExisted = redisTemplate.hasKey(countUserRedisKey);

        // 若存在才会更新
        // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
        if (isCountUserExisted) {
            // 对目标用户 Hash 中的笔记发布总数，进行加减操作
            redisTemplate.opsForHash().increment(countUserRedisKey, RedisKeyConstants.FIELD_NOTE_TOTAL, count);
        }

        // 更新 t_user_count 表
        LambdaQueryWrapper<UserCount> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCount::getUserId, creatorId);
        UserCount userCount = userCountMapper.selectOne(queryWrapper);
        if(Objects.isNull(userCount)){
            userCount = UserCount.builder().userId(creatorId)
                    .fansTotal(0L)
                    .followingTotal(0L)
                    .noteTotal(count)
                    .likeTotal(0L)
                    .collectTotal(0L)
                    .build();
        }else{
            userCount.setNoteTotal(userCount.getNoteTotal() + count);
        }
        userCountMapper.insertOrUpdate(userCount);
    }


}
