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

# 笔记点赞计数

对应小红书的界面

![image-20241021143629579](../../ZealSingerBook/img/image-20241021143629579.png)

用户点赞某笔记，调用后端笔记点赞接口，若服务响应success为true，则说明点赞成功，前端能一直知道点赞数目，第二次进入也不需要再次请求就能知道小红心是否需要点亮，标识该毕竟已经被当前用户点赞过，并请求笔记详情接口返回点赞总数并展示，前端自行进行计数+1的操作

另外，自己点赞过的用户，能查看到点赞的列表（需要注意 只有自己能看到自己点赞过的  别人是看不到）

![image-20241021144053191](../../ZealSingerBook/img/image-20241021144053191.png)

![image-20241021144144287](../../ZealSingerBook/img/image-20241021144144287.png)

## 点赞列表设计

及时自己只能看自己的列表，但是**每个人点赞的数量是没有上限的**，当有多个用户在线，同时查看自己点赞列表的时候，全部直接走数据库依旧是不小的负担，所以**依旧需要缓存层**

点赞列表有如下特点：

**点赞需要查看按照时间排序，一般用户都是喜欢查看最近点赞的作品，但是很久之前的其实一般曝光率比较低**

在我们的点赞列表中，前端依旧是采用流式布局，后端自然也是分页查询 ， 然后**前面几页我们进行缓存，当超出一定页数之后我们在进行查库操作**

![image-20241021145841845](../../ZealSingerBook/img/image-20241021145841845.png)

## 是否点赞条件判断设计

可以看到上面的流程图，在正式更新点赞数据之前，需要判断 **笔记ID的合理性** 和 **是否已经点赞过** 

对于这两个判断，笔记ID的合理性比较简单，直接查库就行，因为只要查一次，压力也不会很大

但是对于的是否已经被点赞过的条件判断，我们需要考虑一下，暂且有如下方案

### 直接查库

最简单直接的，每个用户发起的点赞保存到数据库中（记录一个点赞表 userId-noteId对照关系），每次点赞的时候查询数据库中是否有该数据

**优点：实现简单，适合小规模操作的点赞**

**缺点：并发承受力小，查询和写入性能低**

### Redis Set存储点赞记录

使用Redis的Set结构存储，每个用户对应一个Set，点赞过的笔记NoteId存放到对应的set中，每次点赞查询set中是否存在该数据即可

基本步骤如下

**使用Redis的SISMEMBER指令判断是否点赞过 ； 如果点赞过返回对应的提示 ； 如果没点赞过使用Add添加**

优点：快，简单实现

缺点：因为点赞数据肯定要持久化，userId 和 nodeId都是Long类型，那么一共就是n个8字节，假设一个用户点赞1000篇，一亿用户，那么就是约等于90+GB，对于内存型的redis肯定是不适合的，内存昂贵

### Redis BitMap

Redis的BitMap是一种基于位操作的结构，可以高效的存储某一个集合内的信息，在我们的场景下，可以每个用户维护一个BitMap，每一位对应一个笔记NoteId，0/1标识是否给该笔记点赞

```
Long noteID = x;
SETBIT userID  noteID 1
```

基本步骤为：**GETBIT判断是否点赞过 ； SETBIT 1 将noteId对应的位变为1标识点赞** 

**优点：内存占用少，查询效率高**

**缺点：需要合理的设计NoteId和位图偏移策略，BitMap的操作局限在二进制的0/1无法进行多维数据的操作，并且在我们当前的业务场景中，我们的笔记ID是雪花算法算出来的1+41+5+5+12=64位，而Readis中的BitMap位图最大偏移量位为无符号32位整数，所以我们当前场景是无法使用BitMap的**

### 布隆过滤器

Redis Set其实除了内存问题是一个比较好的选择，而布隆过滤器可以有效的减少存储空间，布隆过滤器底层使用通过Hash数值的计算判断是否存在，存在一定的误判（布隆过滤器中有的不一定有，布隆过滤器中没有的一定没有）

- > 1. **为每个用户点赞列表初始化一个布隆过滤器，并在每次点赞笔记时，将笔记 ID 入到该布隆过滤器中；**
  > 2. **使用 `Bloom Filter` 的 `BF.EXISTS` 命令判断笔记是否已经点赞过；**
  > 3. **如果返回未点赞（不存在误报），则继续执行后续点赞逻辑；若返回已点赞（可能有误报），则需要进一步确认是否已点赞。**

