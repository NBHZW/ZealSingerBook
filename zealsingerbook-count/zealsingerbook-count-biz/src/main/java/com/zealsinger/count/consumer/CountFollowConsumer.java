package com.zealsinger.count.consumer;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.messaging.Message;
import java.util.Objects;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group"+ MQConstant.TOPIC_COUNT_FOLLOWING,
        topic = MQConstant.TOPIC_COUNT_FOLLOWING
)
public class CountFollowConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;


    @Override
    public void onMessage(String message) {
        log.info("收到关注计数服务消费通知{}", message);
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtil.JsonStringToObj(message, CountFollowUnfollowMqDTO.class);
        if(!Objects.isNull(countFollowUnfollowMqDTO)){
            Long userId = countFollowUnfollowMqDTO.getUserId();
            String redisKey = RedisKeyConstants.buildCountUserKey(userId);
            Boolean isHave = redisTemplate.hasKey(redisKey);
            if(Boolean.TRUE.equals(isHave)){
                int number = FollowUnfollowTypeEnum.getFollowUnfollowTypeEnum(countFollowUnfollowMqDTO.getType())==FollowUnfollowTypeEnum.FOLLOW ? 1 : -1;
                redisTemplate.opsForHash().increment(redisKey,RedisKeyConstants.FIELD_FOLLOWING_TOTAL,number);
            }
            Message<String> mqMessage = MessageBuilder.withPayload(message).build();
            rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_FOLLOWING_2_DB, mqMessage, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("关注计数服务落库成功");
                }

                @Override
                public void onException(Throwable throwable) {
                    log.info("关注计数服务落库失败");
                }
            });
        }

    }

}
