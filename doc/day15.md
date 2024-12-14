# 数据对齐服务模块搭建

## 模块职责

在目前业务中 ，我们使用了很多的MQ异步消费，但是中间件的可靠性其实我们没有特地去做保证，在小概率情况下，消息中间件可以会重复消费或者遗漏消费

![image-20241123201645375](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241123201645375.png)

而在我们目前搭建的服务中，并没有对这一方面做一定的防范措施，会导致计数数据出现误差

## 是否允许有误差

在大部分业务场景中，自然是不允许有误差的，对于MQ需要进行幂等判断，保证数据的一致性，但是**计数服务不是一个强一致性的场景，也就是说允许存在中间状态，但是需要保证最终一致性**，如何做到最终一致性呢？这就是我们目前提到的数据对齐服务的作用了，每隔一段时间对redis和数据库中的数据进行对齐，更新计数表和redis缓存

## 需要对齐的数据

需要对齐的数据其实也就是计数表中需要存在的数据

![image-20241123201934560](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241123201934560.png)

再者，**从用户的角度来看，笔记的点赞数，笔记的收藏数，用户的关注数，粉丝数等这些数据关注度其实也是层次不齐的**

**点赞数 和 收藏数** ：当笔记刚发布或者是普通用户发的文章，本身点赞数和收藏数不会有很多，他们普遍容易注意和在意这个数据准确性 ； 相反，对于大V用户和热门帖子反倒不需要如此精准，少那么几个也不会有人真正的去核对数据

**用户维度的粉丝数，获得点赞数，获得收藏数也是一样的道理 ，但是用户的关注数和发布笔记数因为前者有总数限制，后者一般不会太多，所以这两个数据是属于必须准确的数据**

所以 我们可以根据上述的分析，在数据对齐模块中进行一定的放松，减少计算消耗

## 日增量对齐

例如现在小红书平台，超过2亿的用户量，数据量是非常大的，如果采用每次都查库查表的方式进行对齐，哪怕只针对上述需要对齐的数据中的某一个，例如对齐粉丝数或者点赞数，也是一个非常大的数据量，所以我们不可以采取一一查库全部对齐的策略

这里就需要用到了**日增量对齐的方案：以一天为单位，每日对齐一个当天的数据变动，而不是对整个平台的数据对齐**

那么如果是使用日增量对齐方案，**那么需要每天记录每天的数据变动情况，记录数据变动我们采取数据库记录，但是我们每天的数据只用于对齐，而且对齐完毕之后第二天以及之后都不会使用，也就是说数据其实使用期限也就一天，而且每天的数据只用于对齐当天的，所以没必要长久包留，对于此，我们可以每天创建临时表的方式记录每天的对齐数据，然后每天进行删除**

自然 这里就需要用到定时任务 定时创建每天的表并且定时销毁  因为需要高可用 未来可能是分布式架构 分布式架构的定时任务我们之前其实已经接触过了  也就是使用**XXL-JOB**

每天定时创建需要对齐服务的表并且是多个分片  能将每天不同的所需要的数据按照一定的分片规则分别记录到不同的分片表中，分散负载的作用，提高并行处理能力，同时在对齐的时候也能多个任务和节点一起进行对齐

![image-20241128141013545](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128141013545.png)

# XXL-JOB入门

参考实习的时候在积加做的笔记

# 模块搭建

## 基础搭建

data-align数据对齐  因为是后台对齐的 不可能被其他服务调用 所以单独一个模块 而且只需要biz模块而不需要api模块

![image-20241128141331139](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128141331139.png)

这里需要用到XXL-JOB 所以对应相关的新的配置信息如下  同样  会需要操作数据库 缓存之类的  所以需要引入数据库依赖和redis依赖，nacos依赖等

![image-20241128141449379](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128141449379.png)

## 创建临时表的定时任务

因为是要创建表 MP中自然没有对应的封装 所以我们通过mapper.xml自己写