- **优点：显著减少内存消耗，适合海量用户场景。**

- **缺点：对于已点赞笔记，存在一定的误判（对未点赞的笔记判断，则绝对正确）。**

#### 布隆过滤器简介

其本质就是**二进制的数组**  也就是0/1数组  **如果数据存在那么为1 不存在为0**

![image-20241021204826685](../../ZealSingerBook/img/image-20241021204826685.png)

**布隆过滤器会将要存入的数据value，经过多个Hash函数（试图中为三个 实际上不一定）），也就是对应的不同的哈希算法得出value的多个Hash值,然后将对应的Hash值的数组中的下标位置为1**

**从上述存入的逻辑，我们可以推出查询的逻辑，将需要查询的数也按照Hash函数算出多个Hash值，然后检测对应的所有下标位置的元素是否全部为1  只要有一个 不为1那么就说明不存在，反之则说明存在**

![image-20241021205015976](../../ZealSingerBook/img/image-20241021205015976.png)

**但是同时也需要知道，既然放入数据是通过Hash计算的，必然会出现Hash冲突，也就是多个数据会操作同一个数组下标位置的数据，如下，假设"你好"和“Hello”都映射到了索引为2的位置的数据,此时下标为2的位置的1其实代表了存了两个数据，但是当我们删除某一个的时候，该位置变为0，其实说明沃尔玛呢同时删除了"你好"和“Hello”两个数据，所以一般情况下是不会删除布隆过滤器中的数据的**

![image-20241021205545870](../../ZealSingerBook/img/image-20241021205545870.png)

因为是基于数组的，**布隆过滤器的速度极快**，每个数据对应的Hash值，然后直接数组中操作下标位置，每个hash函数对应一个Hash值，对应操作一个角标位置，每一个时间复杂度为O（1），如果有K个哈希函数，那么时间总复杂度就是**O（K）**

除此之外，**占用空间少**，然后因为存储的是0和1而不应是原来的数据，所以**保密性也很好**

**布隆过滤器一个很大的也是不可避免的问题就是误判，如上，假设"你好"和“Hello”都映射到了索引为2的位置的数据，那么先存入”Hello”让下标为2的位置变为1，此时来判断“”你好”是否存在，因为二者映射关系一样，所以判断依据也是看下标为2的位置是否为1，但是因为“Hello”的存在导致确实为1就会认为存在，但实际上不存在，也就出现了误判**

![image-20241021205956836](../../ZealSingerBook/img/image-20241021205956836.png)

**为了降低误判率，所以才会有多个Hash函数，多个Hash值全部一样才算是真正的Hash碰撞，这就是为啥会要求多个Hash数值，但是同时，误判率越低，Hash越多会降低性能**

再者Redis也就解决缓存穿透（缓存中和库中都没数据，一般是恶意查询）

#### 安装RedisBloom

```
docker run -p 6379:6379 --name redis -v /root/Docker/redis/conf/redis.conf:/etc/redis/redis.conf -v /root/Docker/redis/data:/data -v /root/Docker/redis/modules:/etc/redis/modules -d redis:7.0.12 redis-server /etc/redis/redis.conf --appendonly yes
```

#### 指令简介

```
BF.ADD  KEY VALUE
添加一个元素

BF.EXEISTE KEY VALUE
检测key对应的数值中是否存在vlaue

BF.MADD key value1 value2.....
批量添加

BF.MEXISTS key value1 value2....
批量检测是否存在
```

## 主要逻辑编写

就按照上面流程图的顺序，**首先检测note合理性，查note无法就是查note表，但是我们查看笔记详情的时候是存在对笔记的缓存的，也是就说redis和本地缓存中其实都有可能存在数据，所以我们可以在查库之间先查缓存**

