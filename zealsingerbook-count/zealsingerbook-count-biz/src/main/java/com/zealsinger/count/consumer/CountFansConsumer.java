package com.zealsinger.count.consumer;

import cn.hutool.core.collection.CollUtil;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.constants.RedisKeyConstants;
import com.zealsinger.count.domain.dto.CountFollowUnfollowMqDTO;
import com.zealsinger.count.domain.enums.FollowUnfollowTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group" + MQConstant.TOPIC_COUNT_FANS,
        topic = MQConstant.TOPIC_COUNT_FANS)
public class CountFansConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;


    @Resource
    private RocketMQTemplate rocketMQTemplate;


    /**
     * 定义Buffer T
     */
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(2)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();


    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtil.ObjToJsonString(bodys));

        if(CollUtil.isNotEmpty(bodys)) {
            List<CountFollowUnfollowMqDTO> countFollowUnfollowMqDTOList = bodys.stream()
                    .map(s -> JsonUtil.JsonStringToObj(s, CountFollowUnfollowMqDTO.class)).toList();
            // 按照目标用户分组  key为目标用户  value为对key进行关注/取关的操作用户的List集合
            Map<Long, List<CountFollowUnfollowMqDTO>> collect = countFollowUnfollowMqDTOList.stream()
                    .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));

            Map<Long, Integer> countMap = new HashMap<>();
            if(CollUtil.isNotEmpty(collect)) {
                collect.forEach((x,y)->{
                    int finalCount = 0;
                    // 若枚举为空，跳到下一次循环
                    if (!Objects.isNull(y)) {
                        for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : y) {
                            switch(FollowUnfollowTypeEnum.getFollowUnfollowTypeEnum(countFollowUnfollowMqDTO.getType())){
                                case FOLLOW -> finalCount++;
                                case UNFOLLOW -> finalCount--;
                                default -> {}
                            }
                        }
                    }
                    countMap.put(x, finalCount);
                });

                log.info("## 聚合后的计数数据: {}", JsonUtil.ObjToJsonString(countMap));
                countMap.forEach((k,v)->{
                    String redisKey = RedisKeyConstants.buildCountUserKey(k);
                    Boolean isHave = redisTemplate.hasKey(redisKey);
                    // 判断缓存是否存在 缓存如果存在则进行修改 因为缓存存在过期时间 所以这里确实有可能不存在 但是如果缓存不存在 那么暂时可以不需要加载到缓存直接落库
                    // 初始化缓存的过程放到查询计数的过程中
                    if(Boolean.TRUE.equals(isHave)){
                        // redis为Key  RedisKeyConstants.FIELD_FANS_TOTAL为Son_Key  v为value存入redis
                        redisTemplate.opsForHash().increment(redisKey,RedisKeyConstants.FIELD_FANS_TOTAL,v);
                    }
                });
                // TODO 数据异步落库
                Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(countMap)).build();
                rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_FANS_2_DB, message , new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("===>粉丝计数模块入库操作成功");
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.info("===>粉丝计数模块入库操作失败");
                    }
                });
            }
        }
    }
}
