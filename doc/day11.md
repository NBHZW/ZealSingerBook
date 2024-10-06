# 笔记服务模块

## 笔记业务分析和表设计

### 业务分析

笔记查看页面

包括笔记的内容  图片 信息  标题

![image-20241004151107194](../../ZealSingerBook/img/image-20241004151107194.png)

发布笔记

上传文本和图片，视频，添加标题，话题分类，地点

![image-20241004151147789](../../ZealSingerBook/img/image-20241004151147789.png)

笔记相关操作

1：发布之后可以二次修改内容

2：设置仅自己可见

3：置顶笔记 能设置笔记展示的优先级

4：笔记删除

![image-20241004151310829](../../ZealSingerBook/img/image-20241004151310829.png)

频道与话题

根据内容大分类为频道 频道下再次分类为话题  也就是说 频道和话题是父子关系

![image-20241004151432228](../../ZealSingerBook/img/image-20241004151432228.png)

### 表设计

根据上面的分析，我们需要定制四张表，笔记表+话题表+频道表+频道-话题关系表

笔记表

| 字段        | 属性     | 含义                                                       |
| ----------- | -------- | ---------------------------------------------------------- |
| id          | bigint   | 主键id                                                     |
| title       | String   | 笔记标标题                                                 |
| creator_id  | bigint   | 发布者ID                                                   |
| topic_id    | bitint   | 话题ID                                                     |
| topic_name  | varchar  | 话题名字                                                   |
| type        | tinyint  | 类型（0：图文 1：视频）                                    |
| is_top      | bit      | 是否置顶（0未置顶  1 置顶）                                |
| img_urls    | varchar  | 图片URL                                                    |
| video_uri   | varchar  | 视频url                                                    |
| statis      | tinyint  | 状态（0等待审核；1正常展示；2被删除（逻辑删除）；3被下架） |
| visible     | tinyint  | 可见范围（0所有；1仅自己）                                 |
| create_time | datetime | 创建时间                                                   |
| update_time | datetime | 更新时间                                                   |