```Java
// 校验note合理性 + 是否已经点赞 + 更新点赞列表 + 发送MQ落库
        Long noteId = likeNoteReqVO.getNoteId();
        // 因为会存在本地缓存笔记详情，所以可以先查本地缓存
        FindNoteByIdRspVO findNoteByIdRspVO = LOCAL_NOTEVO_CACHE.getIfPresent(String.valueOf(noteId));
        if(Objects.isNull(findNoteByIdRspVO)){
            // 本地缓存无 查redis
            String noteCacheKey = RedisConstant.getNoteCacheId(String.valueOf(noteId));
            String noteStr = redisTemplate.opsForValue().get(noteCacheKey);
            findNoteByIdRspVO = JsonUtil.JsonStringToObj(noteStr,FindNoteByIdRspVO.class);
            if(Objects.isNull(findNoteByIdRspVO)){
                // 都不存在就去查库
                LambdaQueryWrapper<Note> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(Note::getId, noteId);
                queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
                Note note = noteMapper.selectOne(queryWrapper);
                if(note==null){
                    throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }
                // 库中存在的话 异步缓存一下笔记信息  直接调用之前写的的查询笔记详情方法从而实现同步缓存
                threadPoolTaskExecutor.submit(()->{
                    FindNoteByIdReqDTO build = FindNoteByIdReqDTO.builder().noteId(String.valueOf(noteId)).build();
                    try {
                        findById(build);
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            // redis中有缓存 异步存入本地缓存即可
            FindNoteByIdRspVO finalFindNoteByIdRspVO = findNoteByIdRspVO;
            threadPoolTaskExecutor.submit(()->{
                LOCAL_NOTEVO_CACHE.put(String.valueOf(noteId), finalFindNoteByIdRspVO);
            });
        }
```

然后是对于**是否已经被点赞过的判断，因为redisTemplate不能直接操作布隆过滤器，所以我们采用Lua脚本的形式使用布隆过滤器相关的操作**



```Java
// 判断是否已经点赞
        Long userId = LoginUserContextHolder.getUserId();
// 布隆过滤器缓存对应的key
        String bloomKey = RedisConstant.getBloomUserNoteLikeListKey(userId);
// 执行Lua脚本  作用是检测是否存在数据
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_like_check.lua")));
// 获得执行结果
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(bloomKey), noteId);
        NoteBloomLuaResultEnmu resultEnmu = NoteBloomLuaResultEnmu.valueOf(result);
// 对结果进行判断 从而进行不同操作
        switch (resultEnmu) {
                // 如果在布隆过滤器中查到 那么就需要抛出已经点赞过的异常 但是因为布隆过滤器存在误判 这里还需要修改
            case NOTE_LIKED -> throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                // 这里是说明布隆过滤器不存在 也就是reids中不存在bloomKey 那么需要初始化布隆过滤器 也需要查库验证
            case BLOOM_NOT_EXIST -> {
                // 查库判断是否点赞过
                LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteLike::getUserId, userId);
                queryWrapper.eq(NoteLike::getNoteId, noteId);
                queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
                // 先查库是否存在
                NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
                long expiredSecond = 60*60*24 + RandomUtil.randomInt(60*60*24);
                if(!Objects.isNull(noteLike)){
                    // 存在 说明已经点赞 不能重复点 抛出异常 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(()->{
                        batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey);
                    });
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 到这里的就是未点赞 主动初始化布隆过滤器  添加新记录 异步放入到库中
                batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey);
                // 将新记录加入
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setResultType(Long.class);
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_like_note_and_expire.lua")));
                redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId,expiredSecond);
            }
            default -> {}
        }
```

上面可以看到两个Lua文件，第一个/lua/bloom_note_like_check.lua  负责检测布隆过滤器重是否存在数据

```lua
-- LUA 脚本：点赞布隆过滤器

local key = KEYS[1] -- 操作的 Redis Key
local noteId = ARGV[1] -- 笔记ID

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验该篇笔记是否被点赞过(1 表示已经点赞，0 表示未点赞)
local isLiked = redis.call('BF.EXISTS', key, noteId)
if isLiked == 1 then
    return 1
end

-- 未被点赞，添加点赞数据
redis.call('BF.ADD', key, noteId)
return 0

```

/lua/bloom_add_like_note_and_expire.lua  主要负责新增新的数据设置过期时间  主要作用于当初始化布隆过滤器之后添加当前请求的点赞记录

```lua
-- 操作的 Key
local key = KEYS[1]
local noteId = ARGV[1] -- 笔记ID
local expireSeconds = ARGV[2] -- 过期时间（秒）

redis.call("BF.ADD", key, noteId)
-- 设置过期时间
redis.call("EXPIRE", key, expireSeconds)
return 0

```

然后是解决上面的：布隆过滤器中判断存在的误判问题的解决，这个自然是不能容忍的，我们的结局方案是采用三重检测

**Bloom过滤器 + ZSet校验 + 数据库校验方案**

