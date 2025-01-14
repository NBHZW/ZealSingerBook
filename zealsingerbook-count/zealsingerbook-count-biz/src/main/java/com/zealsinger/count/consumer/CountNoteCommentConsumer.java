package com.zealsinger.count.consumer;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.phantomthief.collection.BufferTrigger;
import com.zealsinger.book.framework.common.constant.MQConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.count.domain.dto.CountPublishCommentMqDTO;
import com.zealsinger.count.domain.entity.Comment;
import com.zealsinger.count.domain.entity.NoteCount;
import com.zealsinger.count.mapper.CommentMapper;
import com.zealsinger.count.mapper.NoteCountMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group" + MQConstant.TOPIC_COUNT_NOTE_COMMENT,
        topic = MQConstant.TOPIC_COUNT_NOTE_COMMENT
)
@Slf4j
public class CountNoteCommentConsumer implements RocketMQListener<String> {

    @Resource
    private NoteCountMapper noteCountMapper;

    @Resource
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Resource
    private CommentMapper commentMapper;

    @Override
    public void onMessage(String message) {
        try {
            List<CountPublishCommentMqDTO> countList = JsonUtil.parseList(message, CountPublishCommentMqDTO.class);
            if(CollUtil.isNotEmpty(countList)){
                threadPoolTaskExecutor.submit(()->{
                    Map<Long, List<CountPublishCommentMqDTO>> map = countList.stream().collect(Collectors.groupingBy(CountPublishCommentMqDTO::getNoteId));
                    map.forEach((noteId,commentList)->{
                        LambdaQueryWrapper<NoteCount> queryWrapper = new LambdaQueryWrapper<>();
                        queryWrapper.eq(NoteCount::getNoteId, noteId);
                        NoteCount noteCount = noteCountMapper.selectOne(queryWrapper);
                        if(Objects.isNull(noteCount)){
                            noteCount=NoteCount.builder()
                                    .noteId(noteId)
                                    .likeTotal(0L)
                                    .collectTotal(0L)
                                    .commentTotal((long) commentList.size())
                                    .createTime(LocalDateTime.now())
                                    .updateTime(LocalDateTime.now())
                                    .build();
                        }else{
                            noteCount.setCommentTotal(noteCount.getCommentTotal()+commentList.size());
                        }
                        noteCountMapper.insertOrUpdate(noteCount);
                    });
                });

                threadPoolTaskExecutor.submit(()->{
                    Map<Long, List<CountPublishCommentMqDTO>> map = countList.stream().filter(countPublishCommentMqDTO -> countPublishCommentMqDTO.getLevel()==2).collect(Collectors.groupingBy(CountPublishCommentMqDTO::getParentId));
                    map.forEach((parentId,commentList)->{
                        LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
                        queryWrapper.eq(Comment::getId,parentId);
                        Comment comment = commentMapper.selectOne(queryWrapper);
                        if(Objects.isNull(comment)){
                            log.error("二级评论对应的父评论ID{}错误",parentId);
                            throw new RuntimeException("[计数服务:二级评论数统计] 发生错误");
                        }
                        comment.setChildCommentTotal(comment.getChildCommentTotal()+commentList.size());
                        commentMapper.updateById(comment);
                    });
                });

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
