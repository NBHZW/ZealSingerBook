# 评论模块搭建

评论系统的核心其实是盖楼设计，个人感觉需要好好思考的是数据库的搭建

## 原型分析

![image-20250111163849827](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250111163849827.png)

所需要的逻辑内容

### 评论总数

这个属于我们的技术模块负责的范畴内，在计数表中已经存在了

![image-20250111164042722](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250111164042722.png)

### 多级展示

对于笔记的直接评论都是能展示的，但是二级评论都是默认只展示一个，点击展开回复的时候才会继续展示，而且“展开x条回复”这里有二级评论的条数

除此之外，二级评论也是需要分页展示，类似B站这种效果,一页展示最多展示十条

![image-20250111165033627](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250111165033627.png)

### 对于评论的操作

笔记发布者可以选择对一级评论进行置顶，对于任何评论都可以回复，点赞，评论发布者对于自己评论可以进行删除操作

![image-20250111165231262](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250111165231262.png)

### 评论发布功能

评论应该由文字+顶多一张图片的形式进行回复

## 库表设计

### MySQL中评论表设计

```SQL
CREATE TABLE `t_comment` (
    `id` bigint (20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
    `note_id` bigint (20) unsigned NOT NULL COMMENT '关联的笔记ID',
    `user_id` bigint (20) unsigned NOT NULL COMMENT '发布者用户ID',
    `content_uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '评论内容UUID',
    `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：为空)',
    `image_url` varchar(60) NOT NULL DEFAULT '' COMMENT '评论附加图片URL',
    `level` tinyint (2) NOT NULL DEFAULT '1' COMMENT '级别(1：一级评论 2：二级评论)',
    `reply_total` bigint (20) unsigned DEFAULT 0 COMMENT '评论被回复次数，仅一级评论需要',
    `like_total` bigint (20) unsigned DEFAULT 0 COMMENT '评论被点赞次数',
    `parent_id` bigint (20) unsigned DEFAULT 0 COMMENT '父ID (若是对笔记的评论，则此字段存储笔记ID; 若是二级评论，则此字段存储一级评论的ID)',
    `reply_comment_id` bigint (20) unsigned DEFAULT 0 COMMENT '回复哪个的评论 (0表示是对笔记的评论，若是对他人评论的回复，则存储回复评论的ID)',
    `reply_user_id` bigint (20) unsigned DEFAULT 0 COMMENT '回复的哪个用户, 存储用户ID',
    `is_top` tinyint (2) NOT NULL DEFAULT '0' COMMENT '是否置顶(0：不置顶 1：置顶)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_note_id` (`note_id`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE,
    KEY `idx_parent_id` (`parent_id`) USING BTREE,
    KEY `idx_create_time` (`create_time`) USING BTREE,
    KEY `idx_reply_comment_id` (`reply_comment_id`) USING BTREE,
    KEY `idx_reply_user_id` (`reply_user_id`) USING BTREE
  ) ENGINE = InnoDB COMMENT = '评论表';

```

解释一下表中一些核心字段的作用：

- **`content_uuid`** : 评论内容 UUID。 评论内容和笔记正文一样，均保存在 Cassandra 中，通过此字段关联；

- **`image_url`** ：小红书评论支持发布评论时，附加一张图片；

- **`is_content_empty`** : 评论内容是否为空，因为支持不写任何评论内容，仅发布一张图片，

- **`parent_id`** ： 评论归属的父级 ID。若是对笔记的评论，则此字段存储笔记ID; 若是二级评论，则此字段存储一级评论的 ID；

- **`reply_comment_id`** ： 回复的哪个评论。0 表示是对笔记的评论，若是对他人评论的回复，则存储回复评论的 ID；

- **`reply_user_id`** ： 回复的哪个用户。

  ![img](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/173469185905090)

  > **索引**：添加一下相关查询字段的索引，这里先简单添加一下，后续如果有优化再修正。



### cassandra表设计

对于每个评论的内容自然属于短文本，和笔记一样，数据库中只存储对应的内容UUID，实际上的真实内存存储在Cassandra中，Cassandra中表设计如下

```cql
CREATE TABLE comment_content (
    note_id BIGINT, -- 笔记 ID，分区键
    year_month TEXT, -- 发布年月
    content_id UUID, -- 评论内容 ID
    content TEXT,
    PRIMARY KEY ((note_id, year_month), content_id)
);
```

解释一下这个cassandra的cql语句

**PRIMARY KEY ((note_id, year_month), content_id) 将note_id 和 year_month 和 content_id一起作为主键，前者(note_id, year_month)为分区键，决定数据存放位置，同一笔记的评论放在一个位置，但是为了防止一个位置过大，加入事件year_mouth进行分散，方式热点问题 ； 后者 content_id作为聚簇列，可以认为是每个分区的主键，使用UUID保证唯一性**

测试数据展示如下

```cql
INSERT INTO comment_content (note_id, year_month, content_id, content)
VALUES (1862482047415615528, '2024-12', 123e4567-e89b-12d3-a456-426614174000, '这是对这篇笔记的一条评论内容');
```

![img](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/173469601923821)

### MySQL评论点赞关系表

每个评论可以被点赞，这个也需要进行记录，所以需要评论点赞关系表

```sql
CREATE TABLE `t_comment_like` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `comment_id` bigint NOT NULL COMMENT '评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id_comment_id` (`user_id`,`comment_id`) #建立唯一索引，防止重复点赞
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';
```

## 基础模块搭建

### 评论表 和 评论点赞关系表 对应的实体

```Java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_comment")
public class Comment {
    @TableId("id")
    private Long id;