![image-20241022191810686](../../ZealSingerBook/img/image-20241022191810686.png)

```Java
// 布隆中已经存在 进行二次判断
            case NOTE_LIKED -> {
                // 多重检测之ZSET检测  检测zset中是否存在noteId的记录
                Double score = redisTemplate.opsForZSet().score(noteLikeZSetKey, noteId);
                if(Objects.nonNull(score)){
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // zset中不存在数据 查库
                LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteLike::getUserId, userId);
                queryWrapper.eq(NoteLike::getNoteId, noteId);
                queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
                NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
                if(noteLike!=null){
                    // 到这里说明数据库中存在但是zset中没有数据,zset过期 所有需要被初始化  异步初始化zset 抛出异常
                    threadPoolTaskExecutor.execute(()->{
                        LambdaQueryWrapper<NoteLike> lastQueryWrapper = new LambdaQueryWrapper<>();
                        lastQueryWrapper.eq(NoteLike::getUserId, userId);
                        lastQueryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
                        lastQueryWrapper.orderByDesc(NoteLike::getUpdateTime);
                        lastQueryWrapper.last("limit 100");
                        List<NoteLike> lastNoteLikeList = noteLikeMapper.selectList(lastQueryWrapper);
                        if(CollUtil.isNotEmpty(lastNoteLikeList)){
                            // 非空 初始化ZSET
                            Object[] luaArgs = buildNoteLikeZsetArg(lastNoteLikeList);
                            DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                            script3.setResultType(Long.class);
                            script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire")));
                            redisTemplate.execute(script3,Collections.singletonList(noteLikeZSetKey),luaArgs);
                        }
                    });
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 走到这里说明数据库中都没有 布隆过滤器确实误判了 初始化布隆过滤器然后将当前记录加入到布隆过滤器中
                batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey);
                // 将新记录加入
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setResultType(Long.class);
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_like_note_and_expire.lua")));
                redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId,expiredSecond);
            }
```

对于是否已经点赞的逻辑完成之后，就是**确保布隆过滤器中已经存在新数据**，现在只需要**更新ZSET集合以及数据落库就行**

```Java
// 到这里说明之前没点赞过且一定更新完布隆过滤器了
        // 更新点赞zset数据 执行lua脚本 先判断是否zset存在 如果存在 判断是否有100个数据 如果有 则删除最早那个  如果没有则直接新增
        DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
        script2.setResultType(Long.class);
        script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        Long zsetResult = redisTemplate.execute(script2, Collections.singletonList(noteLikeZSetKey), noteId, expiredSecond);
        if(Objects.equals(zsetResult, NoteBloomLuaResultEnmu.ZSET_NOT_EXIST.getCode())){
            // ZSET不存在 进行初始化 找到当前用户最近的100个点赞用于初始化ZSET
            LambdaQueryWrapper<NoteLike> lastQueryWrapper = new LambdaQueryWrapper<>();
            lastQueryWrapper.eq(NoteLike::getUserId, userId);
            lastQueryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
            lastQueryWrapper.orderByDesc(NoteLike::getUpdateTime);
            lastQueryWrapper.last("limit 100");
            List<NoteLike> lastNoteLikeList = noteLikeMapper.selectList(lastQueryWrapper);
            if(CollUtil.isNotEmpty(lastNoteLikeList)){
                // 非空 初始化ZSET
                Object[] luaArgs = buildNoteLikeZsetArg(lastNoteLikeList);
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setResultType(Long.class);
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
                redisTemplate.execute(script3,Collections.singletonList(noteLikeZSetKey),luaArgs);
                // 再次调用新增ZSET的Lua脚本,将本次数据添加进去
                redisTemplate.execute(script2, Collections.singletonList(noteLikeZSetKey), noteId, expiredSecond);
            }
        }
        // 直接到这里标识zset的更新操作没问题 zset存在并且添加更新成功  准备异步MQ入库
        log.info("===>点赞消息准备异步落库,用户{},笔记{}",userId,noteId);
        LikeUnlikeMqDTO likeUnlikeMqDTO = LikeUnlikeMqDTO.builder().userId(userId)
                .noteId(noteId)
                .likeStatus(LikeStatusEnum.LIKE.getCode())
                .optionTime(LocalDateTime.now())
                .build();
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(likeUnlikeMqDTO)).build();
        String messageHead = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE + ":" + RocketMQConstant.TAG_LIKE;
        rocketMQTemplate.asyncSendOrderly(messageHead, message,String.valueOf(userId), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("用户{}点赞笔记{}落库操作成功",userId,noteId);
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("用户{}点赞笔记{}落库操作失败",userId,noteId);
            }
        });

        return Response.success();
```

