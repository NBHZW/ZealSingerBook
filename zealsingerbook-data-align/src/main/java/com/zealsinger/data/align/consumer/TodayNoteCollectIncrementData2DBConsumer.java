package com.zealsinger.data.align.consumer;

import com.google.common.base.Objects;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.data.align.constant.RedisKeyConstants;
import com.zealsinger.data.align.constant.TableConstants;
import com.zealsinger.data.align.entity.dto.CollectionUnCollectionMqDTO;
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
        consumerGroup = "zealsinger_data_align_"+ MQConstant.TOPIC_COUNT_NOTE_COLLECT,
        topic = MQConstant.TOPIC_COUNT_NOTE_COLLECT
)
@Slf4j
@RefreshScope
public class TodayNoteCollectIncrementData2DBConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private InsertMapper insertRecordMapper;

    @Value("${table.shards}")
    private int shards;

    @Override
    public void onMessage(String message) {
        CollectionUnCollectionMqDTO collectionUnCollectionMqDTO = JsonUtil.JsonStringToObj(message, CollectionUnCollectionMqDTO.class);
        if(collectionUnCollectionMqDTO==null){
            return;
        }
        log.info("## TodayNoteCollectIncrementData2DBConsumer 消费到了 MQ: {}", message);
        Long noteId = collectionUnCollectionMqDTO.getNoteId();
        Long creatorId = collectionUnCollectionMqDTO.getCreatorId();
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 布隆过滤器判断该记录是否被记录 noteId和creator需要分别判断
        String noteBloomKey = RedisKeyConstants.buildBloomUserNoteCollectNoteIdListKey(date);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_note_collect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(noteBloomKey), noteId);
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
        if(Objects.equal(result,0L)){
            long noteShards = noteId%shards;
            try{
                insertRecordMapper.insert2DataAlignNoteCollectCountTempTable(TableConstants.buildTableNameSuffix(date,noteShards),noteId);
            }catch (Exception e){
                log.error("", e);
            }
            redisTemplate.execute(bloomAddScript, Collections.singletonList(noteBloomKey), noteId);
        }

        String userBloomKey = RedisKeyConstants.buildBloomUserNoteCollectUserIdListKey(date);
        result = redisTemplate.execute(script, Collections.singletonList(userBloomKey), creatorId);
        if(Objects.equal(result,0L)){
            long userShards = creatorId%shards;
            try{
                insertRecordMapper.insert2DataAlignUserCollectCountTempTable(TableConstants.buildTableNameSuffix(date,userShards),creatorId);
            }catch (Exception e){
                log.error("", e);
            }
            redisTemplate.execute(bloomAddScript,Collections.singletonList(userBloomKey),creatorId);
        }

    }
}
