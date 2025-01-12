package com.zealsinger.comment.server.impl;

import com.google.common.base.Preconditions;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.comment.constant.MQConstants;
import com.zealsinger.comment.domain.dto.PublishCommentMqDTO;
import com.zealsinger.comment.domain.vo.PublishCommentReqVO;
import com.zealsinger.comment.server.CommentService;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        Long noteId = publishCommentReqVO.getNoteId();
        String content = publishCommentReqVO.getContent();
        String imageUrl = publishCommentReqVO.getImageUrl();
        /**
         Preconditions.checkArgument(boolean 条件,String errorMessage)
         如果前面条件为false，则抛出异常并且携带errorMessage作为异常信息
         guava中的工具
         */
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),"内容和图片不能均为空");
        Long creatorId = LoginUserContextHolder.getUserId();
        PublishCommentMqDTO mqDTO = PublishCommentMqDTO.builder()
                .noteId(noteId)
                .content(content)
                .imageUrl(imageUrl)
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .replyCommentId(publishCommentReqVO.getReplyCommentId())
                .build();
        MessageBuilder<PublishCommentMqDTO> message = MessageBuilder.withPayload(mqDTO);
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT,message,new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论发布】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }
}