#### 异步点赞落库消费者逻辑

异步点赞落库逻辑比较简单  **注意令牌桶限流 和 注意一下数据可能在库中是逻辑删除，所以添加数据的时候可能是新增也可能是更新**

```Java

@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ RocketMQConstant.TOPIC_LIKE_OR_UNLIKE,
        topic = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE,
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
public class LikeUnlikeNoteConsumer implements RocketMQListener<Message> {
    @Resource
    private NoteLikeMapper noteLikeMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(Message message) {
        rateLimiter.acquire();
        log.info("开始落库点赞消息{}",message);
        if(Objects.isNull(message)){
            return;
        }
        // 获取标签和消息体
        String tags = message.getTags();
        String body = new String(message.getBody());
        if(Objects.equals(tags,RocketMQConstant.TAG_LIKE)){
            handleLikeNoteTagMessage(body);
        }else if(Objects.equals(tags,RocketMQConstant.TAG_UNLIKE)){
            handleUnlikeNoteTagMessage(body);
        }else{
            log.info("消息{}点赞状态错误",message);
        }
    }

    /**
     * 笔记点赞
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        if(StringUtils.isBlank(bodyJsonStr)){
            return;
        }
        LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, LikeUnlikeMqDTO.class);
        LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteLike::getNoteId, likeUnlikeMqDTO.getNoteId());
        queryWrapper.eq(NoteLike::getUserId, likeUnlikeMqDTO.getUserId());
        NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
        if(Objects.isNull(noteLike)){
            noteLike = NoteLike.builder().userId(likeUnlikeMqDTO.getUserId())
                    .noteId(likeUnlikeMqDTO.getNoteId())
                    .createTime(likeUnlikeMqDTO.getOptionTime())
                    .status(likeUnlikeMqDTO.getLikeStatus().byteValue())
                    .build();
            noteLikeMapper.insert(noteLike);
            return;
        }
        noteLike.setStatus(likeUnlikeMqDTO.getLikeStatus().byteValue());
        noteLikeMapper.updateById(noteLike);

        // TODO 发送MQ计数服务

    }


}

```

## 取消点赞接口

取消点赞的接口设计，和点赞接口还是类似的

触发取消点赞请求---->判断笔记是否存在---->判断是否已经被点赞（只能对于已经点赞的笔记进行笔记取消点赞操作）---->更新ZSET点赞列表----->异步落库操作----->计数模块

![image-20241030103613891](../../ZealSingerBook/img/image-20241030103613891.png)

详细逻辑如下：

- 首先，判断想要取消点赞的笔记是否真实存在，若不存在，抛出业务异常，提示用户 “笔记不存在”；

- 判断笔记点赞布隆过滤器是否存在：

  - 若不存在布隆过滤器，查询数据库，校验是否有点赞过目标笔记，若没有点赞，则抛出业务异常，提示用户 “您未点赞该篇笔记，无法取消点赞”；并异步初始化布隆过滤器；

  - 若存在布隆过滤器，通过布隆过滤器来校验目标笔记是否点赞：

    - 若返回未点赞，则判断绝对正确，抛出业务异常，提示用户对应提示信息；

    - 若返回已点赞，可能存在很小几率的误判的情况（误判则代表该用户实际上没点赞，但是返回已经点赞）；

      > **误判是否能够容忍？**
      >
      > - 分析一波业务场景，大多数情况下，用户不会对刚刚点赞的笔记进行取消点赞，反而是以前点赞的笔记，没有价值了，进行了取消点赞。
      > - 另一方面，ZSET 只会缓存最新点赞的部分笔记，而为了校验这些小几率事件，当 ZSET 中不存在时，就不得不查数据库来校验，这就导致大部分流量都会打到数据库，导致数据库压力太大，反而得不偿失了！
      > - **相比较笔记点赞的场景，误判会影响用户正常的操作，必须得校验，这里的误判是可以容忍的！只需要在 MQ 异步数据落库的时候，再次校验一下即可，那么，接口中就无需操作数据库，保证取消点赞接口支持高并发写。**

- 若笔记已点赞，删除 `ZSET` 笔记点赞列表中对应的笔记 ID;

