# 计数模块

在 zealsingerbook 中，为了应对高并发和大数据量，计数功能自然是少不了的。在平台中，笔记数量，粉丝数量，点赞数量，收藏数量等操作，都是需要频繁的增减的，是属于高并发的读写操作，所以该模块的高可用，高效率是必备的

# 模块职责

## 用户维度

**用户的关注数，粉丝数，收藏数，总获得的点赞数目**

![image-20241017101741524](../../ZealSingerBook/img/image-20241017101741524.png)

除此之外，点击获赞与收藏，可以查看到详细的获赞和收藏情况：包括了 **当前发布笔记总数，收藏总数，获得点赞总数**

![image-20241017101829841](../../ZealSingerBook/img/image-20241017101829841.png)

## 笔记维度

**点赞量，收藏量，评论量**

![image-20241017102004131](../../ZealSingerBook/img/image-20241017102004131.png)

## 评论维度

每条评论也支持点赞，需要该评论的总点赞数

![image-20241017102113148](../../ZealSingerBook/img/image-20241017102113148.png)

# 架构设计

首先，想到计数，我们第一感觉就是使用 **count**，那么我们来分析一下是否可以？

答案自然是不行的，存在 **IO 过载和性能瓶颈**

查询计数功能我们可以看到，**是无时不刻，极其频繁的一个读取操作**，而且计数相关的操作很容易会进行增加和减少的操作，一篇热门的笔记，会有很多的收藏和点赞，也会有很多评论，每条评论又有可能会有很多的点赞和取消点赞，**数据的变化是非常的频繁的**，**如果采用 count 操作，数据库需要频繁的大量的扫描来进行计数，大大增加的数据库的负载**

再者，**数据库操作涉及磁盘 IO，而直接频繁的 count 容易出现 IO 瓶颈，对于某些热点数据，频繁的被 count，导致数据库响应变慢甚至崩溃**

![image-20241017102612600](../../ZealSingerBook/img/image-20241017102612600.png)

所以自然，我么们不能依靠 count 进行计数，而是应该 **新增一些关联表**，例如笔记点赞表，收藏表，用户计数表等，通过外部关联一些表来进行记录，每次需要的时候进行一次条件查表即可，而不需要去 count 原来的表

## 表设计

### 笔记点赞表

因为暂时还没开放评论模块，但是我们有用户模块，但是存在笔记模块，所以我们先搭建笔记点赞表

| 字段        | 介绍                                   |
| ----------- | -------------------------------------- |
| id          | 主键 ID                                 |
| user_id     | 用户 ID                                 |
| note_id     | 笔记 ID                                 |
| create_time | 创建时间                               |
| update_time | 更新时间                               |
| status      | 点赞状态（0 取消点赞/没有点赞   1 点赞） |

