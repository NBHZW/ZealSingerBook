# 用户关系模块

背景：在我们的zealsingerbook项目中，用户之间是会存在很多关系的，最直接的关系就是关注，粉丝，好友这几个。随着用户数量的增长，用户之间的关系变得日渐复杂，如果管理这块大数据的关系，成为了用户关系模块的任务

## 用户模块的主要功能

关注和取关

查询某个用户的关注列表

查询某个用户的粉丝列表

查询用户之间的关系（主要用于前端UI区分，A关注B之前可能是关注按钮，A关注B之后会是不同的按钮，前端需要进行UI变化）

## 表创建

第一个表 即关注表

一个用户自然能关注多个用户 一个用户也能被多个用户关注 所以是个多对多的关系  为user_id创建索引 提高查询效率

| 字段              | 含义         |
| ----------------- | ------------ |
| id                | 主键ID       |
| user_id           | 用户ID       |
| following_user_id | 关注的用户ID |
| create_time       | 创建时间     |
| update_time       | 更新时间     |

```
CREATE TABLE `t_following` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `following_user_id` bigint unsigned NOT NULL COMMENT '关注的用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

```

粉丝表

其实就是关注表的逆向表 结构和上面基本基本差不多 就是某些字段的命名的区别

```
CREATE TABLE `t_fans` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `fans_user_id` bigint unsigned NOT NULL COMMENT '粉丝的用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户粉丝表';

```

## 关注接口设计

思考一下 关注逻辑会是怎么样的

请求开始 后端接收到请求后 先检测目标用户是不是自己 **不能自己关注自己**   ； 然后检测是否关注达到上限（为了防止刷  **用户关注有总量限制**）  ;  然后检测关注用户的合法性**即只能关注数据库中真实存在的用户数据**   ；  最后**写一条数据分别到粉丝库和关注库**中

![image-20241008215305957](../../ZealSingerBook/img/image-20241008215305957.png)

如上 是最直接的用户关注接口，那么想一想，关注用户对于客户而言是没有代价的，只要点击一下，那么如果就是如此设计，就会出现并发问题，每次关注需要对数据库进行二次操作 增大数据库的压力  更何况 在某个热点事件或者突然火起来的笔记 均有可能瞬间高并发

### 解决并发问题方案

（1）采用redis + MQ  削弱对库的集中操作

当接口其余操作都完成之后，可以将redis作为数据缓冲区，直接返回操作成功，然后写库操作使用MQ的异步操作从redis中拿数据发送消息通知进行存库消费

#### redis的zset数据结构

redis的zset数据结构可以支持高效的数据插入和查询，并且可以对键值保存权重value 从而比较好的进行排序

```
Redis 的有序集合（ZSet）是一种非常有用的数据结构，它允许你在集合中存储成员（member），并且每个成员都关联一个分数（score）
整体结构为key-score-member/value

与普通set类似 不会出现重复的元素(元素不能重复 但是评分可以  也就是可以存在 A 10 "B" 和  A 20 "C" 这样的多组数据) 但是因为评分在 对所有的成员进行了排序  默认由低到高进行排序
底层采用跳表或者压缩列表  redis会在这两种数据结构中进行不断地动态切换 从而达到内存和性能之间的一个折中平衡点

例如A关注了B  对应的两个数据库操作在redis中缓存的指令是
ZADD following:用户A 时间戳 用户B
ZADD fans:用户B 时间戳 用户A
利用时间戳 实现排序效果  从而保证越早执行的 时间戳越小 排序越靠前
```

#### 解决架构

暂时写入到redis  然后发送MQ进行异步消费  redis中存入关注列表的变化  粉丝列表的变化采用异步进行

![image-20241008223023079](../../ZealSingerBook/img/image-20241008223023079.png)

#### MQ消费逻辑

然后对于MQ消费者中的消费逻辑  MQ消费其实就是具体的对数据库的操作了 但是有需要注意，如果MQ消费者这边直接消费，那么这层缓冲和异步就很鸡肋，因为直接消费的话，相当于你把主线程的给数据库的压力异步到了子线程而已，对于数据库的压力并没有减轻，所以**我们需要进行流量削峰，控制消费速度**  除此之外，因为是异步消费，还需要注意幂等性，防止多次操作，最后才会数据库落库确认最终数据，然后更新粉丝列表数据（后续会存在计数服务 例如你有多少个粉丝和关注用户的技术服务  暂时没写）

![image-20241008223142558](../../ZealSingerBook/img/image-20241008223142558.png)

#### redis流程设计以及引入Lua脚本

主要来讲讲redis中相关的

![image-20241009085616192](../../ZealSingerBook/img/image-20241009085616192.png)

##### 问题一：ZSET的数据是否需要设置有效期

如果不设置有效期，我们可以来计算一下，一个zset数据分为了key-score-value三个数据  key为bigInt/String的类型，8个字节 + score为时间戳64位整数8字节 +  zset底层使用跳表可能存在**额外50字节开销**

那么每个zset数据的开销大概为66个字节，假设一个用户平均关注100个用户，那么就是66*100=6600字节=6.6KB  平台一共两亿用户6.6KB  *  200000000 = 1320GB 总体内存1.32TB  如果永久存储，肯定是不合适的，所以需要对ZSet设置一定的过期时间，防止内存占用过多

关于过期时间的设置，我们可以根据**二八原则**进行设置：**20%的人产生80%的数据，1%的人拥有巨大的粉丝量（大V）除去5%的小V，95%的是普通用户**  **所以我们完全可以根据不同的用户设置不同的过期时间**

##### 问题二：Redis相关的流程细节

整体流程就是

1：使用Exists执行检测对应的用户的关注列表是否存在

2.1：如果存在，那么使用ZCARD指令检测校验关注的人数是否达到上限

```
ZCARD指令可以检测对应的key的数量 从而实现计数功能
例如
ZADD A 10 "B"
ZADD A 20 "C"
ZCARD A  会返回2
可以猜想 我们redis中会存入许多的关注列表 所以可以依赖这个进行技术
```

2.1.2：利用ZSCORE指令校验目标用户是否已经被关注  如果已被关注会返回对应的score（在这里我们的score就是时间戳），如果不存在返回null

```
zscore返回有序集合中的成员的分数值
ZADD A 10 "B"
ZADD A 20 "C"  // 存入两个key为A的数据

ZRANGE salary 0 -1 A   # 返回key为A的所有的成员和分数值
1) "B"
2) "10"
3) "C"
4) "20"

ZSCORE salary B
返回 "10" 但是需要注意，返回的数据类型是字符串类型的
```

2.1.3：最后那个在使用ZADD添加关注的信息

2.1.4：MQ消费，对粉丝表进行操作

2.2：如果Redis中不存在现有的用户关注列表缓存数据(过期了),从数据库上查询当前用户的关注列表数据是否已经存在，如果存在则同步用户库中的关注列表数据到Redis并且设置过期时间 然后重新走流程 ； 如果不存在，则说明是这个用户的第一个关注，初始化ZSET并且设置过期时间，然后进行MQ消费即可

![image-20241009093302959](../../ZealSingerBook/img/image-20241009093302959.png)

##### 问题三：极端并发情况下的原子性问题

比如现在，我们规定一个用户上线关注100人，当其关注到99人的时候，虽然从实际操作而言，不可能会出现短时间内一个用户同时关注两个用户，但是为了保证程序的健壮性，需要考虑一下这种极端的情况，同一用户同时关注两人，那么就可能会导致第一次关注的数据还没ZADD进去，99还没变为100，第二次用户已经做完了ZCARD通过了人数上限检测，那么最终结果就是会是101个关注量，这显然是不符合我们的要求的

![image-20241009101414744](../../ZealSingerBook/img/image-20241009101414744.png)

为了保证Redis操作的原子性  我们可以引入Lua脚本，确保ZCARD和ZADD作为一组操作事件，具备原子性

##### Lua脚本

Redis中内置Lua脚本解释器，确保多个命令在脚本中以原子方式进行，并且Lua的存在可以减少Redis的网络延迟，客户端多个Redis命令请求，会被看成多个通信，自然会存在多个网络通讯延迟，但是Lua的存在，可以让其成员一组指令，从而减少网络交互次数，提高性能  ； 再者，单个Redis命令可能难以完成复杂的逻辑，但是通过Lua让一组指令一起执行，自然就能完成更复杂的逻辑了

我们将上面流程图中分为三个Lua脚本，可以解决极端高并发问题和大大提高效率

![image-20241009102523697](../../ZealSingerBook/img/image-20241009102523697.png)

## 关注接口编写

首先 进行redis操作的相关准备

### RedisConstant

redis常量类  关注信息key的获取和粉丝数据KEY的获取