    @TableField("note_id")
    private Long noteId;

    @TableField("user_id")
    private Long userId;

    @TableField("content_uuid")
    private String contentUuid;

    @TableField("is_content_empty")
    private Boolean isContentEmpty;

    @TableField("image_url")
    private String imageUrl;

    @TableField("level")
    private Integer level;

    @TableField("reply_total")
    private Long replyTotal;

    @TableField("like_total")
    private Long likeTotal;

    @TableField("parent_id")
    private Long parentId;

    @TableField("reply_comment_id")
    private Long replyCommentId;

    @TableField("reply_user_id")
    private Long replyUserId;

    @TableField("is_top")
    private Boolean isTop;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

//------------------


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_comment_like")
public class CommentLike {
    @TableId("id")
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("comment_id")
    private Long commentId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
```

### 异常枚举类型 和 全局异常捕获

```Java
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("COMMENT-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("COMMENT-10001", "参数错误"),
    ;
    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;
}
```

```Java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 捕获自定义业务异常
     * @return
     */
    @ExceptionHandler({ BusinessException.class })
    @ResponseBody
    public Response<Object> handleBusinessException(HttpServletRequest request, BusinessException e) {
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", request.getRequestURI(), e.getErrorCode(), e.getErrorMessage());
        return Response.fail(e);
    }

    /**
     * 捕获参数校验异常
     * @return
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class })
    @ResponseBody
    public Response<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        // 参数错误异常码
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();

        // 获取 BindingResult
        BindingResult bindingResult = e.getBindingResult();

        StringBuilder sb = new StringBuilder();

        // 获取校验不通过的字段，并组合错误信息，格式为： email 邮箱格式不正确, 当前值: '123124qq.com';
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors -> {
            errors.forEach(error ->
                    sb.append(error.getField())
                            .append(" ")
                            .append(error.getDefaultMessage())
                            .append(", 当前值: '")
                            .append(error.getRejectedValue())
                            .append("'; ")

            );
        });

        // 错误信息
        String errorMessage = sb.toString();

        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);

        return Response.fail(errorCode, errorMessage);
    }

    /**
     * 捕获 guava 参数校验异常
     * @return
     */
    @ExceptionHandler({ IllegalArgumentException.class })
    @ResponseBody
    public Response<Object> handleIllegalArgumentException(HttpServletRequest request, IllegalArgumentException e) {
        // 参数错误异常码
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();

        // 错误信息
        String errorMessage = e.getMessage();

        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);

        return Response.fail(errorCode, errorMessage);
    }

    /**
     * 其他类型异常
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler({ Exception.class })
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
    }
    
}

```

## 业务逻辑

### 发布评论

出参返回是否评论成功即可不需要额外的对象，入参则需要，入参如下

```Java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishCommentReqVO {
    @NotNull(message = "笔记ID不能为空")
    private Long noteId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 图片链接
     */
    private String imageUrl;

    /**
     * 回复的评论父ID
     */
    private Long replyCommentId;
}
```

我们采用异步落库的方式，在落库之前会进行一下评论信息的校验---content文本内容和imageUrl不能同时为空

将入库信息封装为DTO对象，然后通过MQ异步落库

```java 
public class PublishCommentMqDTO {
    private Long noteId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片链接
     */
    private String imageUrl;