```
CREATE TABLE `t_note` (
  `id` bigint(11) unsigned NOT NULL COMMENT '主键ID',
  `title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：空)',
  `creator_id` bigint(11) unsigned NOT NULL COMMENT '发布者ID',
  `topic_id` bigint(11) unsigned DEFAULT NULL COMMENT '话题ID',
  `topic_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '话题名称',
  `is_top` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否置顶(0：未置顶 1：置顶)',
  `type` tinyint(2) DEFAULT '0' COMMENT '类型(0：图文 1：视频)',
  `img_uris` varchar(660) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '笔记图片链接(逗号隔开)',
  `video_uri` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频链接',
  `visible` tinyint(2) DEFAULT '0' COMMENT '可见范围(0：公开,所有人可见 1：仅对自己可见)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态(0：待审核 1：正常展示 2：被删除(逻辑删除) 3：被下架)',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_topic_id` (`topic_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';

```

频道表

| 字段        | 属性     | 含义                  |
| ----------- | -------- | --------------------- |
| id          | bigint   | 主键id                |
| name        | varchar  | 频道名称              |
| is_delete   | bit(1)   | 状态(0正常 1逻辑删除) |
| create_time | datetime | 创建时间              |
| update_time | datetime | 更新时间              |

```
CREATE TABLE `t_channel` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='频道表';

```

话题表

| 字段        | 属性     | 含义                  |
| ----------- | -------- | --------------------- |
| id          | bigint   | 主键id                |
| name        | varchar  | 话题名称              |
| is_delete   | bit(1)   | 状态(0正常 1逻辑删除) |
| create_time | datetime | 创建时间              |
| update_time | datetime | 更新时间              |

```
CREATE TABLE `t_topic` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '话题名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题表';

```

频道-话题关联表

| 字段        | 属性     | 含义     |
| ----------- | -------- | -------- |
| id          | bigint   | 主键id   |
| channel_id  | int      | 频道ID   |
| topic_id    | int      | 话题ID   |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

```
CREATE TABLE `t_channel_topic_rel` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint(11) unsigned NOT NULL COMMENT '频道ID',
  `topic_id` bigint(11) unsigned NOT NULL COMMENT '话题ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='频道-话题关联表';

```

### 设计细节分析

topic_name  话题名称  基本发布之后不会进行修改的  属于冗余字段  直接写道note表中，能防止总时联表查询

is_content_empty  笔记内容是否为空  笔记内容我们是存在cassandra中的 但是实际上小红书中允许用户不写内容的 所以可以通过该字段进行分析是否需要查kv获取笔记正文  从而减少不必要的网络IO操作  提高效率

img_uris  一个笔记能放多张图片  多张图片但是也会限制图片数量 在合理的数量范围内就可以通过 逗号 的方式 将各个图URL一起放到同一个表格属性中  从而减少了 note_image 类似的关联表的建立和查询 提高查询速度



**发布笔记 发起发布请求之前 先请求OSS对象存储服务 将图片和视频这类文件先上传至OSS中进行存储返回访问路径  笔记服务整合返回的访问路径和Leaf生成的ID号和笔记的其他状态 封装到请求对象中进行存储  ； 对于文本内容，远程调用KV服务存储到Cassandra中 ； 等待Cassandra中确保保存成功之后   整个笔记服务才会执行完毕 提醒用户保存成功**

![image-20241004193814995](../../ZealSingerBook/img/image-20241004193814995.png)

# Cassandra分区键

Cassandra中分区键的概念很重要，**用于区分数据该存放在哪个分区中的标准  每个分区键值对应于数据集的一部分  这部分数据会被存储在集群的一个或者多个节点上  这个就表明 分区键值一样的数据一定会被分配到同一个节点上**

分区键作为分区标准 也能在查询的时候 能帮助定位到哪个分区进行查找 从而提高查询效率

通过选择合理的分区键 可以控制数据在集群中的分布  避免热点数据和负载不平衡

## 分区键的选择

需要考虑

均匀性：分区键的选择需要尽量能做到数据分布平均

查询模式：分区键应该基于最常见的查询模式，也就是说，如果某个字段是大部分数据查询的条件，那么这个字段就需要成为分区键的一部分

复合主键：如果单一字段不满足查询需求 可以采用复合主键，复合主键是由分区键+聚簇列组成，其中分区键用于定位所在节点，聚簇列用于定位节点内的排序和查询数据

避免热点：选择不会导致热点的字段作为分区键。例如，避免使用时间戳作为分区键，除非你采取措施（如时间戳散列）来避免数据集中写入到少数几个节点

数据生命周期管理：分区键可以用于数据的生命周期管理，例如，使用日期作为分区键的一部分可以帮助实现基于时间的数据到期和清理

## Note表的分区键

可以看到 我们之前的分区键id使用的数据类型就是UUID  UUID有一个好处  随机生成  基本能保证数据的分布均匀 不会存在分布倾斜

![image-20241004195149395](../../ZealSingerBook/img/image-20241004195149395.png)

### 关联字段

但是可以发现，我们目前的NoteContent对象和Note对象之间其实没有关联字段的  所以我们需要先给Note表加上一个content_id

```
ALTER table t_note add column `content_uuid` varchar(36) DEFAULT '' COMMENT '笔记内容UUID';
```

所以将相关的id属性修改一下  UUID和String之间可以很好的互换 别报错就行

# 发布笔记

入参

```
{
    "type": 0, // 笔记类型，0 代表图文笔记，1 代表视频笔记
    "imgUris": [
     "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg"
    ], // 图片链接数组，当为图文笔记时，此字段不能为空
    "videoUri": "http://xxxx", // 视频连接，当为视频笔记时，此字段不能为空
    "title": "图文笔记测试标题", // 笔记标题
    "content": "图文笔记测试内容", // 笔记内容（可不填）
    "topicId": 1 // 话题 ID（可不填）
}

```

整体逻辑就是  根据type类型 封装对应的属性到imgUris中或者videUri中  然后将title直接封装到Note对象 然后直接将content笔记内容 判断其是否为空从而决定是否需要调用kv服务  然后topicId是否为空来决定是否要查询topicName   笔记ID 和 笔记内容UUID 分别采用 Leaf雪花算法生成  和 直接用UUID生成  确保笔记内容上传cassandra成功后 在将笔记note、对象存入MySQL，完成整个逻辑

最终代码如下

```Java
public Response<?> publicNote(PublishNoteReqVO publishNoteReqVO) {
        Integer code = publishNoteReqVO.getType();
        if(NoteTypeEnum.isValid(code)){
            NoteTypeEnum typeEnum = NoteTypeEnum.getType(code);
            if(Objects.isNull(typeEnum)){
                throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
            }
            String imgUris = null;
            Boolean isContentEmpty = true;
            String videoUri = null;
            switch (typeEnum){
                case TEXT:
                    //图文
                    List<String> imgUriList = publishNoteReqVO.getImgUris();
                    // 校验图片是否为空
                    Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                    // 校验图片数量
                    Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                    // 将图片链接拼接，以逗号分隔
                    imgUris = StringUtils.join(imgUriList, ",");
                    break;
                case VIDEO:
                    //视频
                    videoUri = publishNoteReqVO.getVideoUri();
                    // 校验视频链接是否为空
                    Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                    break;
                default:
                    throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
            }
            // 生成笔记ID  即数据库中的笔记ID
            String contentId = idGeneratorRpcService.getNoteId();
            // 笔记内容ID  即Cassandra中的对应的UUID
            String contentUuid = null;
            // 笔记内容文本
            String contentText = publishNoteReqVO.getContent();

            // 内容不为空 需要KV服务
            if(StringUtils.isNotBlank(contentText)){
                isContentEmpty = false;
                contentUuid = UUID.randomUUID().toString();
                // 调用kv服务进行存储
                Boolean isSaveSuccess = kvRpcService.addNoteContent(contentUuid, contentText);
                if(!isSaveSuccess){
                    throw new BusinessException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
                }
            }

            // 话题ID
            Long topicId = publishNoteReqVO.getTopicId();
            String topicName = null;
            // 查询话题名
            if(topicId!=null){
                 topicName= topicMapper.selectById(topicId).getName();
            }
            // 调用笔记服务 保存note笔记对象
            Note note = Note.builder().id(Long.valueOf(contentId))
                    .title(publishNoteReqVO.getTitle())
                    .isContentEmpty(isContentEmpty)
                    .creatorId(LoginUserContextHolder.getUserId())
                    .topicId(topicId)
                    .topicName(topicName)
                    .isTop(Boolean.FALSE)
                    .type(code)
                    .imgUris(imgUris)
                    .videoUri(videoUri)
                    .visible(NoteVisibleEnum.PUBLIC.getValue())
                    .contentUuid(contentUuid)
                    .status(NoteStatusEnum.NORMAL.getCode())
                    .build();

            try{
                noteMapper.insert(note);
            }catch (Exception e){
                log.error("===》保存笔记失败", e);
                if(StringUtils.isNotBlank(contentUuid)){
                    kvRpcService.deleteNoteContent(contentUuid);
                }
                throw new BusinessException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }

            return Response.success();
        }else{
            throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
        }
    }
```

压测测试

因为正常互联网上其实读操作会比写操作多很多  我们这个属于写操作 QPS为237.....啊这....其实有点少....

![image-20241005190036336](../../ZealSingerBook/img/image-20241005190036336.png)

## 尝试优化（未完成）



# 查看笔记详情

## 根据用户查询信息

用户可以通过点击感兴趣的笔记 从而进去查看笔记详情  笔记详情其他的好说  现在是上头这个发布者的信息 

我们知道 笔记Note表中是存在creatorId即发布者ID的，我们需要给接口进行查询， 所以我们需要先写一个根据用户ID查用户信息的接口

![image-20241005162125696](../../ZealSingerBook/img/image-20241005162125696.png)

入参

```
{
	"id": 2
}
```

出参

```
{
	"success": true,
	"message": null,
	"errorCode": null,
	"data": {
		"id": 2, // 用户 ID
		"nickName": "xxxx", // 用户昵称
		"avatar": "http://127.0.0.1:9000/xiaohashu/f22e21fb0c144c088bd20bc616916ff3.jpg" // 用户头像
	}
}

```

主体逻辑

```Java
public Response<?> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long userId = findUserByIdReqDTO.getId();
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new BusinessException(ResponseCodeEnum.USER_NOT_EXIST);
        }
        FindUserByIdRspDTO findUserByIdRspDTO = FindUserByIdRspDTO.builder()
                .id(user.getId())
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .build();
        return Response.success(findUserByIdRspDTO);
    }
```

来进行一波压测 测试接口单机QPS大概

查询4.5min  以共7.5w样本，QPS为265

![image-20241005220327303](../../ZealSingerBook/img/image-20241005220327303.png)

## 加入缓存进行优化

缓存设计需要防止雪崩三兄弟--

缓存穿透(**缓存穿透是指查询一个缓存中不存在的数据，同时，数据库中同样不存在该条数据  一般是恶意攻击**)    ；

```
防范措施：
方法一：保存空值，但同时会出现redis中存放大量无效数据缓存命中低，当存入数据过多极端情况下甚至会删除有效缓存数据
方法二：布隆过滤器  不存在的一定能防住 但是存在的不一定真存在 存在一定的误判
```

缓存击穿（**缓存击穿是指某个热点数据在失效的瞬间，大量的并发请求直接打到了数据库上，导致数据库压力过大**）   

```
防范措施：
1：热点数据不设置过期时间
2：加分布式锁 当缓存过期  某个请求打过来查询数据库的时候  只允许一个请求进行 其他的先阻塞  等这个查完后 也自然更新了缓存 等锁开放后 其他的能直接走缓存
3：采用本地缓存  本地缓存和redis互为犄角  相互补充 两者设置不同的过期时长
4：热点key过期时间检测机制  定时检测热点key的剩余过期时间  即使进行更新
```

缓存雪崩（**当大量的缓存数据在同一时间失效，导致大量的请求直接打到数据库上，这种现象称为缓存雪崩。**）

```
防范措施：
比较常见的做法是，在存入缓存时，为它们设置不同的失效时间，从而避免同一时间，大量缓存同时失效的情况发生。
```

### 加入Redis缓存

给User模块加入redis相关配置  线程池配置（缓存操作可以线程池任务的方式异步进行 提高效率）  和JsonUtil工具类（将json封装为对象）

JsonUtil使用的是common中的公共组件 添加一个将json转化为对象的操作即可  直接使用OBJECT_MAPPER的readValue方法即可

```Java
@SneakyThrows
    public static <T> T JsonStringToObj(String json,Class<T> clazz){
        if(StringUtils.isNotBlank(json)) {
            return OBJECT_MAPPER.readValue(json, clazz);
        }
        return null;
    }
```

redis配置和线程池配置参考之前的模块即可

然后是server逻辑修改

```Java
public Response<?> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long userId = findUserByIdReqDTO.getId();
        FindUserByIdRspDTO findUserByIdRspDTO = null;
        // 先从缓存中拿取 如果没有在从库中获取
        String userinfoStr=(String) redisTemplate.opsForValue().get(RedisConstant.buildUserRoleKey(userId));
        if(StringUtils.isNotBlank(userinfoStr)){
            findUserByIdRspDTO = JsonUtil.JsonStringToObj(userinfoStr, FindUserByIdRspDTO.class);
            return Response.success(findUserByIdRspDTO);
        }

        // 从数据库中获取 查询后存入redis缓存
        User user = userMapper.selectById(userId);
        if(user == null){
            // 缓存空值  防止穿透
            long expireSeconds = 60+RandomUtil.randomInt(60);
            redisTemplate.opsForValue().set(RedisConstant.buildUserRoleKey(userId), "null",expireSeconds, TimeUnit.SECONDS);
            throw new BusinessException(ResponseCodeEnum.USER_NOT_EXIST);
        }
        findUserByIdRspDTO = FindUserByIdRspDTO.builder()
                .id(user.getId())
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .build();

        //异步缓存数据 防止阻塞主线程  一分钟+随机数 防止雪崩

        FindUserByIdRspDTO finalFindUserByIdRspDTO = findUserByIdRspDTO;
        threadPoolTaskExecutor.submit(()->{
            long expireSeconds = 60+RandomUtil.randomInt(60);
            redisTemplate.opsForValue().set(RedisConstant.buildUserRoleKey(userId), JsonUtil.ObjToJsonString(finalFindUserByIdRspDTO),expireSeconds, TimeUnit.SECONDS);
        });

        return Response.success(findUserByIdRspDTO);
    }
```

### 添加本地缓存caffeine

caffeine的使用和介绍可以去看以前的cloud笔记 里面有涉及

整体逻辑的添加也很简单 就是在查询redis之前再加一层本地缓存 查询完本地缓存后再查redis 然后再查mysql

![image-20241005220250567](../../ZealSingerBook/img/image-20241005220250567.png)

二次压测查看QPS  发现尼玛一样 严重怀疑是网络和设备的问题

![image-20241005223433628](../../ZealSingerBook/img/image-20241005223433628.png)



------

然后是继续写查看笔记详情的接口

入参

```json
{
	“noteId":...
}
```

出参

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": {
        "id": 1842530793780412511,
        "title": "图文笔记测试标题",
        "creatorId": 2,
        "creatorName": "zealsingerbook101",
        "topicId": 1,
        "topicName": "高分美剧推荐",
        "isTop": false,
        "type": 0,
        "imgUris": [
            "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg"
        ],
        "videoUri": null,
        "visible": 0,
        "updateTime": null,
        "contentUuid": "ec79c4dd-c835-4381-8345-1e12860b7d97",
        "content": null
    }
}
```

主体逻辑

```
public Response<?> findById(FindNoteByIdReqDTO findNoteByIdReqDTO) {
        String noteId = findNoteByIdReqDTO.getNoteId();
        Note note = noteMapper.selectById(noteId);
        if(note==null){
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkVisible(note);
        String content =null;
        List<String> imgUris = null;
        if(!note.getIsContentEmpty()) {
            FindNoteContentRspDTO noteContent = kvRpcService.findNoteContentById(note.getContentUuid());
            content = noteContent.getContent();
        }

        FindUserByIdRspDTO userInfoById = userRpcService.getUserInfoById(note.getCreatorId());
        String creatorName = userInfoById.getNickname();
        String noteImgUris = note.getImgUris();
        if(StringUtils.isNotBlank(noteImgUris)){
            imgUris  = Arrays.asList(noteImgUris.split(","));
        }

        FindNoteByIdRspVO resultNoteVO = FindNoteByIdRspVO.builder().id(note.getId())
                .title(note.getTitle())
                .creatorId(note.getCreatorId())
                .creatorName(creatorName)
                .topicId(note.getTopicId())
                .topicName(note.getTopicName())
                .isTop(note.getIsTop())
                .type(note.getType())
                .imgUris(imgUris)
                .videoUri(note.getVideoUri())
                .visible(note.getVisible())
                .content(content)
                .contentUuid(note.getContentUuid())
                .updateTime(note.getUpdateTime())
                .build();

        return Response.success(resultNoteVO);

    }
```

同样的 可以添加redis缓存和本地缓存 从而提高接口的高可用

```Java
 @Override
    public Response<?> findById(FindNoteByIdReqDTO findNoteByIdReqDTO) {
        String noteId = findNoteByIdReqDTO.getNoteId();
        FindNoteByIdRspVO resultNoteVO = null;
        resultNoteVO = LOCAL_NOTEVO_CACHE.getIfPresent(noteId);
        if(resultNoteVO !=null){
            log.info("===>命中本地缓存{}",resultNoteVO);
            return Response.success(resultNoteVO);
        }
        // 查询redis缓存
        String noteStr = (String) redisTemplate.opsForValue().get(RedisConstant.getNoteCacheId(noteId));
        if(StringUtils.isNotBlank(noteStr)){
            resultNoteVO = JsonUtil.JsonStringToObj(noteStr, FindNoteByIdRspVO.class);
            // 异步存入本地缓存
            FindNoteByIdRspVO finalResultNoteVO1 = resultNoteVO;
            threadPoolTaskExecutor.submit(()-> LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO1));
            return Response.success(resultNoteVO);
        }

        // 两层缓存中均无数据 查库！！

        Note note = noteMapper.selectById(noteId);
        if(note==null){
            // 缓存空值 防止穿透
            long expireSeconds = 60+RandomUtil.randomInt(60);
            redisTemplate.opsForValue().set(RedisConstant.getNoteCacheId(noteId), "null", expireSeconds, TimeUnit.SECONDS);
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkVisible(note);
        String content =null;
        List<String> imgUris = null;
        if(!note.getIsContentEmpty()) {
            FindNoteContentRspDTO noteContent = kvRpcService.findNoteContentById(note.getContentUuid());
            content = noteContent.getContent();
        }

        FindUserByIdRspDTO userInfoById = userRpcService.getUserInfoById(note.getCreatorId());
        String creatorName = userInfoById.getNickname();
        String noteImgUris = note.getImgUris();
        if(StringUtils.isNotBlank(noteImgUris)){
            imgUris  = Arrays.asList(noteImgUris.split(","));
        }

        resultNoteVO = FindNoteByIdRspVO.builder().id(note.getId())
                .title(note.getTitle())
                .creatorId(note.getCreatorId())
                .creatorName(creatorName)
                .topicId(note.getTopicId())
                .topicName(note.getTopicName())
                .isTop(note.getIsTop())
                .type(note.getType())
                .imgUris(imgUris)
                .videoUri(note.getVideoUri())
                .visible(note.getVisible())
                .content(content)
                .contentUuid(note.getContentUuid())
                .updateTime(note.getUpdateTime())
                .build();

        // 异步写入redis缓存 和 本地缓存
        FindNoteByIdRspVO finalResultNoteVO = resultNoteVO;
        threadPoolTaskExecutor.submit(()->{
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO);
            redisTemplate.opsForValue().set(RedisConstant.getNoteCacheId(noteId), JsonUtil.ObjToJsonString(finalResultNoteVO), expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(resultNoteVO);

    }
```

## 压测

但是还是很令人心痛的吞吐量数据  四分半  近6w样本数据  220的吞吐量......  有点抽象

![image-20241006160306627](../../ZealSingerBook/img/image-20241006160306627.png)

## 二次优化！！！

发现我们目前的架构是

查找笔记详情----->查库中对应的数据----->KV模块服务找content内容---->User模块服务只要userName---->返回结果

会发现 本来模块间远程调用其实就已经是属于比较耗时的操作了 我们这里同步进行两个调用 效率自然会比较低下

![image-20241006160657407](../../ZealSingerBook/img/image-20241006160657407.png)

为了解决这个问题 我们的用户服务和KV服务的调用是两个完全不会冲突的服务调用 所以完全可以让这两个过程异步调用 并发调用

![image-20241006160833179](../../ZealSingerBook/img/image-20241006160833179.png)

### CompletableFuture

`CompletableFuture` 是 `Future` 的一个扩展，能够简化异步编程。它允许你更容易地组合和变换任务的结果，并提供了丰富的 API 来处理错误和取消操作

#### 简单入门

如下是使用`CompletableFuture` 实现异步单个任务并且等待任务完成得到结果

```Java
public class CompletableFutureExample {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            // 模拟一个耗时的任务
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 42; // 返回任务的结果
        });

        // 等待任务完成
        Integer result = future.get();
        System.out.println("Result: " + result);
    }
}
```

`CompletableFuture` 提供了链式编程，能轻松实现多个异步任务链接在一起

```Java
public class CompletableFutureChainExample {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "Hello")
            .thenApply(s -> s.toUpperCase()) // 转换成大写
            .thenCompose(s -> CompletableFuture.supplyAsync(() -> s + " World!")); // 组合字符串

        String result = future.get();
        System.out.println("Result: " + result);
    }
}

