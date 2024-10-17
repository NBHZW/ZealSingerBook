package com.zealsinger.note.consumer;

import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.note.constant.RocketMQConstant;
import com.zealsinger.note.server.NoteServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group"+ MQConstant.DELETE_LOCAL_NOTE_CACHE, // Group
        topic = RocketMQConstant.TOPIC_DELETE_NOTE_LOCAL_CACHE, // 消费的主题 Topic
        messageModel = MessageModel.BROADCASTING) // 广播模式
public class DeleteLocalNoteCacheConsumer implements RocketMQListener<String> {

    @Resource
    private NoteServer noteServer;

    @Override
    public void onMessage(String s) {
        log.info("DeleteLocalNoteCacheConsumer receive message: {}  准备进行消费", s);
        noteServer.deleteNoteLocalCache(s);
        log.info("### 消费完成 {}",s);

    }
}