    /**
     * 回复的哪个评论（评论 ID）
     */
    private Long replyCommentId;

    /**
     * 发布时间
     */
    private LocalDateTime createTime;

    /**
     * 发布者 ID
     */
    private Long creatorId;
}
```



```Java
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
```

### 保证MQ消息可靠性的措施--Spring retry

在配置信息中，其实我们确实配置过了MQ的失败重试次数，这里配置了同步消息和异步消息的重试次数都是三次

![image-20250112155030329](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250112155030329.png)

但是，这明显不够，如果三次都失败了该怎么办？就让消息丢失了么？所以肯定不能这么搞，我们还需要补偿措施，即**采用 重试+补偿 保证MQ消息的可靠性，所谓补偿就是失败的消息落库处理，定时重试再次消费库中的失败消息**

步骤如下：

- **重试方案**：当发送 MQ 失败，则进行重试，并设置时间间隔，如重试 3 次，间隔为 1s 后、2s后、4s后；
- **失败消息落库**：若重试多次均失败，则不应重复无限制重试，而是将该条发送失败的消息写入数据库中；
- **补偿发送**：通过定时任务扫库，将发送失败的 MQ 重新捞出来，再次发送，保证 MQ 最终发送成功(发送成功后，需要将数据库中的记录删除)；

![image-20250112155240917](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250112155240917.png)

MQ本身的重试机制比较简单，我们采用Spring retry更为强大的重试框架，因为是Spring的依赖，直接biz的pom文件中导入就行

```xml
        <!-- Spring Retry 重试框架  -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        
        <!-- AOP 切面（Spring Retry 重试框架需要） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

```

我们创建一个retry的工具类，帮助我们进行重发

```java 
@Component
@Slf4j
public class SendMqRetryHelper {
// @Retryable是重试方法，指定异常类型和重试次数以及重试间隔，当方法发生指定类型的异常的时候 就会进行重试
// @Recover是兜底注解 当触发@Retry重试超过设定次数之后 就会进入到该注解下的方法  进入兜底方法
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Retryable(
            retryFor = { Exception.class },  // 需要重试的异常类型
            maxAttempts = 3,                 // 最大重试次数
            backoff = @Backoff(delay = 1000, multiplier = 2)  // 初始延迟时间 1000ms，每次重试间隔加倍
    )
    public void send(String topic, PublishCommentMqDTO publishCommentMqDTO) {
        log.info("==> 开始异步发送 MQ, Topic: {}, publishCommentMqDTO: {}", topic, publishCommentMqDTO);

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(publishCommentMqDTO))
                .build();

        // 同步发送 MQ
        rocketMQTemplate.syncSend(topic, message);
    }

    /**
     * 兜底方案: 将发送失败的 MQ 写入数据库，之后，通过定时任务扫表，将发送失败的 MQ 再次发送，最终发送成功后，将该记录物理删除
     */
    @Recover
    public void asyncSendMessageFallback(Exception e, String topic, PublishCommentMqDTO publishCommentMqDTO) {
        log.error("==> 多次发送失败, 进入兜底方案, Topic: {}, publishCommentMqDTO: {}", topic, publishCommentMqDTO);

        // TODO:
    }
}
```

除此之外，我们启用Spring retry需要在启动类上加上对应的Enable注解，如下

![image-20250112160349161](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250112160349161.png)

那么写好之后，我们对应的修改一下上面的发布评论的Service层逻辑

```Java
@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    // 省略...
    
    @Resource
    private SendMqRetryHelper sendMqRetryHelper;

    /**
     * 发布评论
     *
     * @param publishCommentReqVO
     * @return
     */
    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        // 省略...

        // 发送 MQ
        // 构建消息体 DTO
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .noteId(publishCommentReqVO.getNoteId())
                .content(content)
                .imageUrl(imageUrl)
                .replyCommentId(publishCommentReqVO.getReplyCommentId())
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .build();

        // 发送 MQ (包含重试机制)
        sendMqRetryHelper.send(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        return Response.success();
    }
}