最终输出  Result: HELLO World
可以发现 第二个任务也会等待第一个任务
```

CompletableFuture中也提供了对应的错误处理 当异步任务执行期间出现异常 可以按照我们的配置处理异常  主要是exceptionally方法和handler方法

```
public class CompletableFutureErrorHandling {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    if (Math.random() < 0.5) { // 获取一个随机数，若小于 0.5， 则抛出运行时异常
                        throw new RuntimeException("Something went wrong!");
                    }
                    return "Success!"; // 否则，返回 Success
                })
                .exceptionally(e -> { // 处理异常
                    System.out.println("Caught exception: " + e.getMessage());
                    return "Error occurred";
                });

        String result = future.get();
        System.out.println("Result: " + result);
    }
}
```

![image-20241006161431293](../../ZealSingerBook/img/image-20241006161431293.png)

同样 也提供了异步任务取消机制 能很好地中断异步任务的执行 **cancel方法**

可以猜想 cancel底层其实就是抛出一个异常CancellationException被cache捕获

```Java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureCancellation {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Done!";
        });

        future.cancel(true); // 尝试取消任务

        try {
            String result = future.get();
            System.out.println("Result: " + result);
        } catch (CancellationException e) {
            System.out.println("Task was cancelled.");
        }
    }
}