![image-20241009103941278](../../ZealSingerBook/img/image-20241009103941278.png)

### 时间工具类--将时间转化为时间戳

然后在common模块中添加一个事件工具类，用于将LocalDateTime转化为时间戳信息

```
public class DateUtils {
    /**
     * LocalDateTime 转时间戳
     *
     * @param localDateTime
     * @return
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
```

![image-20241009104023001](../../ZealSingerBook/img/image-20241009104023001.png)

### 编写Lua脚本

在resource目录下创建lua目录 用于存放lua脚本  lua脚本的编写规则 可以看cloud笔记中有相关记录

我们将任务一的对应的Lua脚本对应如下

```lua
-- LUA 脚本：校验并添加关注关系

local key = KEYS[1] -- 操作的 Redis Key
local followUserId = ARGV[1] -- 关注的用户ID
local timestamp = ARGV[2] -- 时间戳

-- 使用 EXISTS 命令检查 ZSET 是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验关注人数是否上限（是否达到 1000）
local size = redis.call('ZCARD', key)
if size >= 1000 then
    return -2
end

-- 校验目标用户是否已经关注
if redis.call('ZSCORE', key, followUserId) then
    return -3
end

-- ZADD 添加关注关系
redis.call('ZADD', key, timestamp, followUserId)
return 0

```

解释

```
key = KEY[1] 获取KEY集合的第一个元素 也就是获得第一个键  通过Java中Redis相关指令传递参数
同理
local followUserId = ARGV[1] :获取第一个参数，这是要添加的关注对象的用户ID。
local timestamp = ARGV[2] ：获取第二个参数，这里是一个时间戳，通常用于排序关注关系的时间顺序。

local exists = redis.call('EXISTS', key) ：redis执行exists的执行，检查给定的Key是否存在。如果不存在(exists == 0)，则返回一个错误码，这里自定义为 -1, 表示缓存不存在

local size = redis.call('ZCARD', key) ：计算有序集合中的元素数量。如果关注数已经达到或超过1000，则返回-2表示已到达关注上限。
if redis.call('ZSCORE', key, followUserId)： 检查指定的用户ID是否已经在有序集合中。如果用户已经被关注，则返回-3表示重复关注。
redis.call('ZADD', key, timestamp, followUserId) ： 如果以上条件都满足，那么将新的关注关系添加到有序集合中，使用时间戳作为分数，这有助于之后按照时间顺序进行排序。
return 0 - 如果所有操作成功执行，则返回0表示成功。
```

自然，这里我们对应的返回了0  -1 -2 -3 根据不同的返回值判断问题出现在哪里 所以我们对应的需要一个枚举类来对应异常信息

![image-20241009163258897](../../ZealSingerBook/img/image-20241009163258897.png)

然后对应的设置ResponseCodeEnum枚举类

```Java
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
	// 省略...
    // ----------- 业务异常状态码 -----------
	// 省略...
    FOLLOWING_COUNT_LIMIT("RELATION-20003", "您关注的用户已达上限，请先取关部分用户"),
    ALREADY_FOLLOWED("RELATION-20004", "您已经关注了该用户"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
```

### Server逻辑编写

首先需要来介绍一下，如何在Java代码中指定执行lua脚本

Java中借助DefaultRedisScript 执行lua脚本

```java
// 构建当前用户的关注列表
        String followingKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        // 创建脚本执行对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // 设置脚本资源路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        // 设置脚本返回值类型
        script.setResultType(Long.class);
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 当前时间转时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(now);
        // 执行 Lua 脚本，拿到返回结果  该方法的第一个参数对应lua脚本中的KEY[]集合 其余的参数对应ARGV[] 第一个参数就是ARGV[1]  第二个就是ARGV[2]  依次类推  
        Long result = redisTemplate.execute(script, Collections.singletonList(followingKey), followUserId, timestamp);
```

然后就是对结果进行分析 因为我们的返回值对应了四种返回值 我们需要分别进行判断操纵  使用switch-case即可

```Java
// 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);

        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");

        // 判断返回结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
            // ZSet 关注列表不存在
            case ZSET_NOT_EXIST -> {
                // TODO
               
            }
        }
```

对于Zset不存在的情况，根据我们上述的流程图，我们需要查库进行信息同步，如果库中有数据，那么就全量同步到redis中设置过期时间，如果库中没有数据，那么直接放到redis中保存设置过期时间

逻辑如下

```Java
// ZSet 关注列表不存在
            case ZSET_NOT_EXIST -> {
                // TODO 关注列表不存在 需要查库找寻载入缓存信息
                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                LambdaQueryWrapper<Following> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(Following::getUserId,userId);
                List<Following> followingList = followingMapper.selectList(queryWrapper);
                if(CollUtil.isEmpty(followingList)){
                    // 如果为空 那么说明是第一次关注用户  采用ZADD添加信息设置信息即可
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                    script2.setResultType(Long.class);
                    // TODO 计数服务没有搭建 到时候搭建完毕后 在这里根据计数结果进行不同过期时间的设置
                    redisTemplate.execute(script2, Collections.singletonList(followingKey), followUserId, timestamp,expireSeconds);
                }else{
                    // 则说明原本有数据  全量同步到redis中即可
                    DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                    script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                    script3.setResultType(Long.class);
                    Object[] luaArgs = buildLuaArgs(followingList, expireSeconds);
                    redisTemplate.execute(script3, Collections.singletonList(followingKey), luaArgs);
                }
            }


 /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常 主要是针对 已关注 和 关注数量达到上线
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }

    /**
     * 构建 Lua 脚本参数
     *
     * @param followingDOS
     * @param expireSeconds
     * @return
     */
/*
这里用于构建全量数据同步redis操作的参数
第一个参数followingDOS就是我们查到的当前登录user的关注列表
第二个参数exporeSeconds就是过期时间
我们lua中拿取传入的数据是一个一个拿的 所以我们这里用object[]数据
数组的长度为 关注数*2+1
为啥呢  假设一下 我们现在全量导入A用户关注列表  假设A关注了B C D
那么我们该方法返回的obejct数组其实就是
B ； 关注B的时间点(时间戳) ； C ；关注C的时间点 ; D ；关注D的时间点 ；过期时间
可以参考下面的Lua脚本获取数据的方式进行理解
*/
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds) {
        int argsLength = followingDOS.size() * 2 + 1; // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FollowingDO following : followingDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime()); // 关注时间作为 score
            luaArgs[i + 1] = following.getFollowingUserId();          // 关注的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }
```



```lua
// 第一次关注 直接zadd添加到redis中
local key = KEYS[1] -- 操作的 Redis Key
local followUserId = ARGV[1] -- 关注的用户ID
local timestamp = ARGV[2] -- 时间戳
local expireSeconds = ARGV[3] -- 过期时间（秒）

-- ZADD 添加关注关系
redis.call('ZADD', key, timestamp, followUserId)
-- 设置过期时间
redis.call('EXPIRE', key, expireSeconds)
return 0

```

```lua
// 全量增加  需要将数据库中的数据都放入到redis中
-- 操作的 Key
local key = KEYS[1]

-- 准备批量添加数据的参数
local zaddArgs = {}

-- 遍历 ARGV 参数，将分数和值按顺序插入到 zaddArgs 变量中
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs, ARGV[i])      -- 分数（关注时间）
    table.insert(zaddArgs, ARGV[i+1])    -- 值（关注的用户ID）
end

-- 调用 ZADD 批量插入数据
redis.call('ZADD', key, unpack(zaddArgs))  -- unpack是内置函数 可以解析zaddArgs列表中的所有元素拆分为不同指令参数执行redis命令

-- 设置 ZSet 的过期时间
local expireTime = ARGV[#ARGV] -- 最后一个参数为过期时间
redis.call('EXPIRE', key, expireTime)

return 0

```

#### 小测一手

第一次能成功关注并且redis中有相关数据，第二次模拟用户存在和自己关注自己和已经关注的三种情况都能正常出现就是没有问题

![image-20241009204712856](../../ZealSingerBook/img/image-20241009204712856.png)

### MQ消费--Tag标签

我们知道RocketMQ中信息是可以按照Topic之间进行区分的，其实还存在使用Tga标价签进行二级分类，即同一个Topic下的不同Tag

简单而言就是，Topic之间不应该存在关联（每个Topic之间是不同的类型  不应该会有交融  否则容易出现低并发事件和高并发事件采用同一个Topic，导致消费的时候高并发事件进场被消费而低并发事件一直处于等待状态 从而产生“饿死”现象）

tag属于topic下的二级分类，用于区分同一Topic下的相互关联的消息，一般tag之间有父子关系或者流程先后关系

![image-20241009204826587](../../ZealSingerBook/img/image-20241009204826587.png)