```
CREATE TABLE `t_note_like` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(11) NOT NULL COMMENT '用户ID',
  `note_id` bigint(11) NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint(2) NOT NULL DEFAULT '0' COMMENT '点赞状态(0：取消点赞 1：点赞)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id_note_id` (`user_id`,`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记点赞表';

```

每一条记录表示了  **userID 用户对 noteID 笔记的点赞状态，另外，还为笔记点赞表的 `user_id` 和 `note_id` 两个字段，创建了联合唯一索引 `uk_user_id_note_id` ，提升查询效率的同时，还能保证关联记录的幂等性，保证同一个用户无法多次点赞同一篇笔记。** 

------

### 笔记收藏表

整体和点赞表结构类似

```
CREATE TABLE `t_note_collection` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(11) NOT NULL COMMENT '用户ID',
  `note_id` bigint(11) NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint(2) NOT NULL DEFAULT '0' COMMENT '收藏状态(0：取消收藏 1：收藏)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id_note_id` (`user_id`,`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记收藏表';

```



### 笔记计数表

| 字段          | 介绍         |
| ------------- | ------------ |
| id            | 主键         |
| note_id       | 笔记 ID       |
| like_total    | 获得点赞总数 |
| collect_total | 获得收藏总数 |
| comment_total | 被评论总数   |
| create_time   |              |
| update_time   |              |

```Java
CREATE TABLE `t_note_count` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `note_id` bigint(11) unsigned NOT NULL COMMENT '笔记ID',
  `like_total` bigint(11) unsigned DEFAULT '0' COMMENT '获得点赞总数',
  `collect_total` bigint(11) unsigned DEFAULT '0' COMMENT '获得收藏总数',
  `comment_total` bigint(11) unsigned DEFAULT '0' COMMENT '被评论总数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记计数表';
```

**为笔记计数表的 `note_id` 添加唯一索引，提升查询效率。**

### 用户计数表

```
CREATE TABLE `t_user_count` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(11) unsigned NOT NULL COMMENT '用户ID',
  `fans_total` bigint(11) DEFAULT '0' COMMENT '粉丝总数',
  `following_total` bigint(11) DEFAULT '0' COMMENT '关注总数',
  `note_total` bigint(11) DEFAULT '0' COMMENT '发布笔记总数',
  `like_total` bigint(11) DEFAULT '0' COMMENT '获得点赞总数',
  `collect_total` bigint(11) DEFAULT '0' COMMENT '获得收藏总数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户计数表';

```

**为用户计数表的 `user_id` 添加唯一索引，提升查询效率。**

# 业务分析

## 如何触发计数

对于用户计数表  **首先是粉丝总数和关注总数  这两个和关注和取关操作是绑定的  当 A 关注 B  A 的关注数量+1 B 的粉丝数量+1  当取关的时候刚好相反**

那么其实就是 **用户关系服务模块向计数服务模块进行调用/消息通知**

### 场景分析

**对于关注数量：一个用户的关注数量存在上限，并且关注操作一般不会短时间内同时关注多个人，属于低并发操作 ； 对于粉丝数量的变化，一个用户可能存在爆火情况从而被多个人关注同时关注，属于高并发操作**

但是要知道 我们的关注数和粉丝数目是存在一个表中的，那么直接操作表肯定是不合适的，那么我们还是 **选择先操作 redis 然后在异步落库**

### redis 数据类型选择

我们之前的关注和取关操作存储在 redis 中的时候，我们会选择的是 ZSET 数据类型，这是因为我们粉丝和关注都是一种 List 性质的数据，每一个用户的关注和粉丝都是一行数据，每一行数据就是一个对象，在 Java 自然就是整体是一个集合的数据，对应的 redis 中也要存集合，所以永 ZSET 数据类型

但是对于计数模块 count，每一个用户相关计数都是对应那一行数据，不会有多行，变化的只是每行中对应列的数据，一行是一个对象，那么每个用户对应的计数都是一个对象，只是对象中的成员属性会发生变化，**所以这里我们并不适合采用 ZSET 数据类型，所以我们采用 Hash 结构**

每一行数据除了主键之外，分为了 user_id ; fans_total ; following_total ; note_total ; like_total ; collect_total  

那么就可以利用 Redis 的 Hash 结构 user_id 作为 Key ，其余的作为 Value  那么整体结构就如下

![image-20241017193934490](../../ZealSingerBook/img/image-20241017193934490.png)

redis中的hash结构就是如此，并且redis中的hash结构允许我们对单个数据进行子key-value进行操作和修改，很贴切我们的使用场景

```
redis中Hash常用的指令操作
HSET KEY SON_KEY VALUE  添加/修改SON_KEY的VALUE
HGET KEY SON_KEY 获取SON_KEY的VALUE 
HGETALL KEY 获取KEY下的所有的SON_KEY和对应的VALUE
HKEYS KEY 获取该KEY下的所有的SON_KEY
HVALS KEY 获取该KEY下的所有的VALUE
HINCRBY KEY SON_KEY VALUE 让SON_KEY对应的value进行VALUE的变化（VALUE可以是负数  正数为加 负数为减）
```

## 聚合写Redis

每次操作redis都是加一或者减一，再加上之前说到过的，计数操作是很频繁的，所以其实哪怕是redis，我们总时请求，而且只会进行+1  -1操作，效率极低而且redis压力也很大，所以我们可以**考虑聚合写redis 这是一种保证最终一致性的操作**

![image-20241017195327893](../../ZealSingerBook/img/image-20241017195327893.png)

也就是我们收集一定时间内的操作，然后组合到一起后在写入Redis，这样可以大大减少redis的访问压力，使用聚合写需要注意如下几点：

- **1：聚合后的结果一定要和不聚合的操作结果一致**
- **2：聚合选哟设置一定的窗口时间，我们聚合的一定时间内或者一定条数的数据后就需要被提交，从而防止数据的过度不一致，最终的界限点还是时间（一定时间后哪怕没有到达一定的设定的数量也需要被提交了）**

其优势在于

- **1：减小写操作次数，提高并发量**
- **2：提高系统的吞吐量**

# 创建关注数和粉丝数的MQ消费

上面我们有分析到，触发计数的时机主要在于关注和取关操作，那么关注和取关接口的对应的consumer中再次添加对count计数模块的消息通知

关注和取关接口分别对应FollowAndUnfollowConsumer中的followingHandler方法和unfollowingHandler方法，我们在这两个方法的逻辑后面添加对计数模块的消息通知即可

## 公用对象准备

对于这个消息体，我们在消费者端口，其实无论是关注数的操作 还是粉丝数的操作，所需要的内容就是A关注了B / A取消关注了B  中的A和B的信息，能确认具体操作的用户数据即可，所以我们关注数操作和粉丝数操作的MQ消息体可以公用一个对象

```
public class CountFollowUnfollowMqDTO {
    private Long userId;
    private Long targetUserId;
    /**
     * 0取消关注  1关注
     */
    private Integer type;
}
```

然后 我们定义一些要用的常量，MQ对应的计数模块所需的Topic 和 上面消息对象中的type的枚举

```Java
public enum FollowUnfollowTypeEnum {
    FOLLOW(1),
    UNFOLLOW(0);
    ;
    private Integer value;

    public static FollowUnfollowTypeEnum getFollowUnfollowTypeEnum(Integer value) {
        FollowUnfollowTypeEnum[] followUnfollowTypeEnums = FollowUnfollowTypeEnum.values();
        for (FollowUnfollowTypeEnum followUnfollowTypeEnum : followUnfollowTypeEnums) {
            if (followUnfollowTypeEnum.getValue().equals(value)) {
                return followUnfollowTypeEnum;
            }
        }
        return null;
    }
}


public interface RocketMQConstant {
    /**
     * Topic: 关注、取关共用一个
     */
    String TOPIC_FOLLOW_OR_UNFOLLOW = "FollowUnfollowTopic";

    /**
     * 关注标签
     */
    String TAG_FOLLOW = "Follow";

    /**
     * 取关标签
     */
    String TAG_UNFOLLOW = "Unfollow";

    /**
     * Topic: 关注数计数
     */
    String TOPIC_COUNT_FOLLOWING = "CountFollowingTopic";

    /**
     * Topic: 粉丝数计数
     */
    String TOPIC_COUNT_FANS = "CountFansTopic";
}
```

## 发送计数MQ通知消息

在原本的逻辑底下 利用rockmqtemplate发送消息

![image-20241018115753784](../../ZealSingerBook/img/image-20241018115753784.png)

具体的发送MQ消息的逻辑方法sendCountMessage()逻辑如下

将**消息体分别发送给关注数计数conumer和粉丝数计数consumer**

```Java
public void sendCountMqMessage(CountFollowUnfollowMqDTO countFollowUnfollowMqDTO){
        org.springframework.messaging.Message<String> message= MessageBuilder.withPayload(JsonUtil.ObjToJsonString(countFollowUnfollowMqDTO)).build();
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_FOLLOWING, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("### 关注计数模块 MQ消费成功");
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("### 关注计数模块 MQ消费失败");
            }
        });

        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_FANS, message, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("### 粉丝计数模块 MQ消费成功");
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("### 粉丝计数模块 MQ消费成功");
            }
        });
    }
```

同理，取消关注的逻辑也是一样的，在原本逻辑的最后调用的上述的sendCountMqMessage即可

![image-20241018121400762](../../ZealSingerBook/img/image-20241018121400762.png)

## 聚合服务引入

我们上面有提到，每次只为了加减一而去操作缓存和数据库，压力会巨大，效率也低，我们需要一个聚合服务，能将一段时间内的操作聚合在一起后，保证数据最终准确性同步到redis即可，我们可以**整合快手 BufferTrigger：实现流量聚合**

### BufferTrigger简介

BufferTrigger 的主要作用是为了解决在大数据流处理中常见的问题：如何高效地对连续的数据流进行缓冲，并在满足一定条件时触发下游计算或存储操作。

使用 BufferTrigger 优势如下：

1. **提高效率**：通过批量处理数据而不是逐条处理，可以显著减少 I/O 操作的次数，从而提升整体处理效率。
2. **资源优化**：对于一些需要消耗较多计算资源的操作（如写入数据库、调用外部服务等），通过累积一批数据后再执行一次这样的操作，可以更有效地利用系统资源。
3. **简化逻辑**：对于开发者而言，使用 BufferTrigger 可以帮助简化代码逻辑，将注意力集中在业务逻辑上而不是复杂的缓冲控制逻辑上。
4. **灵活配置**：支持多种触发策略（比如基于时间窗口、基于数据量大小等），使得用户可以根据具体应用场景灵活选择最合适的触发方式。
5. **易于集成**：设计上考虑了与现有数据处理框架的良好兼容性，使得它可以方便地与其他组件一起工作，在现有的技术栈中引入该功能变得更加容易。

### Buffer Trigger快速入门

相关依赖

```
    <properties>
        // 省略...
        <buffertrigger.version>0.2.21</buffertrigger.version>
    </properties>

    <!-- 统一依赖管理 -->
    <dependencyManagement>
        <dependencies>
            // 省略...

            <!-- 快手 Buffer Trigger -->
            <dependency>
                <groupId>com.github.phantomthief</groupId>
                <artifactId>buffer-trigger</artifactId>
                <version>${buffertrigger.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

项目中进行整合

整个Buffer Trigger的最主要的核心对象就是  

```
private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();

可以理解：上述整个bufferTrigger就是一个容器，泛型<String> 决定了该容器存储的数据类型
BufferTrigger.<String>batchBlocking()为基础的构造方法
bufferSize指定了容器的大小
batchSize每次聚合的数据量
linger设置每次聚合之间的间隔时间/多久聚合一次
setConsumerEx(this::consumeMessage) 传入一个方法，作为聚合处理的方法
```

我们拿粉丝数计数模块举例

```Java
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group" + MQConstant.TOPIC_COUNT_FANS,
        topic = MQConstant.TOPIC_COUNT_FANS)
public class CountFansConsumer implements RocketMQListener<String> {
    /**
     * 定义Buffer T
     */
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) //指定聚合方法为 consumeMessage
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    // 作为bufferTrigger的聚合处理方法，其实就是onMessage中接收传入到buffertrigger中的参数列表 进行统一的处理 ，当数量达到batchSize或者时间达到了linger就会自动触发该方法从而聚合处理消息
    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtil.ObjToJsonString(bodys));
    }
}
```

我们可以创建一个测试类进行测试一下

```Java
@SpringBootTest
@Slf4j
class MQTests {
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    /**
     * 测试：发送计数 MQ, 以统计粉丝数
     */
    @Test
    void testSendCountFollowUnfollowMQ() {
        // 循环发送 3200 条 MQ
        for (long i = 0; i < 3200; i++) {
            // 构建消息体 DTO
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(i+1) // 关注者用户 ID
                    .targetUserId(27L) // 目标用户
                    .type(FollowUnfollowTypeEnum.FOLLOW.getCode())
                    .build();

            // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
            org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countFollowUnfollowMqDTO))
                    .build();