```

![image-20241006161547865](../../ZealSingerBook/img/image-20241006161547865.png)

#### 重构代码

```Java
@Override
    public Response<?> findById(FindNoteByIdReqDTO findNoteByIdReqDTO) throws ExecutionException, InterruptedException {
        String noteId = findNoteByIdReqDTO.getNoteId();
        FindNoteByIdRspVO resultNoteVO = null;
        resultNoteVO = LOCAL_NOTEVO_CACHE.getIfPresent(noteId);
        if(resultNoteVO !=null){
            log.info("===>命中本地缓存{}",resultNoteVO);
            return Response.success(resultNoteVO);
        }
        // 查询redis缓存
        String noteStr = (String) redisTemplate.opsForValue().get(RedisConstant.getNoteCacheId(noteId));
        if(StringUtils.isNotBlank(noteStr)){
            resultNoteVO = JsonUtil.JsonStringToObj(noteStr, FindNoteByIdRspVO.class);
            // 异步存入本地缓存
            FindNoteByIdRspVO finalResultNoteVO1 = resultNoteVO;
            threadPoolTaskExecutor.submit(()-> LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO1));
            return Response.success(resultNoteVO);
        }

        // 两层缓存中均无数据 查库！！

        Note note = noteMapper.selectById(noteId);
        if(note==null){
            // 缓存空值 防止穿透
            long expireSeconds = 60+RandomUtil.randomInt(60);
            redisTemplate.opsForValue().set(RedisConstant.getNoteCacheId(noteId), "null", expireSeconds, TimeUnit.SECONDS);
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkVisible(note);
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if(!note.getIsContentEmpty()) {
            contentResultFuture = contentResultFuture.supplyAsync(()->{
                // 异步调用KV服务获取笔记内容
                FindNoteContentRspDTO noteContentById = kvRpcService.findNoteContentById(note.getContentUuid());
                return noteContentById.getContent();
            },threadPoolTaskExecutor);
        }

        // 异步调用user模块
        CompletableFuture<FindUserByIdRspDTO> userResultFuture =  CompletableFuture.supplyAsync(()->userRpcService.getUserInfoById(note.getCreatorId()),threadPoolTaskExecutor);
        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        // 整体异步任务连接封装返回对象
        CompletableFuture<FindNoteByIdRspVO> resultFuture = CompletableFuture
            // allof方法 参数为可变参数 接收多个completableFutrue对象  等待其所有接受的futrue对象完成才会进行下面的任务
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                            FindUserByIdRspDTO join = userResultFuture.join();
                            String creatorName = join.getNickname();
                            String content = finalContentResultFuture.join();
                            List<String> imgUris = null;
                            String noteImgUris = note.getImgUris();
                            if(StringUtils.isNotBlank(noteImgUris)){
                                imgUris  = Arrays.asList(noteImgUris.split(","));
                            }
                            return FindNoteByIdRspVO.builder().id(note.getId())
                                    .title(note.getTitle())
                                    .creatorId(note.getCreatorId())
                                    .creatorName(creatorName)
                                    .topicId(note.getTopicId())
                                    .topicName(note.getTopicName())
                                    .isTop(note.getIsTop())
                                    .type(note.getType())
                                    .imgUris(imgUris)
                                    .videoUri(note.getVideoUri())
                                    .visible(note.getVisible())
                                    .content(content)
                                    .contentUuid(note.getContentUuid())
                                    .updateTime(note.getUpdateTime())
                                    .build();
                        });

        // 等待异步运行结果 获取拼装后的 FindNoteDetailRspVO
        resultNoteVO = resultFuture.get();
        // 异步写入redis缓存 和 本地缓存
        FindNoteByIdRspVO finalResultNoteVO = resultNoteVO;
        threadPoolTaskExecutor.submit(()->{
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO);
            redisTemplate.opsForValue().set(RedisConstant.getNoteCacheId(noteId), JsonUtil.ObjToJsonString(finalResultNoteVO), expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(resultNoteVO);

    }
```

# 更新笔记

更新笔记 接口逻辑还是比较简单的  无需多言

入参

```json
{
    "id":"",
    "title":"",
    "content": "",
    "imgUris": [],
    "videoUri": "",
    "topicId": 4, // 话题
    "type": 1
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

## 注意事项

更新操作的整体逻辑不难 但是细节需要注意

1：对于**不同type需要记得进行特判**    例如 图文的图片数量不能大于8且不能为空

2：如果**内容content变为了空 需要讲KV中对应存储的内容删除 如果没有变空 需要同步更新KV中的数据**

3：**内容是否为空也需要同时影响数据库中的uuid 和 isContentEmpty字段的属性**

4：全部操作完成之后 最后**需要记得删除本地缓存和redis缓存**（这个不建议异步删除  因为整体删除了才算更新成功 异步的话 返回success结果异步过程失败或者本线程还在异步还没删除完成的的时候再来访问就容易出现问题）

5：同时涉及到先后更新KV数据库和MySQL还有缓存 所以**如果有一个步骤失败 前面的都应该回滚 所以添加@Transactional()注解让Spring管理事务回滚**

## 业务代码

```Java
@Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReqVO reqVO) {
        Integer type = reqVO.getType();
        NoteTypeEnum typeEnum = NoteTypeEnum.getType(type);
        if(typeEnum==null){
            throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
        }
        List<String> imgUriList = reqVO.getImgUris();
        String videoUri = reqVO.getVideoUri();
        String imgUris = null;
        // 根据类型进行特判
        if(NoteTypeEnum.TEXT.getValue().equals(type)){
            Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList),"图片不能为空");
            Preconditions.checkArgument(imgUriList.size()<=8,"图片数量不能多于8张");
        }else{
            Preconditions.checkArgument(StringUtils.isNotBlank(videoUri),"视频不能为空");
        }
        imgUris = String.join(",",imgUriList);

        String content = reqVO.getContent();
        String id = reqVO.getId();
        Note note = noteMapper.selectById(id);
        String contentUuid = note.getContentUuid();
        LambdaUpdateWrapper<Note> queryWrapper = new LambdaUpdateWrapper<>();
        if(StringUtils.isNotBlank(content)){
            Boolean isUpdateSuccess = kvRpcService.updateNoteContent(contentUuid, content);
            if(!isUpdateSuccess){
                throw new BusinessException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
            }
        }else{
            // 如果内容为空 则删除kv存储中的内容
            kvRpcService.deleteNoteContent(contentUuid);
            contentUuid = null;
        }

        String topicName = null;
        Long topicId = reqVO.getTopicId();
        if(topicId!=null){
            topicName = topicMapper.selectById(topicId).getName();
        }

        queryWrapper.eq(Note::getId, id)
                .set(Note::getImgUris,imgUris)
                .set(Note::getTitle,reqVO.getTitle())
                .set(Note::getTopicId, topicId)
                .set(Note::getTopicName,topicName)
                .set(Note::getType, type)
                .set(Note::getContentUuid,contentUuid)
                .set(Note::getIsContentEmpty,StringUtils.isBlank(content))
                .set(Note::getVideoUri, videoUri);

        noteMapper.update(queryWrapper);

        // 更新完毕后删除缓存
        LOCAL_NOTEVO_CACHE.invalidate(id);
        redisTemplate.delete(RedisConstant.getNoteCacheId(id));

        return Response.success();
    }
```

## 存在的问题以及MQ解决

**上述业务单机测试没啥问题 但是需要考虑到分布式环境  我们的删除本地笔记缓存的操作 只会操作本地  就会导致数据更新后  操作的设备对应的服务器上的被清除 其他的服务器上的本地数据均没有被清理 这个自然是不行的  我们需要的是 所有的对应的笔记服务中的本地缓存都被删除**

![image-20241006212706004](../../ZealSingerBook/img/image-20241006212706004.png)

所以针对这个问题 我们采用MQ进行解决  采用消息通知的方式  当某一个服务触发了更新 就让所有的订阅消息的服务接收到消息后进行删除本地缓存的操作  从而达到好的操作效果 