在我们的项目中，关注和取关就可以认为是一个Topic下的不同tag

------

首先是给user-relation模块添加rocketmq的相关依赖和配置信息

```
<!-- Rocket MQ -->
<dependency>
   <groupId>org.apache.rocketmq</groupId>
   <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>



rocketmq:
  name-server: 192.168.17.131:9876 # name server 地址
  producer:
    group: zealsingerbook_group
    send-message-timeout: 3000 # 消息发送超时时间，默认 3s
    retry-times-when-send-failed: 3 # 同步发送消息失败后，重试的次数
    retry-times-when-send-async-failed: 3 # 异步发送消息失败后，重试的次数
    max-message-size: 4096 # 消息最大大小
  consumer:
    group: zealsingerbook_group
    pull-batch-size: 5 # 每次拉取的最大消息数
```

创建消费请求对象

![image-20241009211904165](../../ZealSingerBook/img/image-20241009211904165.png)

创建MQ相关常量Topic和Tag

![image-20241009212031518](../../ZealSingerBook/img/image-20241009212031518.png)

接下来 就可以补充Server中的关于MQ发送消费的逻辑

![image-20241009214205873](../../ZealSingerBook/img/image-20241009214205873.png)

添加的代码如下

```Java
// 异步MQ消费通知
        MqFollowUserDTO mqFollowUserDTO = MqFollowUserDTO.builder().userId(userId).followUserId(followUserId).createTime(now).build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(mqFollowUserDTO))
                .build();
        // 采用  Topic:str 的形式作为topic 后面的str就是tag
        String header = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW+":"+RocketMQConstant.TAG_FOLLOW;
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", mqFollowUserDTO);
        // 异步发送MQ消息  提高性能
        rocketTemplate.asyncSend(header, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
```

首先构建我们的Message对象MqFollowUserDTO  然后将其封装为SpringFrameWork的Message对象  利用之前的工具类将Object对象转化为String对象

```Java
Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(mqFollowUserDTO)).build();
```

然后使用RocketMQ的异步发送信息  **第一个参数为topic:tag信息 这样标记的topic后半部分的tag能被RocketMQ识别为tag  第二个为消息内容  第三个因为是异步执行 所以需要有回调函数  发送成功和发送失败的时候的解决方案**

```Java
rocketTemplate.asyncSend(header, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
```

到这里为止，消息是发送过去了，然后是消费MQ的消息的逻辑

创建一个Consumer类 然后打上注解监听接收对应的Topic的消息  实现RocketMQ中的接口RocketMQListener< Message > 实现方法

接收到的对象类型为Message   然后使用getbody得到消息体  通过getTag得到我们head中的 : 后面的tag部分 

这里需要注意的是  直接上和之前不一样 没指定消费模式 ，我们之前笔记模块中的删除本地缓存的消费方式是广播方式，因为所有的监听者都需要消费，但是在业务关系中，消费者的作用是消费消息从而操作库，添加数据，只需要保证有一个消费者消费到即可，所以即点对点即可，这是RocketMQ默认的消费方式，如果也采用广播方式，数据库中将多出很多重复的数据

![image-20241009214613732](../../ZealSingerBook/img/image-20241009214613732.png)

小测一手  发现能成功

![image-20241009214850571](../../ZealSingerBook/img/image-20241009214850571.png)

## 消费逻辑的细节补充

### 保证幂等

当MQ发送给消息和消费消息的时候，因为网络问题，消费失败等问题，会导致消息重复，那么如何**保证消息的幂等性**呢

对于目前模块的功能而言 消费者的最终结果就是往库中添加了数据 幂等的必要性就是防止数据库中添加多条相同的数据

所以我们采取的方案是 ： **给follow表的userID和following_user_id添加唯一复合索引**

```
ALTER TABLE t_following ADD UNIQUE uk_user_id_following_user_id(user_id, following_user_id);
```

同样的 我们可以为fans表同样加上唯一复合索引

```
ALTER TABLE t_fans ADD UNIQUE uk_user_id_fans_user_id(user_id, fans_user_id);
```

### 补充消费逻辑

上述代码，我们看到已经能获取到对应的Tag了，那么接下来就可以按照Tag进行对应的操作 我们分别创建followHander 和 unfollowHandler两个处理方法 分别处理关注和取关业务

![image-20241009220954739](../../ZealSingerBook/img/image-20241009220954739.png)

```Java
public class FollowAndUnFollowConsumer implements RocketMQListener<Message> {
    @Resource
    private FollowingMapper followingMapper;
    @Resource
    private FansMapper fansMapper;
    @Override
    public void onMessage(Message message) {
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();
        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if(Objects.equals(tags,RocketMQConstant.TAG_FOLLOW)){
            //关注操作
            followingHandler(bodyJsonStr);
        } else if (Objects.equals(tags,RocketMQConstant.TAG_UNFOLLOW)) {
            //取关操作
            unfollowingHandler(bodyJsonStr);
        }
    }
    
    /**
     * 关注操作方法
     */
    public void followingHandler(String message){
        MqFollowUserDTO mqFollowUserDTO = JsonUtil.JsonStringToObj(message, MqFollowUserDTO.class);
        if(mqFollowUserDTO == null){
            return;
        }
        Long userId = mqFollowUserDTO.getUserId();
        Long followUserId = mqFollowUserDTO.getFollowUserId();
        // 对于已经设置了时间的成员 不会被自动填充
        LocalDateTime followTime = mqFollowUserDTO.getCreateTime();
        Following following = Following.builder().userId(userId).followingUserId(followUserId).createTime(followTime).build();
        Boolean isSuccess = transactionTemplate.execute(status -> {
            try {
                followingMapper.insert(following);
                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("", e);
                return false;
            }

        });
        log.info("### 关注操作数据库操作结果: {}",isSuccess);

        // TODO 操作redis的fans相关的记录
    }
    /**
     * 取关操作
     */
    public void unfollowingHandler(String message){

    }
}
```

可以看到 我们在followHandler方法中  采用编程式事务的方式，保证整个流程的事务的原子性，发生异常及时回滚

### 令牌桶限流

我们之前有说到过，如果只是加MQ会有点鸡肋，因为我们进行这么多操作的原因，无非是为了防止数据库一下子承受太多，就我们目前这个处理方式，你会发现，假设我们100请求打过来，按照以前的逻辑假如数据库无法承受，对于我们现在写的而言，其实也承担不起，为啥，我们只是将这个操作异步消费了而已，100请求对应100个followHandler方法的运行，还是100个请求直接打过来了，所以其实对于数据库的高并发访问缓冲效果其实并没有多少

所以，**我们这里要采用令牌桶技术，为MQ消费速度进行一个控制，进行流量削峰**，对于上述案例而言就是 将虽然发送了100各信息消费 但消费者不是一股脑一次性都消费 可以每次只消费10条或者其他数目

#### 令牌桶技术介绍

令牌桶（Token Bucket）是一种常见的限流算法，用于控制数据流量的传输速率，它允许一定的速率通过，同时能灵活的处理流量突增的现象，主要用于网络带宽控制，API限流，流量削峰等场景

令牌桶的工作原理：

**1：维护了一个容量为m的装有令牌的桶，即最多容纳m个令牌**
**2：令牌的生成：以一个固定的速率往桶中添加令牌，当桶容量达到上限的时候，超出的令牌会被舍弃**
**3：请求的处理：当有请求到来，会检测桶中是否存在令牌，如果有，系统消耗一个令牌并且允许该请求，如果没有，则拒绝或者延迟处理**
**4：突发处理:因为桶里可以积累一定数量的令牌，令牌桶算法允许一定程度的突发流量。在请求量少时，令牌会积累，当流量突然增加时，这些积累的令牌可以帮助通过一部分突发流量。**

#### Guava令牌桶

Guava包含了很多工具库，力面就有能充当令牌桶工具

Guava实现令牌桶主要依赖于依赖中定义提供的**RateLimiter**这个工具类，Guava令牌桶其特点是：**平滑限流**，通过令牌的生成速率来确保流量的平滑度，RateLimiter默认采用“**平滑突发限流**”  允许一定的流量突发 ； **平滑预热限流**：提供了一种平滑预热限流，当系统启动的时候，令牌生成速度逐渐增加，适用于在服务需要冷启动或者预热阶段的场景  ； **灵活配置**：根据需求灵活调整令牌生成速度

##### 快速入门

导入依赖

```xml
<dependency>
   <groupId>com.google.guava</groupId>
   <artifactId>guava</artifactId>
</dependency>
```

使用

```Java
@Component
@RocketMQMessageListener(consumerGroup = "xiaohashu_group", // Group 组
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW // 消费的主题 Topic
)
@Slf4j
public class FollowUnfollowConsumer implements RocketMQListener<Message> {

    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(Message message) {
        // 流量削峰：调用acquire()方法 通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 消息体
        String bodyJsonStr = new String(message.getBody());
        
        // 省略...
    }
    // 省略...
}
```

