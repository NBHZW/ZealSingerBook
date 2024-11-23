package com.zealsinger.note.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.util.concurrent.RateLimiter;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.note.constant.RocketMQConstant;
import com.zealsinger.note.domain.dto.CollectionUnCollectionMqDTO;
import com.zealsinger.note.domain.entity.NoteCollection;
import com.zealsinger.note.domain.enums.NoteCollectionStatusEnum;
import com.zealsinger.note.mapper.NoteCollectionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ RocketMQConstant.TOPIC_COLLECTION_UNCOLLECTION,
        topic = RocketMQConstant.TOPIC_COLLECTION_UNCOLLECTION,
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
public class CollectionUnCollectionConsumer implements RocketMQListener<Message> {

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private NoteCollectionMapper noteCollectionMapper;


    @Override
    public void onMessage(Message message) {
        // 令牌限流
        rateLimiter.acquire();
        if(Objects.isNull(message)){
            return;
        }
        String bodyJsonStr = new String(message.getBody());
        log.info("收藏信息{} 开始落库消费",bodyJsonStr);
        // 根据Tag分别处理数据
        String tags = message.getTags();
        if(Objects.equals(tags, RocketMQConstant.TAG_COLLECTION)){
            collectionNoteHandler(bodyJsonStr);
        }
        if(Objects.equals(tags, RocketMQConstant.TAG_UNCOLLECTION)){
            uncollectionNoteHandler(bodyJsonStr);
        };
    }

    /**
     * 处理取消收藏
     * @param bodyJsonStr
     */
    private void uncollectionNoteHandler(String bodyJsonStr) {
        CollectionUnCollectionMqDTO collectionUnCollectionMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, CollectionUnCollectionMqDTO.class);
        if(Objects.isNull(collectionUnCollectionMqDTO)){
            return;
        }
        Long userId = collectionUnCollectionMqDTO.getUserId();
        Long noteId = collectionUnCollectionMqDTO.getNoteId();
        LambdaQueryWrapper<NoteCollection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteCollection::getUserId, userId);
        queryWrapper.eq(NoteCollection::getNoteId, noteId);
        queryWrapper.eq(NoteCollection::getStatus, NoteCollectionStatusEnum.COLLECTION.getCode());
        NoteCollection noteCollection = noteCollectionMapper.selectOne(queryWrapper);
        if(!Objects.isNull(noteCollection)){
            noteCollection.setStatus(NoteCollectionStatusEnum.UNCOLLECTION.getCode());
            noteCollectionMapper.updateById(noteCollection);
        }

        // TODO 发送计数服务
        // 更新数据库成功后，发送计数 MQ
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr)
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_NOTE_COLLECT, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记取消收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记取消收藏】MQ 发送异常: ", throwable);
            }
        });
    }

    /**
     * 处理收藏
     * @param bodyJsonStr
     */
    private void collectionNoteHandler(String bodyJsonStr) {
        CollectionUnCollectionMqDTO collectionUnCollectionMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, CollectionUnCollectionMqDTO.class);
        if(Objects.isNull(collectionUnCollectionMqDTO)){
            return;
        }
        Long userId = collectionUnCollectionMqDTO.getUserId();
        Long noteId = collectionUnCollectionMqDTO.getNoteId();
        LocalDateTime optionTime = collectionUnCollectionMqDTO.getOptionTime();
        LambdaQueryWrapper<NoteCollection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteCollection::getUserId, userId);
        queryWrapper.eq(NoteCollection::getNoteId, noteId);
        NoteCollection noteCollection = noteCollectionMapper.selectOne(queryWrapper);
        if(Objects.isNull(noteCollection)){
            NoteCollection build = NoteCollection.builder().userId(userId)
                    .noteId(noteId)
                    .status(NoteCollectionStatusEnum.COLLECTION.getCode())
                    .createTime(optionTime)
                    .updateTime(optionTime)
                    .build();
            noteCollectionMapper.insert(build);
        }else{
            noteCollection.setStatus(NoteCollectionStatusEnum.COLLECTION.getCode());
            noteCollectionMapper.updateById(noteCollection);
        }

        // TODO 发送计数服务
        // 更新数据库成功后，发送计数 MQ
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr)
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_NOTE_COLLECT, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记收藏】MQ 发送异常: ", throwable);
            }
        });
    }
}