```

现在是配置好了，**但是需要注意的是，我们这么配置重试之后，整个异步MQ变成了同步MQ，有没有办法还是异步MQ呢？答案是有的**

Spring retry对外提供RetryTemplate让我们自定义重试逻辑，自然同时，需要配置一些重试信息

首先我们准备好重试次数，时间间隔这些配置信息 在properties配置文件中配置信息，然后读取到配置信息类中

![image-20250112162626493](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250112162626493.png)

然后我**们注册自定义RetryTemplate**

```Java
@Configuration
public class RetryConfig {

    @Resource
    private RetryProperties retryProperties;

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 定义重试策略（最多重试 3 次）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(retryProperties.getMaxAttempts()); // 最大重试次数

        // 定义间隔策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.getInitInterval()); // 初始间隔 2000ms
        backOffPolicy.setMultiplier(retryProperties.getMultiplier());       // 每次乘以 2

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
```

然后既然要异步，**我们知道同步是RetryTemplate同步调用了三次retry，每次retry实际就是同步MQ，这个过程是同步的不能修改的，那么我们异步调用RetryTemplate就能实现异步重试发送消息**

所以我们和之前一样自定义一个线程池，然后修改SendMqRetryHelper

```java 
Component
@Slf4j
public class SendMqRetryHelper {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;

    @Resource
    private RetryTemplate retryTemplate;

    /**
     * 异步发送 MQ
     * @param topic
     */
    public void asyncSend(String topic, String body) {
        log.info("==> 开始异步发送 MQ, Topic: {}, Body: {}", topic, body);

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(body)
                .build();

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论发布】MQ 发送异常: ", throwable);
                handleRetry(topic, message); // 发生异常的时候调用handlRetry方法进行重试 利用MQ的异步发送捕获异常，异常的时候才会要重发
            }
        });
    }

    /**
     * 重试处理
     * @param topic
     * @param message
     */
    private void handleRetry(String topic, Message<String> message) {  
        // 异步处理
        taskExecutor.submit(() -> {
            try {
                // 通过 retryTemplate 执行重试
                retryTemplate.execute((RetryCallback<Void, RuntimeException>) context -> {
                    log.info("==> 开始重试 MQ 发送, 当前重试次数: {}, 时间: {}", context.getRetryCount() + 1, LocalDateTime.now());
                    // 同步发送 MQ
                    rocketMQTemplate.syncSend(topic, message);
                    return null;
                });
            } catch (Exception e) {
                // 多次重试失败，进入兜底方案
                fallback(e, topic, message.getPayload());
            }
        });
    }

    /**
     * 兜底方案: 将发送失败的 MQ 写入数据库，之后，通过定时任务扫表，将发送失败的 MQ 再次发送，最终发送成功后，将该记录物理删除
     */
    private void fallback(Exception e, String topic, String bodyJson) {
        log.error("==> 多次发送失败, 进入兜底方案, Topic: {}, bodyJson: {}", topic, bodyJson);

        // TODO:
    }
}
```

我们service中调用asyncSend方法后其实就是异步MQ消息，所以service中的逻辑不会被阻塞，当MQ发生异常，会异步重试，重试的逻辑中也是异步重试

查看一下重看效果

![image-20250112170403813](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250112170403813.png)

注意 **这里不能 用之前的方案+MQ异步，然后再异常回调中使用带注解的retry方法进行，因为注解底层使用AOP，同类方法中调用会导致AOP失效**



### RocketMQClient批量消费消息

我们之前都是从MQ中拉取一个消息，都是在消费者使用@RocketMQListener注解的方式进行消费，每次只能消费一条消息，效率比较慢，所以我们出现了使用RocketMQClient进行批量消费

批量消费的好处：

1：消费效率高

2：减少网络延迟：批量拉去能减少网络请求次数，降低延迟和系统负载

3：提高系统吞吐量

4：降低消费者的负载：当有大量消息需要被消费的时候，批量消费减少高频操作，减少线程上下文切换的开销

---

那么我们开始在这里引用RocketMQClient

首先在项目最大的pom中引入依赖和同一版本

```
    <properties>
        // 省略...
        
        <rocketmq-client.version>4.9.4</rocketmq-client.version>

    </properties>

    <!-- 统一依赖管理 -->
    <dependencyManagement>
        <dependencies>
            // 省略...
            
            <!-- Rocket MQ 客户端 -->
            <dependency>
                <groupId>org.apache.rocketmq</groupId>
                <artifactId>rocketmq-client</artifactId>
                <version>${rocketmq-client.version}</version>
            </dependency>

            // 省略...
    </dependencyManagement>

