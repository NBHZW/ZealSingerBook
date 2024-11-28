package com.zealsinger.data.align.consumer;

import com.google.common.base.Objects;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.data.align.constant.RedisKeyConstants;
import com.zealsinger.data.align.constant.TableConstants;
import com.zealsinger.data.align.entity.dto.NoteOperateMqDTO;
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
        consumerGroup = "zealsinger_data_align_"+ MQConstant.TOPIC_COUNT_FOLLOWING,
        topic = MQConstant.TOPIC_COUNT_FOLLOWING
)
@Slf4j
@RefreshScope
public class TodayNotePublishIncrementData2DBConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private InsertMapper insertRecordMapper;

    @Value("${table.shards}")
    private int shards;

    @Override
    public void onMessage(String message) {
        NoteOperateMqDTO noteOperateMqDTO = JsonUtil.JsonStringToObj(message, NoteOperateMqDTO.class);
        if(noteOperateMqDTO==null){
            return;
        }
        log.info("## TodayNotePublishIncrementData2DBConsumer 消费到了 MQ: {}", message);
        Long creatorId = noteOperateMqDTO.getCreatorId();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String userBloomKey = RedisKeyConstants.buildBloomUserNoteOperateListKey(date);
        // 1. 布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_note_publish_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        Long execute = redisTemplate.execute(script, Collections.singletonList(userBloomKey), creatorId);
        if(Objects.equal(execute,0L)){
            long userShards = creatorId%shards;
            try{
                insertRecordMapper.insert2DataAlignUserNotePublishCountTempTable(TableConstants.buildTableNameSuffix(date,userShards),creatorId);
            }catch (Exception e){
                log.error("",e);
            }
            // 加入到布隆中
            RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
            redisTemplate.execute(bloomAddScript, Collections.singletonList(userBloomKey), creatorId);
        }


    }
}