因为需要对齐的服务有  关注数；粉丝数；笔记点赞数；笔记收藏数；用户被点赞总数；用户被收藏数总数；笔记发布数 ；每一个对齐服务需要分片临时表，假设我们这里设置三个分片（每个表名采用  表名+分片序号1/2/3 的形式），那么也就是说每一个对齐数据需要三个临时表

```Java
@Mapper
public interface CreateTableMapper {
    /**
     * 创建日增量表：关注数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignFollowingCountTempTable(String tableNameSuffix);

    /**
     * 创建日增量表：粉丝数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignFansCountTempTable(String tableNameSuffix);

    /**
     * 创建日增量表：笔记收藏数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignNoteCollectCountTempTable(String tableNameSuffix);

    /**
     * 创建日增量表：用户被收藏数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignUserCollectCountTempTable(String tableNameSuffix);

    /**
     * 创建日增量表：用户被点赞数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignUserLikeCountTempTable(String tableNameSuffix);

    /**
     * 创建日增量表：笔记点赞数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignNoteLikeCountTempTable(String tableNameSuffix);

    /**
     * 创建日增量表：笔记发布数计数变更
     * @param tableNameSuffix
     */
    void createDataAlignNotePublishCountTempTable(String tableNameSuffix);
}
```

上面每个Mapper接口中的方法都对应一个创建表的SQL，在XML文件中编写

