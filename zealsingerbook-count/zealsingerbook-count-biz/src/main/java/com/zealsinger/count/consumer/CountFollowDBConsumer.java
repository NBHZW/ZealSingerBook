package com.zealsinger.count.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.domain.dto.CountFollowUnfollowMqDTO;
import com.zealsinger.count.domain.entity.UserCount;
import com.zealsinger.count.domain.enums.FollowUnfollowTypeEnum;
import com.zealsinger.count.mapper.UserCountMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook-group" + MQConstant.TOPIC_COUNT_FOLLOWING_2_DB,
        topic = MQConstant.TOPIC_COUNT_FOLLOWING_2_DB
)
public class CountFollowDBConsumer implements RocketMQListener<String> {
    @Resource
    private UserCountMapper userCountMapper;

    @Override
    public void onMessage(String message) {
        log.info("收到关注消息落库通知{}", message);
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtil.JsonStringToObj(message, CountFollowUnfollowMqDTO.class);
        if(!Objects.isNull(countFollowUnfollowMqDTO)) {
            Long userId = countFollowUnfollowMqDTO.getUserId();
            int number = FollowUnfollowTypeEnum.getFollowUnfollowTypeEnum(countFollowUnfollowMqDTO.getType())==FollowUnfollowTypeEnum.FOLLOW ? 1 : -1;
            LambdaQueryWrapper<UserCount> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UserCount::getUserId, userId);
            UserCount userCount = userCountMapper.selectOne(queryWrapper);
            if(userCount!=null) {
                userCount.setFollowingTotal(Optional.ofNullable(userCount.getFollowingTotal()).orElse(0L)+number);
            }else{
                userCount = UserCount.builder().userId(userId).followingTotal((long) number).build();
            }
            userCountMapper.insertOrUpdate(userCount);
        }

    }
}
