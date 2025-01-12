package com.zealsinger.comment.consumer;


import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.constant.DateConstants;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.comment.constant.MQConstants;
import com.zealsinger.comment.domain.dto.PublishCommentMqDTO;
import com.zealsinger.comment.domain.entry.Comment;
import com.zealsinger.comment.mapper.CommentMapper;
import com.zealsinger.comment.rpc.KvRpcService;
import com.zealsinger.kv.dto.CommentContentReqDTO;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Component
@Slf4j
public class Comment2DBConsumer {
    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private KvRpcService kvRpcService;

    @Resource
    private TransactionTemplate transactionTemplate;

    private DefaultMQPushConsumer consumer;

    private RateLimiter rateLimiter = RateLimiter.create(1000);

    @Bean
    public DefaultMQPushConsumer getConsumer() throws MQClientException {
        String group ="zealsingerbook_group"+ MQConstants.TOPIC_PUBLISH_COMMENT;
        // 创建一个新的 DefaultMQPushConsumer 实例，并指定消费者的消费组名
        consumer = new DefaultMQPushConsumer(group);

        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);

        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(MQConstants.TOPIC_PUBLISH_COMMENT, "*");

        // 设置消费者消费消息的起始位置，如果队列中没有消息，则从最新的消息开始消费。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 设置消息消费模式，这里使用集群模式 (CLUSTERING)
        consumer.setMessageModel(MessageModel.CLUSTERING);

        // 设置每批次消费的最大消息数量，这里设置为 30，表示每次拉取时最多消费 30 条消息
        consumer.setConsumeMessageBatchMaxSize(30);

        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            log.info("==> 本批次消息大小: {}", msgs.size());
            try {
                // 令牌桶流控
                rateLimiter.acquire();
                transactionTemplate.execute(status->{
                    try{
                        List<CommentContentReqDTO> commentContentReqDTOList = new ArrayList<>();
                        for (MessageExt msg : msgs) {
                            String message = new String(msg.getBody());
                            log.info("==> Consumer - Received message: {}", message);
                            // TODO: 业务处理
                            PublishCommentMqDTO publishCommentMqDTO = JsonUtil.JsonStringToObj(message, PublishCommentMqDTO.class);
                            Comment comment = Comment.builder().id(publishCommentMqDTO.getCommentId())
                                    .noteId(publishCommentMqDTO.getNoteId())
                                    .userId(publishCommentMqDTO.getCreatorId())
                                    .imageUrl(StringUtils.isBlank(publishCommentMqDTO.getImageUrl()) ? "" : publishCommentMqDTO.getImageUrl())
                                    .isTop(false)
                                    .replyTotal(0L)
                                    .likeTotal(0L)
                                    .replyCommentId(publishCommentMqDTO.getReplyCommentId())
                                    .createTime(publishCommentMqDTO.getCreateTime())
                                    .updateTime(publishCommentMqDTO.getCreateTime())
                                    .build();
                            String content = publishCommentMqDTO.getContent();
                            boolean blank = StringUtils.isBlank(content);
                            String contentUuid = String.valueOf(UUID.randomUUID());
                            comment.setContentUuid(blank ? "": contentUuid);
                            comment.setIsContentEmpty(blank);
                            /*
                             * reply_comment_id为0则说明回复的笔记  为一级评论
                             * 如果不为0 则说明为二级评论
                             *
                             * 如果为二级评论 parentId为对应的评论ID  如果为一级评论则为笔记ID
                             *
                             * reply-user-id只有当回复的是二级评论的时候才会需要 其余的时候不需要
                             * */
                            comment.setLevel(publishCommentMqDTO.getReplyCommentId()==0?1:2);
                            comment.setParentId(publishCommentMqDTO.getReplyCommentId()==0?publishCommentMqDTO.getNoteId(): publishCommentMqDTO.getCommentId());

                            //查询回复的是否为二级评论
                            LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
                            queryWrapper.eq(Comment::getId, comment.getParentId());
                            queryWrapper.eq(Comment::getLevel,2);
                            Comment selectOne = commentMapper.selectOne(queryWrapper);
                            comment.setReplyUserId(Objects.isNull(selectOne)?0:selectOne.getUserId());
                            commentMapper.insert(comment);
                            if(!blank){
                                CommentContentReqDTO commentContentReqDTO = CommentContentReqDTO.builder().noteId(publishCommentMqDTO.getNoteId())
                                        .yearMonth(publishCommentMqDTO.getCreateTime().format(DateConstants.DATE_FORMAT_Y_M))
                                        .contentId(contentUuid)
                                        .content(content)
                                        .build();
                                commentContentReqDTOList.add(commentContentReqDTO);
                            }
                        }
                        boolean isSuccess=true;
                        if(CollUtil.isNotEmpty(commentContentReqDTOList)){
                            isSuccess = kvRpcService.batchSaveCommentContent(commentContentReqDTOList);
                        }
                        return isSuccess;
                    }catch (Exception ex){
                        status.setRollbackOnly();
                        log.error("", ex);
                        throw ex;
                    }
                });


                // 手动 ACK，告诉 RocketMQ 这批次消息消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("", e);
                // 手动 ACK，告诉 RocketMQ 这批次消息处理失败，稍后再进行重试
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        // 启动消费者
        consumer.start();
        return consumer;
    }

    /**
     * @PreDestroy注解
     * 用于标记在Spring容器中的Bean被销毁之前需要执行的方法。这个注解的主要作用是释放资源
     */
    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();  // 关闭消费者
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }
}
