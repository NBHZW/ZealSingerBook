# 计数模块

在zealsingerbook中，为了应对高并发和大数据量，计数功能自然是少不了的。在平台中，笔记数量，粉丝数量，点赞数量，收藏数量等操作，都是需要频繁的增减的，是属于高并发的读写操作，所以该模块的高可用，高效率是必备的

# 模块职责

## 用户维度

**用户的关注数，粉丝数，收藏数，总获得的点赞数目**

![image-20241017101741524](../../ZealSingerBook/img/image-20241017101741524.png)

除此之外，点击获赞与收藏，可以查看到详细的获赞和收藏情况：包括了**当前发布笔记总数，收藏总数，获得点赞总数**

![image-20241017101829841](../../ZealSingerBook/img/image-20241017101829841.png)

## 笔记维度

**点赞量，收藏量，评论量**

![image-20241017102004131](../../ZealSingerBook/img/image-20241017102004131.png)

## 评论维度

每条评论也支持点赞，需要该评论的总点赞数

![image-20241017102113148](../../ZealSingerBook/img/image-20241017102113148.png)

# 架构设计

首先，想到计数，我们第一感觉就是使用**count**，那么我们来分析一下是否可以？

答案自然是不行的，存在**IO过载和性能瓶颈**

查询计数功能我们可以看到，**是无时不刻，极其频繁的一个读取操作**，而且计数相关的操作很容易会进行增加和减少的操作，一篇热门的笔记，会有很多的收藏和点赞，也会有很多评论，每条评论又有可能会有很多的点赞和取消点赞，**数据的变化是非常的频繁的**，**如果采用count操作，数据库需要频繁的大量的扫描来进行计数，大大增加的数据库的负载**

再者，**数据库操作涉及磁盘IO，而直接频繁的count容易出现IO瓶颈，对于某些热点数据，频繁的被count，导致数据库响应变慢甚至崩溃**

![image-20241017102612600](../../ZealSingerBook/img/image-20241017102612600.png)

所以自然，我么们不能依靠count进行计数，而是应该**新增一些关联表**，例如笔记点赞表，收藏表，用户计数表等，通过外部关联一些表来进行记录，每次需要的时候进行一次条件查表即可，而不需要去count原来的表

## 表设计

### 笔记点赞表

因为暂时还没开放评论模块，但是我们有用户模块，但是存在笔记模块，所以我们先搭建笔记点赞表

| 字段        | 介绍                                   |
| ----------- | -------------------------------------- |
| id          | 主键ID                                 |
| user_id     | 用户ID                                 |
| note_id     | 笔记ID                                 |
| create_time | 创建时间                               |
| update_time | 更新时间                               |
| status      | 点赞状态（0取消点赞/没有点赞   1点赞） |

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

每一条记录表示了  **userID用户对noteID笔记的点赞状态，另外，还为笔记点赞表的 `user_id` 和 `note_id` 两个字段，创建了联合唯一索引 `uk_user_id_note_id` ，提升查询效率的同时，还能保证关联记录的幂等性，保证同一个用户无法多次点赞同一篇笔记。** 

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
| note_id       | 笔记ID       |
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