- 发送 MQ, 异步对数据进行更新落库；

对于布隆过滤器的误判的容忍，可能比较绕，可以对比一下点赞和取消点赞逻辑的区别

![image-20241030105338544](../../ZealSingerBook/img/image-20241030105338544.png)

### 主体逻辑编写

同样的 首先检测noteID的合理性 利用缓存+查库的方式综合查询  **先查本地缓存 再查redis 再查MySQL**

```Java
public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        // 判断noteId的合理性  先查本地缓存 再查redis  最后查库
        Long noteId = unlikeNoteReqVO.getNoteId();
        FindNoteByIdRspVO findNoteByIdRspVO = LOCAL_NOTEVO_CACHE.getIfPresent(noteId.toString());
        if(Objects.isNull(findNoteByIdRspVO)){ // 本地缓存中 没有数据 则需要查redis
            String noteCacheId = RedisConstant.getNoteCacheId(String.valueOf(noteId));
            String noteStr = redisTemplate.opsForValue().get(noteCacheId);
            findNoteByIdRspVO = JsonUtil.JsonStringToObj(noteStr, FindNoteByIdRspVO.class);
            if(Objects.isNull(findNoteByIdRspVO)){
                // redis也为空 查库 异步同步数据到缓存
                LambdaQueryWrapper<Note> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(Note::getId, noteId);
                queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
                Note note = noteMapper.selectOne(queryWrapper);
                if(Objects.isNull(note)){
                    throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }
                // 存在的话需要异步同步到本地缓存和redis缓存
                threadPoolTaskExecutor.submit(()->{
                    FindNoteByIdReqDTO build = FindNoteByIdReqDTO.builder().noteId(String.valueOf(noteId)).build();
                    try {
                        findById(build);
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            // redis中就存在缓存 异步保存到本地缓存即可
            FindNoteByIdRspVO finalFindNoteByIdRspVO = findNoteByIdRspVO;
            threadPoolTaskExecutor.submit(()->{
               LOCAL_NOTEVO_CACHE.put(String.valueOf(noteId), finalFindNoteByIdRspVO);
            });
        }
        ............
```

检测完noteId的合理性，我们**接下来就是检测是否已经点赞，我们只能对已经点赞过的笔记进行取消点赞操作  这里利用布隆过滤器进行**这里再次来梳理一下检测的逻辑：
首先查看布隆过滤器中是否有对应的redisKey，**第一种情况就是布隆过滤器不存在，不存在的话需要初始化布隆过滤器**

**第二种情况就是布隆过滤器存在，然后检测布隆过滤器中是否有对应的数据，如果没有，不存在误判，说明确实该用户还没有点赞该笔记，自然就不能进行取消点赞的操作  ； 如果有则说明用户确实对该笔记已经点赞过了，那么可以进行取消点赞操作，虽然存在误判，但是只要在数据库更新的时候条件中加入like_status==1即可（也就是确保修改的数据是原本就是取消点赞）**

```Java
// noteId合理 检测是否已经点赞过 使用Lua去布隆过滤器中检测
        Long userId = LoginUserContextHolder.getUserId();
        String bloomKey = RedisConstant.getBloomUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_unlike_check.lua")));
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(bloomKey), noteId);
        NoteBloomLuaResultEnmu resultEnmu = NoteBloomLuaResultEnmu.valueOf(result);
        switch(resultEnmu){
            case BLOOM_NOT_EXIST ->{
                //布隆过滤器不存在  查库判断是否点赞  初始化布隆过滤器
                // 先初始化布隆过滤器
                long expiredSecond =60*60*24 + RandomUtil.randomInt(60*60*24);
                threadPoolTaskExecutor.submit(()-> batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey));
                // 初始完毕后
                LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteLike::getUserId,userId).eq(NoteLike::getNoteId,noteId);
                NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
                if(Objects.isNull(noteLike) || noteLike.getStatus().equals(Byte.valueOf(String.valueOf(LikeStatusEnum.UNLIKE.getCode())))){
                    // 说明没有点赞过  抛出异常
                    throw new BusinessException(ResponseCodeEnum.NOT_LIKED_NOTE);
                }
                // 到这里说明确实点赞了 这里是数据库层面的取人 通过了已点赞校验
            }
            // 到这里说明布隆中没有数据 布隆中没有数据的不会存在误判 所以确实没有点赞  抛出异常
            case NOTE_UNLIKED ->{
                throw new BusinessException(ResponseCodeEnum.NOT_LIKED_NOTE);
            }
            // 到这里说明布隆过滤器中存在数据 通过了已点赞校验
            case SUCCESS -> {}
        }
```