            // 发送 MQ 通知计数服务：统计粉丝数
            rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("==> 【计数服务：粉丝数】MQ 发送成功，SendResult: {}", sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("==> 【计数服务：粉丝数】MQ 发送异常: ", throwable);
                }
            });
        }

    }

}

```

![image-20241018131211885](../../ZealSingerBook/img/image-20241018131211885.png)

## 完善MQ消费者逻辑

### 粉丝计数消费

首先是对于计数服务粉丝数量的消费

聚合消息后，按照userId进行分组得到Map（Key为学号  value为该userId这段时间内的粉丝对象）

遍历该map 定义一个聚合变量total用于记录最终聚合结果  按照每个粉丝对象的type（关注/取消关注）从而对total进行加减操作

最后将key和对应的total存入到Map中 从而获得  userId-fans_total对应的组合数据  将其存入到缓存 然后异步发送MQ消息进行落库操作

```Java
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook_group" + MQConstant.TOPIC_COUNT_FANS,
        topic = MQConstant.TOPIC_COUNT_FANS)
public class CountFansConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;


    @Resource
    private RocketMQTemplate rocketMQTemplate;


    /**
     * 定义Buffer T
     */
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();


    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtil.ObjToJsonString(bodys));

        if(CollUtil.isNotEmpty(bodys)) {
            List<CountFollowUnfollowMqDTO> countFollowUnfollowMqDTOList = bodys.stream()
                    .map(s -> JsonUtil.JsonStringToObj(s, CountFollowUnfollowMqDTO.class)).toList();
            // 按照目标用户分组  key为目标用户  value为对key进行关注/取关的操作用户的List集合
            Map<Long, List<CountFollowUnfollowMqDTO>> collect = countFollowUnfollowMqDTOList.stream()
                    .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));

            Map<Long, Integer> countMap = new HashMap<>();
            if(CollUtil.isNotEmpty(collect)) {
                collect.forEach((x,y)->{
                    int finalCount = 0;
                    // 若枚举为空，跳到下一次循环
                    if (!Objects.isNull(y)) {
                        for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : y) {
                            switch(FollowUnfollowTypeEnum.getFollowUnfollowTypeEnum(countFollowUnfollowMqDTO.getType())){
                                case FOLLOW -> finalCount++;
                                case UNFOLLOW -> finalCount--;
                                default -> {}
                            }
                        }
                    }
                    countMap.put(x, finalCount);
                });

                log.info("## 聚合后的计数数据: {}", JsonUtil.ObjToJsonString(countMap));
                countMap.forEach((k,v)->{
                    String redisKey = RedisKeyConstants.buildCountUserKey(k);
                    Boolean isHave = redisTemplate.hasKey(redisKey);
                    // 判断缓存是否存在 缓存如果存在则进行修改 因为缓存存在过期时间 所以这里确实有可能不存在 但是如果缓存不存在 那么暂时可以不需要加载到缓存直接落库
                    // 初始化缓存的过程放到查询计数的过程中
                    if(Boolean.TRUE.equals(isHave)){
                        // redis为Key  RedisKeyConstants.FIELD_FANS_TOTAL为Son_Key  v为value存入redis
                        redisTemplate.opsForHash().increment(redisKey,RedisKeyConstants.FIELD_FANS_TOTAL,v);
                    }
                });
                // TODO 数据异步落库
                Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(countMap)).build();
                rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_FANS_2_DB, message , new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("===>粉丝计数模块入库操作成功");
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.info("===>粉丝计数模块入库操作失败");
                    }
                });
            }
        }
    }
}
```

#### 粉丝计数落库操作

我们上面最后发送了一个MQ消息，目的是为了通知对应的消费者进行落库操作  所以我们需要对应的消费者进行消费

消费者逻辑如下：guava令牌进行限流确保消费速率；消费逻辑就是按照传来的Map(存入了userId-fans-total对应关系)在数据库中对对应的userId那行数据的fans-total变化Map中对应的value数值

```Java
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
```

### 关注数计数消费

关注数消息的消费 主要是这里触发计数

![image-20241018195625939](../../ZealSingerBook/img/image-20241018195625939.png)

对应的消费者如下

![image-20241018195659581](../../ZealSingerBook/img/image-20241018195659581.png)

现在来补充具体逻辑，关注信息的消费，从业务逻辑而言，粉丝数量可能会一瞬间大增，所有可能一段时间内聚合的都是一个人的粉丝操作；但是关注操作上，不太可能出现同一个用户一瞬间关注多个人，所以其实消息的触发频率相对而言是较低的，所以关注这边不需要聚合操作

```Java
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsingerbook-group"+ MQConstant.TOPIC_COUNT_FOLLOWING,
        topic = MQConstant.TOPIC_COUNT_FOLLOWING
)
public class CountFollowConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;


    @Override
    public void onMessage(String message) {
        log.info("收到关注计数服务消费通知{}", message);
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtil.JsonStringToObj(message, CountFollowUnfollowMqDTO.class);
        if(!Objects.isNull(countFollowUnfollowMqDTO)){
            Long userId = countFollowUnfollowMqDTO.getUserId();
            String redisKey = RedisKeyConstants.buildCountUserKey(userId);
            Boolean isHave = redisTemplate.hasKey(redisKey);
            if(isHave){
                int number = FollowUnfollowTypeEnum.getFollowUnfollowTypeEnum(countFollowUnfollowMqDTO.getType())==FollowUnfollowTypeEnum.FOLLOW ? 1 : -1;
                redisTemplate.opsForHash().increment(redisKey,RedisKeyConstants.FIELD_FOLLOWING_TOTAL,number);
            }
            Message<String> mqMessage = MessageBuilder.withPayload(message).build();
            rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_FOLLOWING_2_DB, mqMessage, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("关注计数服务落库成功");
                }

                @Override
                public void onException(Throwable throwable) {
                    log.info("关注计数服务落库失败");
                }
            });
        }

    }

}

