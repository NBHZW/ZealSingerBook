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