对应的Lua脚本如下

```lua
-- LUA 脚本：点赞布隆过滤器

local key = KEYS[1] -- 操作的 Redis Key
local noteId = ARGV[1] -- 笔记ID

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验该篇笔记是否被点赞过(1 表示已经点赞，0 表示未点赞)
local isLiked = redis.call('BF.EXISTS', key, noteId)
-- 如果为1 则说明已经点赞 可以返回了 因为布隆一般不进行删除操作  如果为0则说明不存在 确实没点赞
if isLiked == 0 then
    return 1
end

return 0

```

经过switch过滤之后  就说明通过了点赞过滤，确保操作的数据是当前用户已经点赞了的数据  那么剩下的逻辑 就是处理zset的数据 然后异步落库

```Java
// 到这里说明布隆过滤波器中存在数据 通过了已点赞校验
        // 接下来执行的操作：删除zset中的点赞记录，异步落库数据库
        String unlikeNoteRedisKey = RedisConstant.buildUserNoteLikeZSetKey(userId);
        redisTemplate.opsForZSet().remove(unlikeNoteRedisKey,noteId);
        // 异步MQ消息落库
        String topicHeader = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE+":"+RocketMQConstant.TAG_UNLIKE;
        LikeUnlikeMqDTO build = LikeUnlikeMqDTO.builder()
                                .noteId(noteId)
                                .optionTime(LocalDateTime.now())
                                .userId(userId)
                                .likeStatus(LikeStatusEnum.UNLIKE.getCode()).build();
        Message<String> mqMessage = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(build)).build();
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(topicHeader,mqMessage,hashKey,new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
```

### 异步落库

类似于用户关注接口的异步落库操作  当出现热门文章 就可以出现大量的点赞和取消点赞  那么自然需要进行流量削峰 进行限流操作

```Java
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "zealsingerbook_group"+ RocketMQConstant.TOPIC_LIKE_OR_UNLIKE,
        topic = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE,
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
public class LikeUnlikeNoteConsumer implements RocketMQListener<Message> {
    @Resource
    private NoteLikeMapper noteLikeMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        rateLimiter.acquire();
        log.info("开始落库点赞消息{}",message);
        if(Objects.isNull(message)){
            return;
        }
        // 获取标签和消息体
        String tags = message.getTags();
        String body = new String(message.getBody());
        if(Objects.equals(tags,RocketMQConstant.TAG_LIKE)){
            handleLikeNoteTagMessage(body);
        }else if(Objects.equals(tags,RocketMQConstant.TAG_UNLIKE)){
            handleUnlikeNoteTagMessage(body);
        }else{
            log.info("消息{}点赞状态错误",message);
        }
    }

    /**
     * 笔记点赞
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        if(StringUtils.isBlank(bodyJsonStr)){
            return;
        }
        LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, LikeUnlikeMqDTO.class);
        LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteLike::getNoteId, likeUnlikeMqDTO.getNoteId());
        queryWrapper.eq(NoteLike::getUserId, likeUnlikeMqDTO.getUserId());
        NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
        Boolean updateSuccess = Boolean.FALSE;
        if(Objects.isNull(noteLike)){
            noteLike = NoteLike.builder().userId(likeUnlikeMqDTO.getUserId())
                    .noteId(likeUnlikeMqDTO.getNoteId())
                    .createTime(likeUnlikeMqDTO.getOptionTime())
                    .status(likeUnlikeMqDTO.getLikeStatus().byteValue())
                    .build();
            int i = noteLikeMapper.insert(noteLike);
            updateSuccess = i==0?updateSuccess:Boolean.TRUE;
        }else{
            noteLike.setStatus(likeUnlikeMqDTO.getLikeStatus().byteValue());
            int i = noteLikeMapper.updateById(noteLike);
            updateSuccess = i==0?updateSuccess:Boolean.TRUE;
        }
        if(!updateSuccess) {
            return;
        }
        // TODO 发送MQ计数服务
        org.springframework.messaging.Message<String> countMqMessage = MessageBuilder.withPayload(bodyJsonStr).build();
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_NOTE_LIKE, countMqMessage, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记点赞】MQ 发送异常: ", throwable);
            }
        });
    }

    /**
     * 笔记取消点赞
     * @param bodyJsonStr
     */
    private void handleUnlikeNoteTagMessage(String bodyJsonStr) {
        if(StringUtils.isBlank(bodyJsonStr)){
            return;
        }
        LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(bodyJsonStr, LikeUnlikeMqDTO.class);
        LambdaUpdateWrapper<NoteLike> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(NoteLike::getNoteId, likeUnlikeMqDTO.getNoteId());
        queryWrapper.eq(NoteLike::getUserId, likeUnlikeMqDTO.getUserId());
        queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
        queryWrapper.set(NoteLike::getStatus, LikeStatusEnum.UNLIKE.getCode());
        queryWrapper.set(NoteLike::getUpdateTime, likeUnlikeMqDTO.getOptionTime());
        int update = noteLikeMapper.update(queryWrapper);
        // TODO 发送MQ计数服务
        if(update==0){
            return;
        }
        org.springframework.messaging.Message<String> countMqMessage = MessageBuilder.withPayload(bodyJsonStr).build();
        rocketMQTemplate.asyncSend(RocketMQConstant.TOPIC_COUNT_NOTE_LIKE, countMqMessage, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
    }
}
```

