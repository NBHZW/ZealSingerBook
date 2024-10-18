package com.zealsinger.count.consumer;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.util.concurrent.RateLimiter;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.domain.entity.UserCount;
import com.zealsinger.count.mapper.UserCountMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RocketMQMessageListener(consumerGroup = "zealsingerbook-group"+ MQConstant.TOPIC_COUNT_FANS_2_DB,
        topic = MQConstant.TOPIC_COUNT_FANS_2_DB
)
public class CountFansDBConsumer implements RocketMQListener<String> {

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Resource
    private UserCountMapper userCountMapper;

    @Override
    public void onMessage(String message) {
        // 获取令牌进行流量削峰
        rateLimiter.acquire();
        log.info("===>接收到Fans计数模块的落库信息{}", message);
        Map<Long, Integer> map = null;
        try {
            map = JsonUtil.parseMap(message,Long.class,Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if (CollUtil.isNotEmpty(map)) {
            consumerMessage(map);
        }
    }

    private void consumerMessage(Map<Long,Integer> map){
        map.forEach((k,v)->{
            LambdaQueryWrapper<UserCount> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserCount::getUserId,k);
            UserCount userCount = userCountMapper.selectOne(wrapper);
            if(userCount==null){
                userCount = UserCount.builder().userId(k).fansTotal(Long.valueOf(v)).build();
            } else{
                userCount.setFansTotal(Optional.ofNullable(userCount.getFansTotal()).orElse(0L)+v);
            }
            userCountMapper.insertOrUpdate(userCount);
        });
    }
}