```

然后在comment模块中添加对应的依赖

```
<!-- Rocket MQ 客户端 -->
            <dependency>
                <groupId>org.apache.rocketmq</groupId>
                <artifactId>rocketmq-client</artifactId>
                <version>${rocketmq-client.version}</version>
            </dependency>
```

**RocketMQClient的主要消费逻辑就是：绑定MQ，然后通过监听器进行批量消费**

我们定义消费者，进行消费

```Java
@Component
@Slf4j
public class Comment2DBConsumer {
    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

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
        // RocketMQClient使用的时候最需要注意的一点：消息最大批次量不能超过消息队列长度
        consumer.setConsumeMessageBatchMaxSize(30);

        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            log.info("==> 本批次消息大小: {}", msgs.size());
            try {
                // 令牌桶流控  业务处理中还需要对数据库进行操作 防止打垮 需要限流
                rateLimiter.acquire();

                for (MessageExt msg : msgs) {
                    String message = new String(msg.getBody());
                    log.info("==> Consumer - Received message: {}", message);

                    // TODO: 业务处理
                }

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
```

### KV服务批量加入

入参

```json
{
    "comments": [ // 评论集合
        {
            "noteId": 1862481925449449554, // 笔记 ID
            "yearMonth": "2024-12", // 发布年月
            "contentId": "95aafb89-997c-4b14-939c-f848aabdad6d", // 内容 ID
            "content": "这是一条评论内容" // 评论正文
        },
        {
            "noteId": 1862481582414102539,
            "yearMonth": "2024-12",
            "contentId": "db8339cd-beba-40a5-9182-c51c2588ae05",
            "content": "这是一条评论内容2"
        },
        {
            "noteId": 1862482047415615528,
            "yearMonth": "2024-12",
            "contentId": "de81edf6-313c-469a-a77d-57d71ba18194",
            "content": "这是一条评论内容3"
        }
    ]
}

```

出参

```json
{
	"success": true,
	"message": null,
	"errorCode": null,
    "data": null
}
```

对应Cassandra中的表的实体类如下

```Java

// 操作Cassandra的时候传入的时候这个对象的集合  需要将最下面的转化为这个
@Table("comment_content")  // 在这里注解表明了是哪个表 所以下面批量查入的server逻辑中就不需要再次额外指明表了
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentContent {
    @PrimaryKey
    private CommentContentPrimaryKey primaryKey;

    private String content;
}


public class CommentContentPrimaryKey {

    @PrimaryKeyColumn(name = "note_id", type = PrimaryKeyType.PARTITIONED)
    private Long noteId; // 分区键1

    @PrimaryKeyColumn(name = "year_month", type = PrimaryKeyType.PARTITIONED)
    private String yearMonth; // 分区键2

    @PrimaryKeyColumn(name = "content_id", type = PrimaryKeyType.CLUSTERED)
    private UUID contentId; // 聚簇键

}

// 方法入参是个列表
public class BatchAddCommentContentReqDTO {

    @NotEmpty(message = "评论内容集合不能为空")
    @Valid  // 指定集合内的评论 DTO，也需要进行参数校验
    private List<CommentContentReqDTO> comments;

}

// 入参列表中每个成员
public class CommentContentReqDTO {
    @NotNull(message = "评论 ID 不能为空")
    private Long noteId;

    @NotBlank(message = "发布年月不能为空")
    private String yearMonth;

    @NotBlank(message = "评论ID 不能为空")
    private String contentId;

    @NotBlank(message = "评论不能为空")
    private String content;
}
```

对应的service逻辑

```Java
public class CommentContentServiceImpl implements CommentContentService {

    @Resource
    private CassandraTemplate cassandraTemplate;

    /**
     * 批量添加评论内容
     * @param batchAddCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        List<CommentContentReqDTO> comments = batchAddCommentContentReqDTO.getComments();

        // DTO 转 DO
        List<CommentContent> contentDOS = comments.stream()
                .map(commentContentReqDTO -> {
                    // 构建主键类
                    CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                            .noteId(commentContentReqDTO.getNoteId())
                            .yearMonth(commentContentReqDTO.getYearMonth())
                            .contentId(UUID.fromString(commentContentReqDTO.getContentId()))
                            .build();

                    // DO 实体类
                    CommentContent commentContentDO = CommentContent.builder()
                            .primaryKey(commentContentPrimaryKey)
                            .content(commentContentReqDTO.getContent())
                            .build();

                    return commentContentDO;
                }).toList();

        // 批量插入
        cassandraTemplate.batchOps()
                .insert(contentDOS)
                .execute();

        return Response.success();
    }
}
```

### 补充发布评论中的TODO

发布评论之前的逻辑到了MQ消费者这里之后就停止了，所以我们要补充这部分的逻辑

这个逻辑就是将message转化为KV服务中批量存储的接口所需要的入参从而RPC远程调用，将内容存入到Cassandra中，然后封装信息存到库中

![image-20250112213742679](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250112213742679.png)

首先准备好kv服务的对外API接口

![image-20250113162806480](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250113162806480.png)

入参为评论集合，集合的子元素也就是我们发布评论接口的入参

![image-20250113162940356](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250113162940356.png)

服务层的主题逻辑：将集合中的元素全部转化为Cassandra表对应的实体，然后利用Cassdandra的批量插入的逻辑

```Java
public class CommentContentServiceImpl implements CommentContentService {

    @Resource
    private CassandraTemplate cassandraTemplate;

    /**
     * 批量添加评论内容
     * @param batchAddCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        List<CommentContentReqDTO> comments = batchAddCommentContentReqDTO.getComments();

        // DTO 转 DO
        List<CommentContent> contentDOS = comments.stream()
                .map(commentContentReqDTO -> {
                    // 构建主键类
                    CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                            .noteId(commentContentReqDTO.getNoteId())
                            .yearMonth(commentContentReqDTO.getYearMonth())
                            .contentId(UUID.fromString(commentContentReqDTO.getContentId()))
                            .build();

                    // DO 实体类
                    CommentContent commentContentDO = CommentContent.builder()
                            .primaryKey(commentContentPrimaryKey)
                            .content(commentContentReqDTO.getContent())
                            .build();

                    return commentContentDO;
                }).toList();

        // 批量插入
        cassandraTemplate.batchOps()
                .insert(contentDOS)
                .execute();

        return Response.success();
    }
}
```

KV的对外接口提供完毕后，我们可以回到TODO的位置补充逻辑

TODO所在逻辑就是异步MQ的消费者逻辑中，接收每条评论消息，我们将其分别加入到数据库和Cassandra中即可，需要注意的是二级评论和一级评论的一些字段上的区别

```java 
......

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
```

### 计数模块评论数变化

评论入库成功之后，就需要通知计数模块，更新评论数的计数，那么我们直接在后面添加MQ发送消息给计数模块即可

![image-20250114162154327](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250114162154327.png)

计数模块这边创建对应的消费者逻辑

这里没有聚合操作是因为前面计数模块上面是将评论数据批量消费，作为List直接发过来，所以不需要再次聚合，然后除此之外有限流进行批量消费，List大小不会很大，可以放心单独使用

将传来的List根据noteId进行分组，然后分别计数即可，对应的操作note_count表中的comment_tatil字段即可

```Java
public class CountNoteCommentConsumer implements RocketMQListener<String> {

    @Resource
    private NoteCountMapper noteCountMapper;

    @Override
    public void onMessage(String message) {
        try {
            List<CountPublishCommentMqDTO> countList = JsonUtil.parseList(message, CountPublishCommentMqDTO.class);
            if(CollUtil.isNotEmpty(countList)){
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
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
```

### 二级评论数量计数

二级评论数量是针对于以及评论而言的，也就是说只有一级评论才会有这个属性

![image-20250114162957005](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250114162957005.png)

从正常的逻辑而言，直接查询的话，每一条一级评论x返回的时候都需要到comment表里面查parient_id为x的评论数据数量，这样肯定是不行的，所以我们在comment表中加入一个冗余字段“child_comment_total"进行单独的保存，方便后续拿取

首先往数据库中加入一个新字段

```Java
alter table t_comment add column `child_comment_total` bigint(20) unsigned DEFAULT '0' COMMENT '二级评论总数（只有一级评论才需要统计）';
```

我们处理计数的过程，已经将commentList封装为CountPublishCommentMqDTO发送给计数模块了，所以我们可以直接在这个MQ上添加一些信息，方便我们进行计数

![image-20250114163652488](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250114163652488.png)

原本的CountPublishCommentMqDTO只包含了noteId和commentId，现在在添加Leve和parent_id两个成员

![image-20250114163850346](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250114163850346.png)

在消息中将其赋值发送过去即可

![image-20250114163925158](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20250114163925158.png)
