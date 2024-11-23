package com.zealsinger.note.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.util.concurrent.RateLimiter;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.note.constant.RocketMQConstant;
import com.zealsinger.note.domain.dto.LikeUnlikeMqDTO;
import com.zealsinger.note.domain.entity.NoteLike;
import com.zealsinger.note.domain.enums.LikeStatusEnum;
import com.zealsinger.note.mapper.NoteLikeMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ RocketMQConstant.TOPIC_LIKE_OR_UNLIKE,
        topic = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE,
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
public class LikeUnlikeNoteConsumer implements RocketMQListener<Message> {
    @Resource
    private NoteLikeMapper noteLikeMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        rateLimiter.acquire();
        log.info("开始落库点赞消息{}",message);
        if(Objects.isNull(message)){
            return;
        }
        // 获取标签和消息体
        String tags = message.getTags();
        String body = new String(message.getBody());
        if(Objects.equals(tags,RocketMQConstant.TAG_LIKE)){
            handleLikeNoteTagMessage(body);
        }else if(Objects.equals(tags,RocketMQConstant.TAG_UNLIKE)){
            handleUnlikeNoteTagMessage(body);
        }else{
            log.info("消息{}点赞状态错误",message);
        }
    }

    /**
     * 笔记点赞
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        if(StringUtils.isBlank(bodyJsonStr)){
            return;
        }
        LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, LikeUnlikeMqDTO.class);
        LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteLike::getNoteId, likeUnlikeMqDTO.getNoteId());
        queryWrapper.eq(NoteLike::getUserId, likeUnlikeMqDTO.getUserId());
        NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
        Boolean updateSuccess = Boolean.FALSE;
        if(Objects.isNull(noteLike)){
            noteLike = NoteLike.builder().userId(likeUnlikeMqDTO.getUserId())
                    .noteId(likeUnlikeMqDTO.getNoteId())
                    .createTime(likeUnlikeMqDTO.getOptionTime())
                    .status(likeUnlikeMqDTO.getLikeStatus().byteValue())
                    .build();
            int i = noteLikeMapper.insert(noteLike);
            updateSuccess = i==0?updateSuccess:Boolean.TRUE;
        }else{
            noteLike.setStatus(likeUnlikeMqDTO.getLikeStatus().byteValue());
            int i = noteLikeMapper.updateById(noteLike);
            updateSuccess = i==0?updateSuccess:Boolean.TRUE;
        }
        if(!updateSuccess) {
            return;
        }
        // TODO 发送MQ计数服务
        org.springframework.messaging.Message<String> countMqMessage = MessageBuilder.withPayload(bodyJsonStr).build();
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_NOTE_LIKE, countMqMessage, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记点赞】MQ 发送异常: ", throwable);
            }
        });
    }

    /**
     * 笔记取消点赞
     * @param bodyJsonStr
     */
    private void handleUnlikeNoteTagMessage(String bodyJsonStr) {
        if(StringUtils.isBlank(bodyJsonStr)){
            return;
        }
        LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, LikeUnlikeMqDTO.class);
        LambdaUpdateWrapper<NoteLike> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(NoteLike::getNoteId, likeUnlikeMqDTO.getNoteId());
        queryWrapper.eq(NoteLike::getUserId, likeUnlikeMqDTO.getUserId());
        queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
        queryWrapper.set(NoteLike::getStatus, LikeStatusEnum.UNLIKE.getCode());
        queryWrapper.set(NoteLike::getUpdateTime, likeUnlikeMqDTO.getOptionTime());
        int update = noteLikeMapper.update(queryWrapper);
        // TODO 发送MQ计数服务
        if(update==0){
            return;
        }
        org.springframework.messaging.Message<String> countMqMessage = MessageBuilder.withPayload(bodyJsonStr).build();
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_NOTE_LIKE, countMqMessage, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
    }

}