```xml
<insert id="createDataAlignFollowingCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_following_count_temp_${tableNameSuffix}` (
               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
               `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
               PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_user_id` (`user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：关注数';
    </insert>

    <insert id="createDataAlignFansCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_fans_count_temp_${tableNameSuffix}` (
               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
               `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
               PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_user_id` (`user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：粉丝数';
    </insert>

    <insert id="createDataAlignNoteCollectCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_note_collect_count_temp_${tableNameSuffix}` (
               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
               `note_id` bigint unsigned NOT NULL COMMENT '笔记ID',
               PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_note_id` (`note_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：笔记获得收藏数';
    </insert>

    <insert id="createDataAlignUserCollectCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_user_collect_count_temp_${tableNameSuffix}` (
                `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_user_id` (`user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：用户获得收藏数';
    </insert>

    <insert id="createDataAlignUserLikeCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_user_like_count_temp_${tableNameSuffix}` (
               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
               `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
               PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_user_id` (`user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：用户获得点赞数';
    </insert>

    <insert id="createDataAlignNoteLikeCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_note_like_count_temp_${tableNameSuffix}` (
               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                `note_id` bigint unsigned NOT NULL COMMENT '笔记ID',
                PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_note_id` (`note_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：笔记获得点赞数';
    </insert>

    <insert id="createDataAlignNotePublishCountTempTable" parameterType="map">
        CREATE TABLE IF NOT EXISTS `t_data_align_note_publish_count_temp_${tableNameSuffix}` (
               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
               `note_id` bigint unsigned NOT NULL COMMENT '笔记ID',
               PRIMARY KEY (`id`) USING BTREE,
            UNIQUE KEY `uk_note_id` (`note_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据对齐日增量表：用户发布笔记数';
    </insert>
```

方法写好了，就可以在XXL-JOB中定义执行器和任务，让XXL-JOB帮我们定时处理

如下是定时任务的逻辑（shards 是分片数量 我们将其配置在Nacos中从而能动态配置和刷新）



```Java
@Component
@RefreshScope
public class CreateTableXxlJob {
    @Value("${table.shards}")
    private int shards;

    @Resource
    private CreateTableMapper createTableMapper;

    @XxlJob("createTableJobHandler")
    public void createTableJobHandler(){
        // 表后缀
        String date = LocalDate.now().plusDays(1) // 明日的日期
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串
        XxlJobHelper.log("## 开始初始化明日增量数据表");
        // 表名后缀
        if(shards>0){
            for (int hashKey = 0; hashKey < shards; hashKey++) {
                // 表名后缀
                String tableNameSuffix = TableConstants.buildTableNameSuffix(date, hashKey);
                // 创建表
                createTableMapper.createDataAlignFollowingCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignFansCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNotePublishCountTempTable(tableNameSuffix);
            }
        }
        XxlJobHelper.log("## 结束创建日增量数据表，日期: {}...", date);
    }
}
```

**可以看到我们的执行器是每天的23点执行 用于创建第二天需要的临时表**

![image-20241128142242594](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128142242594.png)

那么每天的表格在前一天就能准备好，准备好之后，就是对每天的数据进行对齐了

## 笔记点赞，取消点赞对齐数据

所谓对其数据，那么我们也就是对齐临时表中的数据和数据库中的数据，所以对于需要的数据，我们都需要记录在临时表中

首先是笔记点赞和取消点赞

我们在笔记点赞和取消点赞的逻辑中  主逻辑中只会更新布隆和缓存 然后MQ消息异步落库

![image-20241128143009988](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128143009988.png)

对应的这条落库MQ消息的消费者如下

这边**MQ消费的逻辑是:同步落库，然后发送MQ消息给技术模块，进行异步计数服务**

![image-20241128143217903](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128143217903.png)

自然，**我们所需要对齐的服务是已经落库成功的数据，所以我们也可以为这条计数MQ消息再添加一个消费者，也就是我们的对齐服务，将这表消息对齐保存到临时表 **

可以看到**这条MQ的Topic是TOPIC_COUNT_NOTE_LIKE  对应的是封装的JSON序列化后的LikeUnlikeMqDto对象变为的bodyJsonStr字符串**

![image-20241128143450590](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128143450590.png)

![image-20241128143628274](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128143628274.png)

同样，我们的对齐服务也需要知道和消费到这条消息，所以对齐服务中也可以定义一个消费者，使用同样的Topic，也需要消费到这个类型的消息

需要注意一点，这就代表着，我们的计数服务和对齐服务都需要消费到这个消息，是否会存在竞争消息的情况呢？这个需要预防一下，在RocketMQ中同一个Group中才可能出现消息竞争的情况（广播模式和集群模式，集群模式中每个消息只会被消费一次，广播模式每个消费者都会消费到该消息）），**所以我们需要保证对齐服务的group和count计数模块的group不一样即可**

![image-20241128144300386](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128144300386.png)

那么消费者这边的大概逻辑就是：接收到MQ消息，保存到对齐服务中

**MySQL建表的时候虽然虽然已经对相关逻辑的重要字段做了唯一索引，但是为了减轻数据库的压力，我们可以在加一层布隆过滤器，在消费消息的时候检查一下该数据是否已经被对齐服务记录，如果布隆过滤器中没有该数据（不会产生误判，绝对正确），那么可以将其记录到日增量表中，然后再将变更的数据写入更新布隆过滤器，保证下次被点赞不会再次操作数据库**

```Java
@Component
@RocketMQMessageListener(
        consumerGroup = "zealsinger_data_align_"+ MQConstant.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstant.TOPIC_COUNT_NOTE_LIKE
)
@Slf4j
public class TodayNoteLikeIncrementData2DBConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String message) {
        log.info("## TodayNoteLikeIncrementData2DBConsumer 消费到了 MQ: {}", message);
        // TODO 布隆过滤器判断该日增量数据是否已经记录

        // TODO 如果没有 则落库 否则不落库  减少库的压力(虽然数据库加了唯一索引 不会重复 但是用一层布隆降低数据库的压力)
        // 布隆过滤器中不存在的时候是绝对不存在  不会出现误判  直接可以落库

        // TODO 对应的日增量变更数据
        // - t_data_align_note_like_count_temp_日期_分片序号  noteId%3
        // - t_data_align_user_like_count_temp_日期_分片序号  userId%3

        // TODO 数据库写入成功后，再添加布隆过滤器中
    }
}
```

![image-20241128143751517](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241128143751517.png)

### Lua脚本编写

要操作布隆过滤器 自然需要lua脚本 lua脚本的逻辑：**查看布隆过滤器是否存在，不存在则创建，设置过期时间（一天 这个布隆过滤器是每天的 所以设置过期而且不会有历史数据之类的 所以可以直接在lua脚本逻辑中创建，对于之前的笔记点赞和收藏接口等，会存在历史记录，所以不能直接在lua中初始化，需要单独的方法逻辑进行初始化工作），然后校验该数据变更是否已经存在，存在返回1，不存在返回0**

布隆过过滤波器的Key因为是每天记录的，所以采用 **前缀（bloom:dataAlign:note:likes）+当前时间**的格式，其value，对于笔记点赞和取消点赞，会涉及到笔记的点赞数的数据和创作者被点赞数两个维度的数据，所以这里我们会存放两个数据的布隆过滤器，一个存放创作者ID 一个存放笔记ID

```Java
/**
     * 布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞（笔记ID） 前缀
     */
    public static final String BLOOM_TODAY_NOTE_LIKE_NOTE_ID_LIST_KEY = "bloom:dataAlign:note:like:noteIds";

/**
     * 布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞（笔记发布者ID） 前缀
     */
    public static final String BLOOM_TODAY_NOTE_LIKE_USER_ID_LIST_KEY = "bloom:dataAlign:note:like:userIds";


    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞(笔记ID) KEY
     * @param date
     * @return
     */
    public static String buildBloomUserNoteLikeNoteIdListKey(String date) {
        return BLOOM_TODAY_NOTE_LIKE_NOTE_ID_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞(笔记发布者ID) KEY
     * @param date
     * @return
     */
    public static String buildBloomUserNoteLikeUserIdListKey(String date) {
        return BLOOM_TODAY_NOTE_LIKE_USER_ID_LIST_KEY + date;
    }
```

```lua
-- LUA 脚本：自增量笔记点赞、取消点赞变更数据布隆过滤器

local key = KEYS[1] -- 操作的 Redis Key
local noteIdAndNoteCreatorId = ARGV[1] -- Redis Value

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    -- 创建布隆过滤器
    redis.call('BF.ADD', key, '')
    -- 设置过期时间，一天后过期
    redis.call("EXPIRE", key, 20*60*60)
end

-- 校验该变更数据是否已经存在(1 表示已存在，0 表示不存在)
return redis.call('BF.EXISTS', key, noteIdAndNoteCreatorId)

```

### 整体逻辑编写

Lua脚本已经写完，那么可以调用Lua进行检测

```Java
LikeUnlikeMqDTO likeUnlikeMqDTO = JsonUtil.JsonStringToObj(message, LikeUnlikeMqDTO.class);
        if(likeUnlikeMqDTO==null){
            return;
        }
        Long noteId = likeUnlikeMqDTO.getNoteId();
        Long creatorId = likeUnlikeMqDTO.getCreatorId();
        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串

        String bloomKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(date);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_note_like_check.lua")));
        Long executeResult = redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId);
```

然后对executeResult进行检测 如果是0标识不存在，那么就需要进行入库操作，同样的，因为涉及到了用户的被点赞数和笔记获得点赞数两个维度，所以这里需要分别执行两个布隆过滤器的检测

```Java
Long executeResult = redisTemplate.execute(script, Collections.singletonList(noteBloomKey), noteId);
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
        // TODO 如果没有 则落库 否则不落库  减少库的压力(虽然数据库加了唯一索引 不会重复 但是用一层布隆降低数据库的压力)
        // 布隆过滤器中不存在的时候是绝对不存在  不会出现误判  直接可以落库
        if(Objects.equal(executeResult,0L)){
            // TODO 对应的日增量变更数据
            // - t_data_align_note_like_count_temp_日期_分片序号  noteId%3
            long noteShards = noteId%shards;
            try{
                insertRecordMapper.insert2DataAlignNoteLikeCountTempTable(TableConstants.buildTableNameSuffix(date,noteShards),noteId);
            }catch (Exception e){
                log.error("", e);
            }
            // TODO 数据库写入成功后，再添加布隆过滤器中
            // 4. 数据库写入成功后，再添加布隆过滤器中

            redisTemplate.execute(bloomAddScript, Collections.singletonList(noteBloomKey), noteId);
        }


        // 添加完笔记noteId的布隆和数据库  添加creator的布隆和数据库
        String userBloomKey = RedisKeyConstants.buildBloomUserNoteLikeUserIdListKey(date);
        executeResult = redisTemplate.execute(script, Collections.singletonList(userBloomKey), creatorId);
        if(Objects.equal(executeResult,0L)){
            // - t_data_align_user_like_count_temp_日期_分片序号  userId%3
            long creatorShards = creatorId%shards;
            try{
                insertRecordMapper.insert2DataAlignUserLikeCountTempTable(TableConstants.buildTableNameSuffix(date,creatorShards),creatorId);
            }catch (Exception e){
                log.error("", e);
            }
            // creator落库
            redisTemplate.execute(bloomAddScript, Collections.singletonList(userBloomKey), creatorId);
        }
```

对应的mapper的方法的xml的SQL逻辑  也就是两条插入语句

```xml
<insert id="insert2DataAlignNoteLikeCountTempTable" parameterType="map">
        insert into `t_data_align_note_like_count_temp_${tableNameSuffix}` (note_id) values (#{noteId})
    </insert>

    <insert id="insert2DataAlignUserLikeCountTempTable" parameterType="map">
        insert into `t_data_align_user_like_count_temp_${tableNameSuffix}` (user_id) values (#{userId})
    </insert>
```

### 注意

这里可以看到，其实这里会有点数据遗漏问题存在的，每次查布隆判断是否已经被对齐，我们这里是增量记录，当布隆过滤器中不存在的时候，不存在误判，必定存在，这个没问题

**但是当布隆过滤器中存在的时候，其实会存在误判，那么也就是说，可能这个数据其实并没有被记录到数据库中，那么在后续的对齐服务job逻辑中自然也不会去检测，这部分数据既然第一次被误判检测存在，那么必定一直是被误判检测存在的，那么也也就代表这个数据在今天不可能进入到布隆中，这部分数据也就相当于是被忽略了**

**那我们加入对误判情况的判定？如果加入，那么就是如果布隆中存在，就还要再次查一次库，那么这个操作，当一篇热点文章发布，一直被点赞，除了第一次之后都会需要再次被查库检测（第一次不存在，会被加入到布隆，第二次就会存在，那么需要查库进行二次判断）），相当于失去了缓存的意义，对数据库是十分不利的，所以权衡利弊，选择不进行二次检测**

### job逻辑编写

到目前为止，我们临时表相关的任务就完成了，现在完成的逻辑就是：每次有文字被点赞，取消点赞，他的数据就是变化了的数据，会被录到我们的临时表中

**对齐服务的接下来的任务就是，针对这些被操作的数据进行数据对齐就行了，因为这些是发送了变化的数据，所以需要被对其进行数据对齐，相比较与我们直接的每天都对齐，少了很多的数据量，只会对有变化的数据进行对齐**

这里我们采用xxl-job的分片广播的形式  因为我们是对应的有三个临时表，如果采用分布式和微服务，我们能配置多个数据对齐服务，每个服务对应的处理一个表即可

![image-20241129193439350](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241129193439350.png)

```Java
/**
 * @description: 定时分片广播任务：对当日发生变更的笔记点赞数进行对齐
 **/
@Component
@Slf4j
public class NoteLikeCountShardingXxlJob {

    @Resource
    private SelectMapper selectMapper;
    @Resource
    private UpdateMapper updateMapper;
    @Resource
    private DeleteMapper deleteMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分片广播任务
     */
    @XxlJob("noteLikeCountShardingJobHandler")
    public void noteLikeCountShardingJobHandler() throws Exception {
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的笔记点赞数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 表后缀
        String date = LocalDate.now().minusDays(1) // 昨日的日期
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串
        // 表名后缀
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);

        // 一批次 1000 条
        int batchSize = 1000;
        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;

        // 死循环
        for (;;) {
            // 1. 分批次查询 t_data_align_note_like_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
            List<Long> noteIds = selectMapper.selectBatchFromDataAlignNoteLikeCountTempTable(tableNameSuffix, batchSize);

            // 若记录为空，终止循环
            if (CollUtil.isEmpty(noteIds)) break;

            // 循环这一批发生变更的笔记 ID
            noteIds.forEach(noteId -> {
                // 2: 对 t_note_like 关注表执行 count(*) 操作，获取关注总数
                int likeTotal = selectMapper.selectCountFromNoteLikeTableByUserId(noteId);

                // 3: 更新 t_note_count 表, 更新对应 Redis 缓存
                int count = updateMapper.updateNoteLikeTotalByUserId(noteId, likeTotal);
                // 更新对应 Redis 缓存
                if (count > 0) {
                    String redisKey = RedisKeyConstants.buildCountNoteKey(noteId);
                    // 判断 Hash 是否存在
                    boolean hashKey = redisTemplate.hasKey(redisKey);
                    // 若存在
                    if (hashKey) {
                        // 更新 Hash 中的 Field 点赞总数
                        redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, likeTotal);
                    }
                }
            });

            // 4. 批量物理删除这一批次记录  通过id删除
            deleteMapper.batchDeleteDataAlignNoteLikeCountTempTable(tableNameSuffix, noteIds);

            // 当前已处理的记录数
            processedTotal += noteIds.size();
        }

        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的笔记点赞数进行对齐，共对齐记录数：{}", processedTotal);
    }

}

```

```xml


    <select id="selectBatchFromDataAlignNoteLikeCountTempTable" resultType="long" parameterType="map">
           select note_id from `t_data_align_note_like_count_temp_${tableNameSuffix}` order by id
            limit #{batchSize}
    </select>

    <select id="selectCountFromNoteLikeTableByUserId" parameterType="map" resultType="int">
        select count(*) from t_note_like where note_id = #{noteId} and status = 1
    </select>


	<delete id="batchDeleteDataAlignNoteLikeCountTempTable" parameterType="list">
        delete from `t_data_align_note_like_count_temp_${tableNameSuffix}`
        where note_id in
        <foreach collection="noteIds" open="(" item="noteId" close=")" separator=",">
            #{noteId}
        </foreach>
    </delete>

```



通过笔记点赞和取消点赞的逻辑，可以大致了解XXL-JOB数据对齐模块的逻辑，其他的几个笔记收藏，笔记取消收藏，发布和删除都是一样的逻辑，其实就是对增量数据

# 补充--定时删除临时表

自然 临时表不删除就会一直堆积，所以我们需要定时删除的job

```Java
/**
 * @description: 定时任务：自动删除最近一个月的日增量临时表
 **/
@Component
@RefreshScope
public class DeleteTableXxlJob {

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    @Resource
    private DeleteTableMapper deleteTableMapper;


    @XxlJob("deleteTableJobHandler")
    public void deleteTableJobHandler() throws Exception {
        XxlJobHelper.log("## 开始删除最近一个月的日增量临时表");
        // 今日
        LocalDate today = LocalDate.now();

        // 日期格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        LocalDate startDate = today;
        // 从昨天开始往前推一个月
        LocalDate endDate = today.minusMonths(1);

        // 循环最近一个月的日期，不包括今天
        while (startDate.isAfter(endDate)) {
            // 往前推一天
            startDate = startDate.minusDays(1);
            // 日期字符串
            String date = startDate.format(formatter);

            for (int hashKey = 0; hashKey < tableShards; hashKey++) {
                // 表名后缀
                String tableNameSuffix = TableConstants.buildTableNameSuffix(date, hashKey);
                XxlJobHelper.log("删除表后缀: {}", tableNameSuffix);

                // 删除表
                deleteTableMapper.deleteDataAlignFollowingCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignFansCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignNoteCollectCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignUserCollectCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignUserLikeCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignNoteLikeCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignNotePublishCountTempTable(tableNameSuffix);
            }
        }
        XxlJobHelper.log("## 结束删除最近一个月的日增量临时表");
    }
}

```