MQ发送消息count计数模块异步处理进行数据的变化  我们需要更新count库中的信息  也要同时更新count对应在redis中的数据（注意 count在redis中的数据表现形式为hash格式  也就是更新某个字段）

同样 点赞操作和关注操作一样  正常情况下 每次点赞进行一次操作的话 数据只会有+1 或者 -1的变化 频繁的操作redis和数据库都是不好的  所以这里我们也需要采取数据聚合的方式  聚合一系列操作之后整合发送DB的操作

```Java
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "zealsinger_group"+ MQConstant.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstant.TOPIC_COUNT_NOTE_LIKE
)
public class CountNoteLikeConsumer implements RocketMQListener<String> {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String message) {
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtil.ObjToJsonString(bodys));
        List<CountNoteLikeUnlikeNoteMqDTO> list = bodys.stream().map(s -> JsonUtil.JsonStringToObj(s, CountNoteLikeUnlikeNoteMqDTO.class)).toList();
        Map<Long, List<CountNoteLikeUnlikeNoteMqDTO>> bodysMap = list.stream().collect(Collectors.groupingBy(CountNoteLikeUnlikeNoteMqDTO::getNoteId));
        Map<Long,Integer> countMap = new HashMap<>();
        bodysMap.forEach((nodeId,likeUnlikeOptions)->{
            int endCOunt = 0;
            for (CountNoteLikeUnlikeNoteMqDTO likeUnlikeOption : likeUnlikeOptions) {
                NoteLikeTypeEnum typeEnum = NoteLikeTypeEnum.getByCode(likeUnlikeOption.getLikeStatus());
                if(typeEnum==null){
                    continue;
                }
                switch (typeEnum) {
                    case LIKE: endCOunt++; break;
                    case UNLIKE: endCOunt--; break;
                }
            }
            countMap.put(nodeId,endCOunt);
        });
        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtil.ObjToJsonString(bodysMap));

        // 将数据添加到redis缓存中
        countMap.forEach((k,v)->{
            String countNoteLikeUnlikeRedisKey = RedisKeyConstants.buildCountNoteKey(k);
            Boolean isExisted = redisTemplate.hasKey(countNoteLikeUnlikeRedisKey);
            if(Boolean.TRUE.equals(isExisted)){
                // 缓存存在就新增 不存在则不会操作
                redisTemplate.opsForHash().increment(countNoteLikeUnlikeRedisKey,RedisKeyConstants.FIELD_LIKE_TOTAL,v);
            }
        });
        // 异步计数落库
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(bodys)).build();
        rocketMQTemplate.asyncSend(MQConstant.TOPIC_COUNT_NOTE_LIKE_2_DB,message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：笔记点赞数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：笔记点赞数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
```

所以从上可以看到 我们执行了三层异步 

**server中执行异步MQ消费点赞消息---->第一层异步添加限流令牌，修改noteLike表中的相关记录信息，异步调用第二层异步计数模块--->第二层异步计数模块利用聚合操作汇总操作结果，保证最终一致性，得到最终变化数据之后更新redis缓存，调用第三层异步进行count模块的异步落库操作---->利用令牌进行限流，执行落库操作**
