package com.zealsinger.data.align.consumer;

import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.data.align.constant.RedisKeyConstants;
import com.zealsinger.data.align.constant.TableConstants;
import com.zealsinger.data.align.entity.dto.CountFollowUnfollowMqDTO;
import com.zealsinger.data.align.mapper.InsertMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

@Component
@RocketMQMessageListener(
        consumerGroup = "zealsinger_data_align_"+ MQConstant.TOPIC_COUNT_FOLLOWING,
        topic = MQConstant.TOPIC_COUNT_FOLLOWING
)
@Slf4j
@RefreshScope
public class TodayUserFollowIncrementData2DBConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private InsertMapper insertRecordMapper;

    @Value("${table.shards}")
    private int shards;

    @Override
    public void onMessage(String message) {
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtil.JsonStringToObj(message, CountFollowUnfollowMqDTO.class);
        if(countFollowUnfollowMqDTO==null){
            return;
        }
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", message);
        Long userId = countFollowUnfollowMqDTO.getUserId();
        Long targetUserId = countFollowUnfollowMqDTO.getTargetUserId();
        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串
        String userBloomKey = RedisKeyConstants.buildBloomUserFollowListKey(date);
        String targetUserBloomKey = RedisKeyConstants.buildBloomUserFansListKey(date);
        // 1. 布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_follow_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(userBloomKey), userId);

        // Lua 脚本：添加到布隆过滤器
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);


        // 若布隆过滤器判断不存在（绝对正确）
        if (Objects.equals(result, 0L)) {
            // TODO: 若无，才会落库，减轻数据库压力
            // 将日增量变更数据，写入表 t_data_align_following_count_temp_日期_分片序号
            long userIdHashKey = userId % shards;

            try {
                // 将日增量变更数据，写入表 t_data_align_following_count_temp_日期_分片序号
                insertRecordMapper.insert2DataAlignUserFollowingCountTempTable(
                        TableConstants.buildTableNameSuffix(date, userIdHashKey), userId);
            } catch (Exception e) {
                log.error("", e);
            }
            // TODO: 数据库写入成功后，再添加布隆过滤器中
            // 数据库写入成功后，再添加布隆过滤器中
            redisTemplate.execute(bloomAddScript, Collections.singletonList(targetUserBloomKey), targetUserId);
        }


        // 布隆过滤器判断该日增量数据是否已经记录
        result = redisTemplate.execute(script, Collections.singletonList(targetUserBloomKey), targetUserId);
        // 若布隆过滤器判断不存在（绝对正确）
        if (Objects.equals(result, 0L)) {
            // TODO: 若无，才会落库，减轻数据库压力
            // 将日增量变更数据，写入表 t_data_align_fans_count_temp_日期_分片序号
            long targetUserIdHashKey = targetUserId % shards;

            try {
                // 将日增量变更数据，写入表 t_data_align_fans_count_temp_日期_分片序号
                insertRecordMapper.insert2DataAlignUserFansCountTempTable(
                        TableConstants.buildTableNameSuffix(date, targetUserIdHashKey), targetUserId);
            } catch (Exception e) {
                log.error("", e);
            }
            // TODO: 数据库写入成功后，再添加布隆过滤器中
            // 数据库写入成功后，再添加布隆过滤器中
            redisTemplate.execute(bloomAddScript, Collections.singletonList(targetUserBloomKey), targetUserId);
        }
    }
}