还可以搭配上nacos的配置文件热部署

```
        <!-- Nacos 配置中心 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>

```

在配置文件中配置令牌桶容量

![image-20241010124752271](../../ZealSingerBook/img/image-20241010124752271.png)

```
mq-consumer: # MQ 消费者
  follow-unfollow: # 关注、取关
    rate-limit: 5000 # 每秒限流阈值
```

然后在配置类中读取配置文件信息并且注入RateLimiter的Bean对象

![image-20241010124850711](../../ZealSingerBook/img/image-20241010124850711.png)

## 粉丝列表更新

在上述的消费中，我们消费完MQ之后，只是更新了对应的following表，还是fans表没有进行操作，也就是说，我们只是个当前用户的关注+1，还没有给被关注用户的粉丝+1

那我们来思考一，粉丝表的处理逻辑该如何？和关注表的区别在哪？

1：粉丝表无上限。关注表可以设置上限，但是粉丝表肯定是无上限的，关注表我们都计算过，不适合全量长期放到缓存中，对于数据更多的粉丝表自然也是不行的

2：对于查询粉丝列表的返回，不能全部返回，如果一次性返回，则会数据量过大导致接口响应时间很长，为了防止响应时间过长，我们可以采用分页的方式进行返回（每页十个，一共最多500页）

3：如上 我们采用分页，但是我们也不需要返回全部，如上述，我们最多返回500页面的数据，如果超过500页数据呢？第一，从用户角度而言，不太可能翻500页去看粉丝列表，超出500的数据我们可以选择查表 ； 第二种方式，为了防止对服务器的恶意攻击，我们完全可以拒绝500页之后的查询

### 粉丝列表的更新

首先 这个粉丝列表的相关功能，可以写到粉丝相关的接口中，其整体逻辑就是

**查询粉丝列表--->redis中是否存在---->如果不存在，查库保存至缓存中后返回 ；反之如果存在直接返回**

![image-20241010210454343](../../ZealSingerBook/img/image-20241010210454343.png)

**对于关注操作引起的粉丝数增加：查询是否存在Redis数据---->如果存在则判断redis数量是否超过了5000，如果没超过则加入反之则删除最旧的数据后添加---->如果不存在，则说明是第一个粉丝或者未被记录，直接保存到表中即可，下次查询的时候就会被保存记录**

![image-20241010210504403](../../ZealSingerBook/img/image-20241010210504403.png)

我们首先来写关注操作带来的粉丝列表的变化，按照上面MQ消费的逻辑编写即可，首先是对应的Lua脚本文件

```
local key = KEYS[1] -- 操作的 Redis Key
local fansUserId = ARGV[1] -- 粉丝ID
local timestamp = ARGV[2] -- 时间戳

-- 使用 EXISTS 命令检查 ZSET 粉丝列表是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 获取粉丝列表大小Zcard 命令用于计算集合中元素的数量。
local size = redis.call('ZCARD', key)

-- 若超过 5000 个粉丝，则移除最早关注的粉丝 ZPOPMIN 删除并返回排序中得分最低的成员
if size >= 5000 then
    redis.call('ZPOPMIN', key)
end

-- 添加新的粉丝关系
redis.call('ZADD', key, timestamp, fansUserId)
return 0

```

### 补充业务逻辑

在上面的关注的MQConsumer逻辑中，添加对粉丝zset列表的操作

```java 
.....................
Boolean isSuccess = transactionTemplate.execute(status -> {
            try {
                followingMapper.insert(following);
                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("", e);
                return false;
            }

        });
        log.info("### 关注操作数据库操作结果: {}",isSuccess);

        // 操作redis的fans相关的记录
        if(Boolean.TRUE.equals(isSuccess)){
            String fansKey  = RedisConstant.getFansKey(String.valueOf(followUserId));
            // 创建脚本执行对象
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            // 设置脚本资源路径
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_update_fans_zset.lua")));
            // 设置脚本返回值类型
            script.setResultType(Long.class);
            long timestamp = DateUtils.localDateTime2Timestamp(followTime);
            redisTemplate.execute(script, Collections.singletonList(fansKey), userId, timestamp);
            Fans fans = Fans.builder().userId(followUserId).fansUserId(userId).createTime(followTime).build();
            fansMapper.insert(fans);
        }
```

![image-20241010222841525](../../ZealSingerBook/img/image-20241010222841525.png)

## 取关接口

取关操作可以借助关注操作的思想：

去除数据库中的关注信息和粉丝信息  以及 缓存zset中的关注列表信息和粉丝信息  按照我们的缓存设计思想  我们整体流程应该是 先去除缓存中的数据 然后再去异步操作库

自然 还得需要检测

**1：取消关注的用户是否存在/合理性  2：取消关注的用户是不是自己，不能自己取消关注自己   3：取消关注的用户必须是已经关注的用户**

那么根据这个 我们能比较快速的写出取消关注的逻辑

```Java
@Override
    public Response<?> unfollow(UnFollowReqDTO unFollowReqDTO) {
        Long unfollowUserId = unFollowReqDTO.getUnfollowUserId();
        Long userId = LoginUserContextHolder.getUserId();
        // 不能自己取关自己
        if (Objects.equals(userId, unfollowUserId)) {
            throw new BusinessException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }
        // 用户不存在 非法用户
        Boolean exist = userRpcServer.checkUserExist(unfollowUserId);
        if(!exist){
            throw new BusinessException(ResponseCodeEnum.USER_NOT_EXITS);
        }
        // 执行lua脚本

        // 构建关注列表的key
        String followingKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        // 构造脚本执行对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // 设置返回值
        script.setResultType(Long.class);
        // 设置资源路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_remove.lua")));
        // 执行
        Long executeResult = redisTemplate.execute(script, Collections.singletonList(followingKey), unfollowUserId);
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(executeResult);
        if (Objects.isNull(luaResultEnum)) {
            throw new RuntimeException("Lua 执行失败");
        }
        // 返回-1 关注列表缓存暂且不存在  不用管 直接MQ操作库; 如果返回0则说明Lua操作成功缓存中已经删除所以也是直接可以去删库了  所以这里只有-4的时候需要额外异常处理
        if(LuaResultEnum.UNFOLLOWED.getCode().equals(luaResultEnum.getCode())){
            throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
        }
        // MQ异步消费数据库
        MqUnFollowUserDTO mqUnfollowUser = MqUnFollowUserDTO.builder().unfollowUserId(String.valueOf(unfollowUserId)).userId(String.valueOf(userId)).unfollowTime(LocalDateTime.now()).build();
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(mqUnfollowUser)).build();
        String header = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW+":"+RocketMQConstant.TAG_UNFOLLOW;
        rocketTemplate.asyncSend(header,message,new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }
```

对应的Lua脚本负责的任务应该是检测列表是否存在和是否为已经关注的用户，如果条件都满足那么就需要从缓存following的zset列表中将对应的数据删除，反之，如果列表不存在或者还没关注该用户，那么又会进行需要进行对应的其他操作

```lua
-- LUA 脚本：校验并移除关注关系
local key = KEYS[1] -- 操作的 Redis Key
local unfollowUserId = ARGV[1] -- 关注的用户ID

-- 使用 EXISTS 命令检查 ZSET 是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验目标用户是否被关注
local score = redis.call('ZSCORE', key, unfollowUserId)
if score == false or score == nil then
    return -4
end

-- ZREM 删除关注关系
redis.call('ZREM', key, unfollowUserId)
return 0
```

可以看到  我们这里对于zset列表不存在的操作是和正常一样 直接去MQ消费数据库 哈总那边的逻辑稍微不一样，我们来对比一下看看

**哈总：如果zset不存在，那么就从查库+将记录全量同步到following的zset缓存中+再次执行上述Lua脚本进行校验判断，完成后MQ异步操作数据库**

**我们的：如果zset不存在，那么就直接MQ操作库就行（zset不存在的原因：用户最近没有关注，关注zset缓存过期）**

（自我感觉大差不差的）

### MQ消费者逻辑

在上面 我们最后自然是异步消费MQ消息

![image-20241013153503114](../../ZealSingerBook/img/image-20241013153503114.png)

MQ消息体结构如下

![image-20241013153518789](../../ZealSingerBook/img/image-20241013153518789.png)

消费者逻辑就很简单了：**解析出消息中的userId和unfolloUserId，然后分别操作following库和fans库即可，最后清楚fans的zset缓存即可**

可以看到 我们其实对于操作的异步执行还是同步执行其实也是有讲究的

