package com.zealsinger.data.align.consumer;

import com.google.common.base.Objects;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.data.align.constant.RedisKeyConstants;
import com.zealsinger.data.align.constant.TableConstants;
import com.zealsinger.data.align.entity.dto.LikeUnlikeMqDTO;
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

@Component
@RocketMQMessageListener(
        consumerGroup = "zealsinger_data_align_"+ MQConstant.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstant.TOPIC_COUNT_NOTE_LIKE
)
@Slf4j
@RefreshScope
public class TodayNoteLikeIncrementData2DBConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private InsertMapper insertRecordMapper;


    @Value("${table.shards}")
    private int shards;

    @Override
    public void onMessage(String message) {
        log.info("## TodayNoteLikeIncrementData2DBConsumer 消费到了 MQ: {}", message);
        // TODO 布隆过滤器判断该日增量数据是否已经记录
        LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(message, LikeUnlikeMqDTO.class);
        if(likeUnlikeMqDTO==null){
            return;
        }
        Long noteId = likeUnlikeMqDTO.getNoteId();
        Long creatorId = likeUnlikeMqDTO.getCreatorId();
        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串

        String noteBloomKey = RedisKeyConstants.buildBloomUserNoteLikeNoteIdListKey(date);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_note_like_check.lua")));
        Long executeResult = redisTemplate.execute(script, Collections.singletonList(noteBloomKey), noteId);
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
        // TODO 如果没有 则落库 否则不落库  减少库的压力(虽然数据库加了唯一索引 不会重复 但是用一层布隆降低数据库的压力)
        // 布隆过滤器中不存在的时候是绝对不存在  不会出现误判  直接可以落库
        if(Objects.equal(executeResult,0L)){
            // TODO 对应的日增量变更数据
            // - t_data_align_note_like_count_temp_日期_分片序号  noteId%3
            long noteShards = noteId%shards;
            try{
                insertRecordMapper.insert2DataAlignNoteLikeCountTempTable(TableConstants.buildTableNameSuffix(date,noteShards),noteId);
            }catch (Exception e){
                log.error("", e);
            }
            // TODO 数据库写入成功后，再添加布隆过滤器中
            // 4. 数据库写入成功后，再添加布隆过滤器中

            redisTemplate.execute(bloomAddScript, Collections.singletonList(noteBloomKey), noteId);
        }


        // 添加完笔记noteId的布隆和数据库  添加creator的布隆和数据库
        String userBloomKey = RedisKeyConstants.buildBloomUserNoteLikeUserIdListKey(date);
        executeResult = redisTemplate.execute(script, Collections.singletonList(userBloomKey), creatorId);
        if(Objects.equal(executeResult,0L)){
            // - t_data_align_user_like_count_temp_日期_分片序号  userId%3
            long creatorShards = creatorId%shards;
            try{
                insertRecordMapper.insert2DataAlignUserLikeCountTempTable(TableConstants.buildTableNameSuffix(date,creatorShards),creatorId);
            }catch (Exception e){
                log.error("", e);
            }
            // creator落库
            redisTemplate.execute(bloomAddScript, Collections.singletonList(userBloomKey), creatorId);
        }



    }
}
