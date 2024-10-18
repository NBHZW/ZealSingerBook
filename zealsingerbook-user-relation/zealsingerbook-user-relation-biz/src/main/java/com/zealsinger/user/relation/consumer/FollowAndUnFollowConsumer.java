package com.zealsinger.user.relation.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.util.concurrent.RateLimiter;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.DateUtils;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.user.relation.constant.RedisConstant;
import com.zealsinger.user.relation.constant.RocketMQConstant;
import com.zealsinger.user.relation.domain.dto.CountFollowUnfollowMqDTO;
import com.zealsinger.user.relation.domain.dto.MqFollowUserDTO;
import com.zealsinger.user.relation.domain.dto.MqUnFollowUserDTO;
import com.zealsinger.user.relation.domain.entity.Fans;
import com.zealsinger.user.relation.domain.entity.Following;
import com.zealsinger.user.relation.domain.enums.FollowUnfollowTypeEnum;
import com.zealsinger.user.relation.mapper.FansMapper;
import com.zealsinger.user.relation.mapper.FollowingMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.hibernate.validator.internal.constraintvalidators.bv.number.bound.decimal.DecimalMaxValidatorForLong;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group"+ MQConstant.FOLLOW_AND_UNFOLLOW,
        topic = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW,
        consumeMode = ConsumeMode.ORDERLY)
public class FollowAndUnFollowConsumer implements RocketMQListener<Message> {

    @Resource
    private FollowingMapper followingMapper;

    @Resource
    private FansMapper fansMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RateLimiter rateLimiter;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;
    private DecimalMaxValidatorForLong decimalMaxValidatorForLong;

    @Override
    public void onMessage(Message message) {
        /*
        测试令牌桶限流操作
        for (int i = 0; i < 100; i++) {
            rateLimiter.acquire();
            log.info("获得令牌开始消费{}",LocalDateTime.now());
            Long userId = (long) i;
            Long followUserId = (long) i;
            // 对于已经设置了时间的成员 不会被自动填充
            LocalDateTime followTime = LocalDateTime.now();
            Following following = Following.builder().userId(userId).followingUserId(followUserId).createTime(followTime).build();
            followingMapper.insert(following);
        }
        */

        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();
        log.info("==> FollowAndUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if(Objects.equals(tags,RocketMQConstant.TAG_FOLLOW)){
            //关注操作
            followingHandler(bodyJsonStr);
        } else if (Objects.equals(tags,RocketMQConstant.TAG_UNFOLLOW)) {
            //取关操作
            unfollowingHandler(bodyJsonStr);
        }

    }

    /**
     * 关注操作方法
     */
    public void followingHandler(String message){
        MqFollowUserDTO mqFollowUserDTO = JsonUtil.JsonStringToObj(message, MqFollowUserDTO.class);
        if(mqFollowUserDTO == null){
            return;
        }
        Long userId = mqFollowUserDTO.getUserId();
        Long followUserId = mqFollowUserDTO.getFollowUserId();
        // 对于已经设置了时间的成员 不会被自动填充
        LocalDateTime followTime = mqFollowUserDTO.getCreateTime();
        Following following = Following.builder().userId(userId).followingUserId(followUserId).createTime(followTime).build();
        Boolean isSuccess = transactionTemplate.execute(status -> {
            try {
                followingMapper.insert(following);
                Fans fans = Fans.builder().userId(followUserId).fansUserId(userId).createTime(followTime).build();
                fansMapper.insert(fans);
                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("", e);
                return false;
            }

        });
        log.info("### 关注操作数据库操作结果: {}",isSuccess);

        // 操作redis的fans相关的记录
        if(Boolean.TRUE.equals(isSuccess)){
            String fansKey  = RedisConstant.getFansKey(String.valueOf(followUserId));
            // 创建脚本执行对象
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            // 设置脚本资源路径
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_update_fans_zset.lua")));
            // 设置脚本返回值类型
            script.setResultType(Long.class);
            long timestamp = DateUtils.localDateTime2Timestamp(followTime);
            redisTemplate.execute(script, Collections.singletonList(fansKey), userId, timestamp);

            // TODO 发送消息给关注数和粉丝数计数模块消息
            CountFollowUnfollowMqDTO followUnfollowMqDTO = CountFollowUnfollowMqDTO.builder().userId(mqFollowUserDTO.getUserId())
                    .targetUserId(mqFollowUserDTO.getFollowUserId())
                    .type(FollowUnfollowTypeEnum.FOLLOW.getValue())
                    .build();
            sendCountMqMessage(followUnfollowMqDTO);

        }
    }


    /**
     * 取关操作
     */
    public void unfollowingHandler(String message){
        MqUnFollowUserDTO mqUnFollowUserDTO = JsonUtil.JsonStringToObj(message, MqUnFollowUserDTO.class);
        if(mqUnFollowUserDTO == null){
            return;
        }
        String userId = mqUnFollowUserDTO.getUserId();
        String unfollowUserId = mqUnFollowUserDTO.getUnfollowUserId();
        Boolean executeSuccess = transactionTemplate.execute(status -> {
            try {
                // 关注库表操作
                LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
                followingLambdaQueryWrapper.eq(Following::getUserId, userId);
                followingLambdaQueryWrapper.eq(Following::getFollowingUserId, unfollowUserId);
                followingMapper.delete(followingLambdaQueryWrapper);

                // 粉丝表操作
                LambdaQueryWrapper<Fans> fansLambdaQueryWrapper = new LambdaQueryWrapper<>();
                fansLambdaQueryWrapper.eq(Fans::getUserId, unfollowUserId);
                fansLambdaQueryWrapper.eq(Fans::getFansUserId, userId);
                fansMapper.delete(fansLambdaQueryWrapper);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        log.info("### 取关相关数据库操作完成: {}",executeSuccess);
        // 从取关者的粉丝表zset中去除
        if(Boolean.TRUE.equals(executeSuccess)){
            String fansKey  = RedisConstant.getFansKey(String.valueOf(unfollowUserId));
            redisTemplate.opsForZSet().remove(fansKey,userId);
            // 发送MQ给计数模块
            CountFollowUnfollowMqDTO followUnfollowMqDTO = CountFollowUnfollowMqDTO.builder().userId(Long.valueOf(userId))
                    .targetUserId(Long.valueOf(unfollowUserId))
                    .type(FollowUnfollowTypeEnum.UNFOLLOW.getValue())
                    .build();
            sendCountMqMessage(followUnfollowMqDTO);
        }
    }

    public void sendCountMqMessage(CountFollowUnfollowMqDTO countFollowUnfollowMqDTO){
        org.springframework.messaging.Message<String> message= MessageBuilder.withPayload(JsonUtil.ObjToJsonString(countFollowUnfollowMqDTO)).build();
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_FOLLOWING, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("### 关注计数模块 MQ消费成功");
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("### 关注计数模块 MQ消费失败");
            }
        });

        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_FANS, message, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("### 粉丝计数模块 MQ消费成功");
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("### 粉丝计数模块 MQ消费成功");
            }
        });
    }
}