**我们这里是取消关注的接口 直接需要明显的是用户要从关注列表中看不到取消的用户 这个是直接效果  所以操作following的zset缓存列表需要同步执行**

**但是对于被取消关注的用户而言，本身就是被动操作，所以不一定需要及时知道，允许异步操作稍微慢点**

```java 
@Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();
        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if(Objects.equals(tags,RocketMQConstant.TAG_FOLLOW)){
            //关注操作
            followingHandler(bodyJsonStr);
        } else if (Objects.equals(tags,RocketMQConstant.TAG_UNFOLLOW)) {
            //取关操作
            unfollowingHandler(bodyJsonStr);
        }

    }

/**
     * 取关操作
     */
    public void unfollowingHandler(String message){
        MqUnFollowUserDTO mqUnFollowUserDTO = JsonUtil.JsonStringToObj(message, MqUnFollowUserDTO.class);
        if(mqUnFollowUserDTO == null){
            return;
        }
        String userId = mqUnFollowUserDTO.getUserId();
        String unfollowUserId = mqUnFollowUserDTO.getUnfollowUserId();
        LocalDateTime unfollowTime = mqUnFollowUserDTO.getUnfollowTime();
        Boolean executeSuccess = transactionTemplate.execute(status -> {
            try {
                // 关注库表操作
                LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
                followingLambdaQueryWrapper.eq(Following::getUserId, userId);
                followingLambdaQueryWrapper.eq(Following::getFollowingUserId, unfollowUserId);
                followingMapper.delete(followingLambdaQueryWrapper);

                // 粉丝表操作
                LambdaQueryWrapper<Fans> fansLambdaQueryWrapper = new LambdaQueryWrapper<>();
                fansLambdaQueryWrapper.eq(Fans::getUserId, unfollowUserId);
                fansLambdaQueryWrapper.eq(Fans::getFansUserId, userId);
                fansMapper.delete(fansLambdaQueryWrapper);

                return true;
            } catch (Exception e) {
                return false;
            }
        });
        log.info("### 取关相关数据库操作完成: {}",executeSuccess);
        // 从取关者的粉丝表zset中去除
        if(Boolean.TRUE.equals(executeSuccess)){
            String fansKey  = RedisConstant.getFansKey(String.valueOf(unfollowUserId));
            redisTemplate.opsForZSet().remove(fansKey,userId);
        }
    }
```

## RocketMQ实现消息顺序消费

就目前而言 我们有了关注和取消关注的接口  对应的有了两种MQ消息通知  此时我们需要考虑一个问题  

**消息的消费顺序对于逻辑会不会有影响，如果有，如何去避免？**

考虑如下场景：

A用户对B用户快速进行如下操作  **关注->取消关注-->关注**

那么按照正常情况下 会出现  **MQ需要消费关注消息，给表添加数据，新增fans的zset集合数据--->MQ消费取消关注的消息，删库，去除fans的zset集合--->MQ需要消费关注消息，给表添加数据，新增fans的zset集合数据三个操作**

在上述情况中，我们可以看到，我们同一个消费者需要向同一个Topic发送多条消息，在RocketMQ中，给出了一张官方贴图如下，为了保证消息的水平拓展，对于同一个Topic接收到的消息，会使用多个MessageQueue队列存放储存消息  自然 多条个消息队列之间的的消费是并行执行的

![image-20241013162411572](../../ZealSingerBook/img/image-20241013162411572.png)

那么现在就在上述情景中出现了问题：**短时间内发送这三条消息，可能会被分配到三个不同的消息队列中，然而又因为消息队列之间是并行的，最终就会导致实际的消费顺序可能不是 关注--->取关--->关注  比如会变成  关注--->关注--->取关**  最终逻辑就变成完全相反的操作，这明显是很不合理的  并且我的消费者也是分布式集群部署的，有可能消费者集群中的节点都消费到了消息，导致重复消费

so  我们需要控制消费顺序

可以先通过如下代码观察一下RocketMQ中同一个Topic在应该对多个消息的时候的队列情况

如下代码中 我们模拟出当i为偶数的时候发送关注的MQ消息，当i为奇数的时候发送取消关注的MQ消息

```Java
@SpringBootTest
@Slf4j
class MQTests {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    // 省略...

    /**
     * 测试：发送对同一个用户关注、取关 MQ
     */
    @Test
    void testSendFollowUnfollowMQ() {
        // 操作者用户ID
        Long userId = 27L;
        // 目标用户ID
        Long targetUserId = 100L;

        for (long i = 0; i < 10; i++) {
            if (i % 2 == 0) { // 偶数发送关注 MQ
                log.info("{} 是偶数", i);

                // 发送 MQ
                // 构建消息体 DTO
                FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                        .userId(userId)
                        .followUserId(targetUserId)
                        .createTime(LocalDateTime.now())
                        .build();

                // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
                Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO))
                        .build();

                // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
                String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;

                // 发送 MQ 消息
                SendResult sendResult = rocketMQTemplate.syncSend(destination, message);

                log.info("==> MQ 发送结果，SendResult: {}", sendResult);
            } else { // 取关发送取关 MQ
                log.info("{} 是奇数", i);

                // 发送 MQ
                // 构建消息体 DTO
                UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                        .userId(userId)
                        .unfollowUserId(targetUserId)
                        .createTime(LocalDateTime.now())
                        .build();

                // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
                Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO))
                        .build();

                // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
                String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;

                // 发送 MQ 消息
                SendResult sendResult = rocketMQTemplate.syncSend(destination, message);

                log.info("==> MQ 发送结果，SendResult: {}", sendResult);
            }
        }
    }

}

```

最终打印结果如下  可以看到 确实放到了不同的Queue队列中处理消息

![image-20241013204506780](../../ZealSingerBook/img/image-20241013204506780.png)

那如何保证消息的顺序消费？可以分别从消息提供者和消息消费者入手：

**在发送端，保证消息都按照顺序发送到同i个队列中，保证先进先出**

**在消费端，从单个队列中按照顺序拿取消费消息，消费端不能并发去消费，只能由有个服务实例去消费该队列**

### 发送端处理

修改发送端的发送异步MQ消息的逻辑

使用asyncSendOrderly方法，允许多加入一个参数作为hashKey，同一个HashKey会投递到同一个消息队列中

**这里我们使用UserId作为HashKey 就能保证这个人的所有相关操作都发送到一个队列中 先进先出进行操作**

**为啥不选用followId呢？如果该被关注的人是个大V或者热点事件人物，就可能在某段时间内被大量关注，从而导致该短时间内这个Queue队列满造成单点瓶颈，不适合高并发场景，而关注者作为HashKey，一个人的操作量始终有限，关注者也肯定比被关注者多，从而保证消息更加的分散**

```
 // 采用  Topic:str 的形式作为topic 后面的str就是tag
        String header = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW+":"+RocketMQConstant.TAG_FOLLOW;
        String hashKey = String.valueOf(userId);
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", mqFollowUserDTO);
        // 异步发送MQ消息  提高性能
        rocketTemplate.asyncSendOrderly(header, message, hashKey,new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
```

![image-20241013205513589](../../ZealSingerBook/img/image-20241013205513589.png)

### 接收端处理

消费端主要是要顺序消费，直接在注解中添加属性即可

```Java
consumeMode = ConsumeMode.ORDERLY
```

![image-20241013211809965](../../ZealSingerBook/img/image-20241013211809965.png)

# 批量获取用户信息接口开发

接下来是**关注列表接口的开发**

可以看到关注列表中返回的应该是 **头像+昵称+简介**

![image-20241013212032894](../../ZealSingerBook/img/image-20241013212032894.png)

我们之前是存在关注列表ZSET的缓存的，但是Redis中只存在了对应的followingUserId，按照我们目前的需求，还需要再次调用User模块查找我们需要的信息

![image-20241013212317823](../../ZealSingerBook/img/image-20241013212317823.png)

目前，我们在Note笔记页面的时候，那里需要userId查询用户信息，但是那里我们只需要头像+昵称的信息而不需要简介，并且那个接口我们是单个查询，效率是比较低的，自然是不适合在目前这个需求下进行的

所以我们需要再写一个根据ID集合批量获取用户信息的接口

## User模块主体编写

入参

入参设置@Size进行集合大小限制，第一是防止null 第二是防止恶意访问刷取数据，设置最大数值为10是比较复合前端的分页查询每页大小，防止返回过多

```
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FindUsersByIdsReqDTO {
    @NotNull(message = "用户 ID 集合不能为空")
    @Size(min = 1, max = 10, message = "用户 ID 集合大小必须大于等于 1, 小于等于 10")
    private List<Long> ids;
}
```

出参  可以在原本的用户相信接口的返回值下面添加  简介  属性即可

```
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserByIdRspDTO {
    /**
     * 用户主键ID
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 简介
     */
    private String description;
}
```