```

同理 在最后面需要MQ消息异步落库 对应的落库消息通知的消费者如下

```Java
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook-group" + MQConstant.TOPIC_COUNT_FOLLOWING_2_DB,
        topic = MQConstant.TOPIC_COUNT_FOLLOWING_2_DB
)
public class CountFollowDBConsumer implements RocketMQListener<String> {
    @Resource
    private UserCountMapper userCountMapper;

    @Override
    public void onMessage(String message) {
        log.info("收到关注消息落库通知{}", message);
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtil.JsonStringToObj(message, CountFollowUnfollowMqDTO.class);
        if(!Objects.isNull(countFollowUnfollowMqDTO)) {
            Long userId = countFollowUnfollowMqDTO.getUserId();
            int number = FollowUnfollowTypeEnum.getFollowUnfollowTypeEnum(countFollowUnfollowMqDTO.getType())==FollowUnfollowTypeEnum.FOLLOW ? 1 : -1;
            LambdaQueryWrapper<UserCount> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UserCount::getUserId, userId);
            UserCount userCount = userCountMapper.selectOne(queryWrapper);
            if(userCount!=null) {
                userCount.setFollowingTotal(Optional.ofNullable(userCount.getFollowingTotal()).orElse(0L)+number);
            }else{
                userCount = UserCount.builder().userId(userId).followingTotal((long) number).build();
            }
            userCountMapper.insertOrUpdate(userCount);
        }

    }
}

```