整理一下整体逻辑：

接收到前端传来的UserID的List，通过UserIDList获得之前接口缓存的信息对应的redis缓存Key（ user:infor:{userId} ）的集合

通过redisKeyList，通过mutilget方法传入keyList从而得到valueList（提高效率）

然后将redisValues转化为出参resultList  将resultList去除空值后比对一下resultList的长度和入参userIdList的长度，从而判断缓存中的数据是否满足返回需求

如果等于，则说明缓存中存在所有需要返回的数据，直接返回resultList即可

如果不相等，则说明缓存中没有数据或者只有部分数据

如果没有数据，直接通过userIDList查出数据后返回即可

如果是部分数据，那么userIDList中去除resultListID中已经有的数据后得到userIDsNeedList，然后在数据库中查userIDsNeedList中的学号对应的数据得到resultList2，然后将resultList+resultList2返回

需要注意一点，还需要将resultList2异步同步缓存到redis，尽量下次能直接从redis拿，减少库的操作

```Java
@Override
    public Response<?> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        // 获取所有的userId
        List<Long> userIdList = findUsersByIdsReqDTO.getIds();
        // 通过ID获取所有的redisKey列表
        List<String> redisKeyList = userIdList.stream().map(aLong -> RedisConstant.getUserInfoKey(aLong)).toList();
        // 通过multiGet方法批量获取key所对应的value
        List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeyList);
        // 如果缓存中不为空
        if (CollUtil.isNotEmpty(redisValues)) {
            // 过滤掉为空的数据
            redisValues = redisValues.stream().filter(Objects::nonNull).toList();
        }
        // 返参列表
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = Lists.newArrayList();

        // 将过滤后的缓存集合，转换为 DTO 返参实体类
        if (CollUtil.isNotEmpty(redisValues)) {
            findUserByIdRspDTOS = redisValues.stream()
                    .map(value -> JsonUtil.JsonStringToObj(String.valueOf(value), FindUserByIdRspDTO.class))
                    .toList();
        }

        // 比对缓存中的数据个数和需要的数据个数  如果相同则说明redis都有数据 直接返回redis中的数据
        if(findUserByIdRspDTOS.size()== userIdList.size()){
            return Response.success(findUserByIdRspDTOS);
        }
        // 如果不等于 那么一定小于  小于的话两种情况 如果redis中查出来为null 则直接查库；如果不为null，则说明redis中只有部分数据，需要补充后再返回
        List<Long> userIdsNeedQuery = null;
        if(CollUtil.isNotEmpty(findUserByIdRspDTOS)){
            // 不为空 则说明redis缓存中有部分数据 补充即可
            // userID为key 本身FindUserByIdsRsp作为value 保存为map集合
            Map<Long, FindUserByIdRspDTO> collect = findUserByIdRspDTOS.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));
            // 筛选出Map中没有 但是入参userIdList中有的，也就是还需要从数据库中查询的数据
            userIdsNeedQuery = userIdList.stream().filter(aLong -> Objects.isNull(collect.get(aLong))).toList();
        }else{
            // redis中查出来为null  则说明只能从库中找
            userIdsNeedQuery = userIdList;
        }
        List<User> users = userMapper.selectBatchIds(userIdsNeedQuery);
        List<FindUserByIdRspDTO> needList = Lists.newArrayList();
        if(CollUtil.isNotEmpty(users)){
            needList = users.stream().map(user -> FindUserByIdRspDTO.builder().id(user.getId())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .description(user.getIntroduction()).build()).toList();
            // 走到这里 肯定说明redis中还少数据 异步将数据补充到redis中

        }
        if(CollUtil.isNotEmpty(needList)){
            findUserByIdRspDTOS.addAll(needList);
        }
        return Response.success(findUserByIdRspDTOS);
    }
```

因为我们是将List数据同步到redis，为了提高效率，我们不采用循环，而是**使用redis管道技术pipline实现批量添加，它能将一组 Redis 命令进行组装，通过一次传输给 Redis 并返回结果集**

（redis本身是基于Request/Response协议的  虽然提供了类似于mset  mget批量获取的命令，但是某些操作本身可能就不是批量操作）

![image-20241013225435302](../../ZealSingerBook/img/image-20241013225435302.png)

## redis-pipline技术的使用

redisTemplate使用pipline技术主要依靠**executePipelined方法，方法的参数是一个函数式接口，实现RedisCallback的doInRedis方法 这个方法只能返回null** 

**在这个方法中，进行的redis操作都不会直接执行，等到方法完毕才会一起执行**

```Java
threadPoolTaskExecutor.submit(()->{
                Map<Long, FindUserByIdRspDTO> redisMap = finalNeedList.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));
                redisTemplate.executePipelined(new RedisCallback<Void>() {
                    @Override
                    public Void doInRedis(RedisConnection connection) throws DataAccessException {
                        for (Long l : userIdList) {
                            FindUserByIdRspDTO value = redisMap.get(l);
                            String key = RedisConstant.getUserInfoKey(l);
                            String valueStr = JsonUtil.ObjToJsonString(value);
                            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                            redisTemplate.opsForValue().set(key,valueStr,expireSeconds,TimeUnit.SECONDS);
                        }
                        return null;
                    }
                });
            });
```

------

所以最终该接口的server代码如下

```Java
@Override
    public Response<?> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        // 获取所有的userId
        List<Long> userIdList = findUsersByIdsReqDTO.getIds();
        // 通过ID获取所有的redisKey列表
        List<String> redisKeyList = userIdList.stream().map(aLong -> RedisConstant.getUserInfoKey(aLong)).toList();
        // 通过multiGet方法批量获取key所对应的value
        List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeyList);
        // 如果缓存中不为空
        if (CollUtil.isNotEmpty(redisValues)) {
            // 过滤掉为空的数据
            redisValues = redisValues.stream().filter(Objects::nonNull).toList();
        }
        // 返参列表
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = Lists.newArrayList();

        // 将过滤后的缓存集合，转换为 DTO 返参实体类
        if (CollUtil.isNotEmpty(redisValues)) {
            findUserByIdRspDTOS = redisValues.stream()
                    .map(value -> JsonUtil.JsonStringToObj(String.valueOf(value), FindUserByIdRspDTO.class))
                    .toList();
        }

        // 比对缓存中的数据个数和需要的数据个数  如果相同则说明redis都有数据 直接返回redis中的数据
        if(findUserByIdRspDTOS.size()== userIdList.size()){
            return Response.success(findUserByIdRspDTOS);
        }
        // 如果不等于 那么一定小于  小于的话两种情况 如果redis中查出来为null 则直接查库；如果不为null，则说明redis中只有部分数据，需要补充后再返回
        List<Long> userIdsNeedQuery = null;
        if(CollUtil.isNotEmpty(findUserByIdRspDTOS)){
            // 不为空 则说明redis缓存中有部分数据 补充即可
            // userID为key 本身FindUserByIdsRsp作为value 保存为map集合
            Map<Long, FindUserByIdRspDTO> collect = findUserByIdRspDTOS.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));
            // 筛选出Map中没有 但是入参userIdList中有的，也就是还需要从数据库中查询的数据
            userIdsNeedQuery = userIdList.stream().filter(aLong -> Objects.isNull(collect.get(aLong))).toList();
        }else{
            // redis中查出来为null  则说明只能从库中找
            userIdsNeedQuery = userIdList;
        }
        List<User> users = userMapper.selectBatchIds(userIdsNeedQuery);
        List<FindUserByIdRspDTO> needList = Lists.newArrayList();
        if(CollUtil.isNotEmpty(users)){
            needList = users.stream().map(user -> FindUserByIdRspDTO.builder().id(user.getId())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .description(user.getIntroduction()).build()).toList();
            // 走到这里 肯定说明redis中还少数据 异步将数据补充到redis中
            List<FindUserByIdRspDTO> finalNeedList = needList;
            threadPoolTaskExecutor.submit(()->{
                Map<Long, FindUserByIdRspDTO> redisMap = finalNeedList.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));
                redisTemplate.executePipelined((RedisCallback<Void>) connection -> {
                    for (Long l : userIdList) {
                        FindUserByIdRspDTO value = redisMap.get(l);
                        String key = RedisConstant.getUserInfoKey(l);
                        String valueStr = JsonUtil.ObjToJsonString(value);
                        // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                        long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                        redisTemplate.opsForValue().set(key,valueStr,expireSeconds,TimeUnit.SECONDS);
                    }
                    return null;
                });
            });
        }
        if(CollUtil.isNotEmpty(needList)){
            findUserByIdRspDTOS.addAll(needList);
        }
        return Response.success(findUserByIdRspDTOS);
    }
```

# 关注列表接口编写

写完User模块中的方法，那么就能准备在User-relation模块中进行代码编写

我们之前提到 防止恶意 也为了防止返回过多，我们采取分页的方式返回能提高并发

入参

```json
{
    "userId": 27, // 想要查询的用户 ID
    "pageNo": 1 // 当前页码
}
```

出参

```json
{
	"success": true,
	"message": null,
	"errorCode": null,
	"data": [ // 分页数据
		{
			"userId": 100, // 用户ID
			"avatar": "http://127.0.0.1:9000/xiaohashu/14d8b7c3adad49f5b81dfa68417c0ab3.jpg", // 用户头像
			"nickname": "xxxx", // 昵称
			"introduction": "一枚 Java 程序员" // 简介
		}
	],
	"pageNo": 1, // 当前页码
	"totalCount": 0, // 总数据量
	"pageSize": 10, // 每页展示的数据量
	"totalPage": 0 // 总页数
}

```

我们这是分页返回，将当前页面和总数据量还是每页展示数量，总页数均返回，这里需要我们封装一个专门用于返回分页数据的工具类

**totalPage = (totalCount + pageSize - 1) / pageSize  这是一个数学技巧  记住就行 感觉很吊**

```Java
/**
 * @description: 分页响应参数工具类
 **/
@Data
public class PageResponse<T> extends Response<List<T>> {

    private long pageNo; // 当前页码
    private long totalCount; // 总数据量
    private long pageSize; // 每页展示的数据量
    private long totalPage; // 总页数

    public static <T> PageResponse<T> success(List<T> data, long pageNo, long totalCount) {
        PageResponse<T> pageResponse = new PageResponse<>();
        pageResponse.setSuccess(true);
        pageResponse.setData(data);
        pageResponse.setPageNo(pageNo);
        pageResponse.setTotalCount(totalCount);
        // 每页展示的数据量
        long pageSize = 10L;
        pageResponse.setPageSize(pageSize);
        // 计算总页数
        long totalPage = (totalCount + pageSize - 1) / pageSize;
        pageResponse.setTotalPage(totalPage);
        return pageResponse;
    }

    public static <T> PageResponse<T> success(List<T> data, long pageNo, long totalCount, long pageSize) {
        PageResponse<T> pageResponse = new PageResponse<>();
        pageResponse.setSuccess(true);
        pageResponse.setData(data);
        pageResponse.setPageNo(pageNo);
        pageResponse.setTotalCount(totalCount);
        pageResponse.setPageSize(pageSize);
        // 计算总页数
        long totalPage = pageSize == 0 ? 0 : (totalCount + pageSize - 1) / pageSize;
        pageResponse.setTotalPage(totalPage);
        return pageResponse;
    }

    /**
     * 获取总页数
     * @return
     */
    public static long getTotalPage(long totalCount, long pageSize) {
        return pageSize == 0 ? 0 : (totalCount + pageSize - 1) / pageSize;
    }

}
```

然后是正式的逻辑

从入参中拿去当前页pageNo 和 查询学号userID  然后首先到redis中查询是否有userId相关的关注列表信息  如果有直接从redis中拿取对应的数据即可

这里如果需要拿数据 那么需要从redis中进行分页操作 也就是拿取（pageNo-1）* pageSize 到  （pageNo-1）* pageSize+pageSize 之间的数据 这里涉及到了redis的分页范围查询操作

使用如下方法 **可进行redis的范围查找  reverseRangeByScore 范围且按照Score分数降序进行查找  综合而言就是查询 redisUserInfoKey的Set集合，查询分数在 Double.NEGATIVE_INFINITY,  ~~ Double.POSITIVE_INFINITY 之间的且在offset~offset+limit偏移量中间的数据，按照降序排序返回**

**使用Double.NEGATIVE_INFINITY(double数值的负无穷 ) 和 Double.POSITIVE(double 的正无穷) 作为范围**

```Java
 redisTemplate.opsForZSet().reverseRangeByScore(redisUserInfoKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
```

得到Redis中对应的数据后，进行空判，**如果为空，说明Reids中没有粉丝数据，那么就需要查表全量同步到Redis中，反之，如果不为空，直接返回Redis中查到的数据  如下是不为空的时候，直接返回Redis中的数据**

```Java
 Long pageNo = findFollowingListReqVO.getPageNo();
        Long userId = findFollowingListReqVO.getUserId();
        // 先从redis中查询
        String redisUserInfoKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        Long total = redisTemplate.opsForZSet().zCard(redisUserInfoKey);
        // 每页展示 10 条数据
        long limit = 10;
        List<FindFollowingListRspVO> resultList = null;
        if(total>0) {
            // 说明有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage) {
                return PageResponse.success(null, pageNo, total);
            }
            // 拿取对应的数据进行返回
            long offset = (pageNo - 1) * limit;
            // Set<Object> range = redisTemplate.opsForZSet().range(redisUserInfoKey, offset, offset + limit - 1);
            // 使用 ZREVRANGEBYSCORE 命令（该命令返回的set集合是key的每一个value数值 而不是value的分数  所以直接返回了followingUserId）按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 注意：这里使用了 Double.POSITIVE_INFINITY 和 Double.NEGATIVE_INFINITY 作为分数范围
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(redisUserInfoKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 如果不为空 拿去数据封装返回即可
                // 提取所有用户 ID 到集合中
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcServer.findUserByIds(userIds);
                if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
                    resultList = findUserByIdRspDTOS.stream().map(findUserByIdRspDTO -> FindFollowingListRspVO.builder().userId(findFollowingListReqVO.getUserId())
                            .nickname(findUserByIdRspDTO.getNickname())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .introduction(findUserByIdRspDTO.getDescription())
                            .build()).toList();
                }
            }
        }
```

然后是当Redis中不为空的时候的操作，根据userID查找其关注列表，然后封装为返回对象后返回，利用ThreadPoolTaskExecutor异步去同步redis，将查询的关注列表数据同步到Redis的操作，我们在关注接口的时候就已经写过了，利用之前的Lua脚本，直接使用即可

```Lua
Lua脚本follow_batch_add_and_expire.lua

-- 操作的 Key
local key = KEYS[1]

-- 准备批量添加数据的参数
local zaddArgs = {}

-- 遍历 ARGV 参数，将分数和值按顺序插入到 zaddArgs 变量中
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs, ARGV[i])      -- 分数（关注时间）
    table.insert(zaddArgs, ARGV[i+1])    -- 值（关注的用户ID）
end

-- 调用 ZADD 批量插入数据
redis.call('ZADD', key, unpack(zaddArgs))

-- 设置 ZSet 的过期时间
local expireTime = ARGV[#ARGV] -- 最后一个参数为过期时间
redis.call('EXPIRE', key, expireTime)

return 0
```

redis中没有数据时候的逻辑为下

```Java
else{
                // todo 缓存中没有数据  查库 并且 异步保存到redis中
                if(pageNo<1){
                    pageNo = 1L;
                }
                LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
                followingLambdaQueryWrapper.eq(Following::getUserId, userId);
                followingLambdaQueryWrapper.orderByDesc(Following::getCreateTime);
                followingLambdaQueryWrapper.last("limit"+" "+(pageNo-1)*limit+","+limit);
                List<Following> followingList = followingMapper.selectList(followingLambdaQueryWrapper);
                if(CollUtil.isNotEmpty(followingList)){
                    // 收集ID的List
                    List<Long> followingIdList = followingList.stream().map(Following::getFollowingUserId).distinct().toList();
                    // 调用User模块的RPC 查询用户信息
                    List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(followingIdList);
                    if (CollUtil.isNotEmpty(userByIds)) {
                        resultList= userByIds.stream().map(findUserByIdRspDTO -> FindFollowingListRspVO.builder().userId(findFollowingListReqVO.getUserId())
                                .nickname(findUserByIdRspDTO.getNickname())
                                .avatar(findUserByIdRspDTO.getAvatar())
                                .introduction(findUserByIdRspDTO.getDescription())
                                .build()).toList();
                        // TODO 异步同步到redis
                        taskExecutor.submit(()-> syncFollowingList2Redis(userId));
                    }
                }
            }



/**
     * 全量同步关注列表至 Redis 中  直接套用之前的Lua脚本即可
     */
    private void syncFollowingList2Redis(Long userId) {
        LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
        followingLambdaQueryWrapper.eq(Following::getUserId, userId);
        List<Following> followingList = followingMapper.selectList(followingLambdaQueryWrapper);
        if(CollUtil.isNotEmpty(followingList)){
            String redisKey = RedisConstant.getFollowingKey(String.valueOf(userId));
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            // 和之前的逻辑一样 利用方法构建redisLua脚本所需要的参数格式
            Object[] luaArgs = buildLuaArgs(followingList, expireSeconds);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(redisKey), luaArgs);
        }
    }
```

所以最终 整体的关注列表的Service如下

```java 
@Override
    public Response<?> list(FindFollowingListReqVO findFollowingListReqVO) {
        Long pageNo = findFollowingListReqVO.getPageNo();
        Long userId = findFollowingListReqVO.getUserId();
        // 先从redis中查询
        String redisUserInfoKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        Long total = redisTemplate.opsForZSet().zCard(redisUserInfoKey);
        // 每页展示 10 条数据
        long limit = 10;
        List<FindFollowingListRspVO> resultList = null;
        if(total>0) {
            // 说明有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage) {
                return PageResponse.success(null, pageNo, total);
            }
            // 拿取对应的数据进行返回
            long offset = (pageNo - 1) * limit;
            // Set<Object> range = redisTemplate.opsForZSet().range(redisUserInfoKey, offset, offset + limit - 1);
            // 使用 ZREVRANGEBYSCORE 命令（该命令返回的set集合是key的每一个value数值 而不是value的分数  所以直接返回了followingUserId）按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 注意：这里使用了 Double.POSITIVE_INFINITY 和 Double.NEGATIVE_INFINITY 作为分数范围
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(redisUserInfoKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 如果不为空 拿去数据封装返回即可
                // 提取所有用户 ID 到集合中
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcServer.findUserByIds(userIds);
                if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
                    resultList = findUserByIdRspDTOS.stream().map(findUserByIdRspDTO -> FindFollowingListRspVO.builder().userId(findFollowingListReqVO.getUserId())
                            .nickname(findUserByIdRspDTO.getNickname())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .introduction(findUserByIdRspDTO.getDescription())
                            .build()).toList();
                }
            }
        }else{
                // todo 缓存中没有数据  查库 并且 异步保存到redis中
                if(pageNo<1){
                    pageNo = 1L;
                }
                LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
                followingLambdaQueryWrapper.eq(Following::getUserId, userId);
                followingLambdaQueryWrapper.orderByDesc(Following::getCreateTime);
                followingLambdaQueryWrapper.last("limit"+" "+(pageNo-1)*limit+","+limit);
                List<Following> followingList = followingMapper.selectList(followingLambdaQueryWrapper);
                if(CollUtil.isNotEmpty(followingList)){
                    // 收集ID的List
                    List<Long> followingIdList = followingList.stream().map(Following::getFollowingUserId).distinct().toList();
                    // 调用User模块的RPC 查询用户信息
                    List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(followingIdList);
                    if (CollUtil.isNotEmpty(userByIds)) {
                        resultList= userByIds.stream().map(findUserByIdRspDTO -> FindFollowingListRspVO.builder().userId(findFollowingListReqVO.getUserId())
                                .nickname(findUserByIdRspDTO.getNickname())
                                .avatar(findUserByIdRspDTO.getAvatar())
                                .introduction(findUserByIdRspDTO.getDescription())
                                .build()).toList();
                        // TODO 异步同步到redis
                        taskExecutor.submit(()-> syncFollowingList2Redis(userId));
                    }
                }
            }
        return PageResponse.success(resultList, pageNo, total);
    }
```

# 粉丝列表接口编写

首先是需求分析  粉丝列表接口中需要返回的数据有**头像 昵称 粉丝数  笔记数**

![image-20241015185719375](../../ZealSingerBook/img/image-20241015185719375.png)

入参

```json
{
	"userId":"",  //用户ID
	"pageNo":""   //当前页
}
```

出参

```json
{
	"success": true,
	"message": null,
	"errorCode": null,
	"data": [
		{
			"userId": 101, // 用户ID
			"avatar": "http://127.0.0.1:9000/xiaohashu/f22e21fb0c144c088bd20bc616916ff3.jpg", // 头像
			"nickname": "犬小哈", // 昵称
			"fansTotal": 0, // 粉丝总量
			"noteTotal": 0 // 笔记总量
		},
		{
			"userId": 28,
			"avatar": null,
			"nickname": "小红薯10100",
			"fansTotal": 0,
			"noteTotal": 0
		}
	],
	"pageNo": 1,
	"totalCount": 3,
	"pageSize": 10,
	"totalPage": 1
}

```

首先创建好对应的出参入参实体类

![image-20241015191430443](../../ZealSingerBook/img/image-20241015191430443.png)

![image-20241015191421749](../../ZealSingerBook/img/image-20241015191421749.png)

然后是主要逻辑，顺序还是那一套：先查缓存，缓存有则返回，没有则查库后返回

```Java
/**
     * 返回粉丝列表
     * @param findFansListReqVO
     * @return
     */
    @Override
    public Response<?> findFansListReqVO(FindFansListReqVO findFansListReqVO) {
        Long userId = findFansListReqVO.getUserId();
        Long pageNo = findFansListReqVO.getPageNo();
        if(pageNo < 1){
            pageNo =1L ;
        }
        String fansKey = RedisConstant.getFansKey(String.valueOf(userId));
        Long total = redisTemplate.opsForZSet().zCard(fansKey);
        long limit = 10;
        List<FindFansListRspVO> resultList = null;
        if(total > 0){
            // 说明缓存中有数据
            if(pageNo > total) {
                return PageResponse.success(null, pageNo, total);
            }
            long offset = (pageNo - 1) * limit;
            Set<Object> redisObjectList = redisTemplate.opsForZSet().reverseRangeByScore(fansKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if(CollUtil.isNotEmpty(redisObjectList)) {
                List<Long> fansUserIdList = redisObjectList.stream().map(object -> Long.valueOf(object.toString())).toList();
                List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(fansUserIdList);
                if (CollUtil.isNotEmpty(userByIds)) {
                    resultList = userByIds.stream().map(findUserByIdRspDTO -> FindFansListRspVO.builder().userId(findUserByIdRspDTO.getId())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            // TODO 计数模块再补充这两个数据
                            .fansCount(null)
                            .noteCount(null)
                            .build()).toList();
                }
            }
        }else{
            LambdaQueryWrapper<Fans> fansLambdaQueryWrapper = new LambdaQueryWrapper<>();
            // 查询数据库返回 异步同步到redis中
            Long fansTotal = fansMapper.selectCount(fansLambdaQueryWrapper.eq(Fans::getUserId, userId));
            // 粉丝数量是无上限的  防止返回过多和恶意攻击  之前有做限定 不能大于500页
            if(pageNo > fansTotal || pageNo > 500) {
                return PageResponse.success(null, pageNo, total);
            }
            // 返回前500的数据 到这里能确定pageNo小于500
            fansLambdaQueryWrapper.orderByDesc(Fans::getCreateTime);
            fansLambdaQueryWrapper.last("limit"+" "+(pageNo-1)*limit+","+limit);
            List<Fans> fansList = fansMapper.selectList(fansLambdaQueryWrapper);
            if(CollUtil.isNotEmpty(fansList)){
                List<Long> fansIdList = fansList.stream().map(Fans::getFansUserId).distinct().toList();
                List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(fansIdList);
                if (CollUtil.isNotEmpty(userByIds)) {
                    resultList = userByIds.stream().map(findUserByIdRspDTO -> FindFansListRspVO.builder().userId(findUserByIdRspDTO.getId())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            // TODO 计数模块再补充这两个数据
                            .fansCount(null)
                            .noteCount(null)
                            .build()).toList();
                    // 异步将粉丝数据同步到redis中
                    taskExecutor.submit(()-> syncFansList2Redis(userId));
                }
            }
        }
        return PageResponse.success(resultList, pageNo, total);
    }

    private void syncFansList2Redis(Long userId) {
        LambdaQueryWrapper<Fans> fansLambdaQueryWrapper = new LambdaQueryWrapper<>();
        fansLambdaQueryWrapper.eq(Fans::getUserId, userId);
        fansLambdaQueryWrapper.orderByDesc(Fans::getCreateTime);
        fansLambdaQueryWrapper.last("limit"+" "+0+" , "+ 5000);
        List<Fans> fansList = fansMapper.selectList(fansLambdaQueryWrapper);
        if(CollUtil.isNotEmpty(fansList)){
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            Object[] luaArgs = buildFansLuaArgs(fansList, expireSeconds);
            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(RedisConstant.getFansKey(String.valueOf(userId))), luaArgs);
        }
    }

    private Object[] buildFansLuaArgs(List<Fans> list,long expireSeconds){
        int len = list.size()*2+1;
        Object[] args = new Object[len];
        int i =0 ;
        for (Fans fans : list) {
            args[i] = DateUtils.localDateTime2Timestamp(fans.getCreateTime());
            args[i+1] = fans.getFansUserId();
            i+=2;
        }
        args[len-1] = expireSeconds;
        return args;
    }
```

