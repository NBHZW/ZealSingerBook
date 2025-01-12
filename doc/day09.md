# 短文本技术选型对比

对于小红书而言，发布笔记/帖子 和 访问帖子是核心业务，允许发布者进行编辑，保存，发布和删除，允许其他用户查看 点赞 评论等功能

此时就应该思考一个问题对于笔记详情的展示，除了笔记的标题、图片链接、发布时间等等基础信息外，**短文本数据，如笔记内容、评论内容存储在 MySQL 数据库中合适吗？**

![image-20241002143757755](../../ZealSingerBook/img/image-20241002143757755.png)

对此 需要进行技术选型分析

## 关系型数据库

如果使用MySQL，每段信息一行信息，行存储在页中，**如果数据过大，就会存储到溢出页中，原本页中会存储对应溢出页的指针**，那么就会导致查询一份数据，先找到原本的页的位置然后再找到溢出页，**访问一条长文本信息需要访问多个页，增大了IO操作**

```
MySQL作为选型的缺点：
1：由于异常页的存在，IO操作多，开销大
2：MySQL对于长文本类型text不支持很好的索引支持，对于长文本MySQL只支持前缀索引，全文索引支持度不是很好
3：事务开销 InnoDB 支持 ACID 事务，事务日志和回滚日志需要额外的存储和处理。对于频繁更新的大量长文本数据，这些日志会带来显著的性能开销
4：InnoDB 的数据页大小固定为 16KB。当文本数据长度不固定时，可能会导致数据页的填充率不高，浪费存储空间，并且增加了页碎片(更新前后数据大小不一致 导致原有空间就不足以保存 从而在页面上出现空隙 ； 删除后留下的空间不足以被重新插入或者利用导致碎片 ； 数据大于当前页剩余的空间，InnoDB会将页拆分为两个导致额外的碎片)
```

**性能要求较高场景下，关系型数据库不适合存储文本内容。**

## 非关系型数据库

非关系型数据库，我们最熟的就是Redis，Redis是否满足我们的需求呢？

Redis其实是属于一种**内存型非关系型数据库**

```
Redis的特点就是 快，内置计数功能，保存在内存中，高并发读写
适用于实时统计  例如用户在线数目  临时数据的存储
```

**分布式KV存储系统** 

 常见的有Cassandra ，TiKV等，采用分布式存储，高可用，可拓展性强，存储在磁盘上（造价便宜）

**分布式文档数据库**

以文档的形式存储数据，通常使用JSON，BSON或者XML的形式存储数据，常见的就是MongDB，适合存储半结构化或者无结构化的数据，灵活性强，支持多种索引，搜素性能号，支持水平分片，可拓展性强，适用于日志管理，文档管理，需要快速迭代开发的应用，数据结构变化比较频繁的场景

## 最终选型

本项目主要关注性能，内存型KV数据库内存造价贵，数据持久化性能一般，所以我们优先考虑分布式文档数据库或者KV存储系统

分布式文档型数据库性能上自然没有分布式KV存储系统好用，所以我们使用分布式KV存储系统，这里我们选择Cassandra

# Cassandra

Apache Cassandra 是一个开源的分布式 NoSQL（Not Only SQL）数据库管理系统，专为处理大规模数据量和高写入频率的工作负载而设计

- **高可用性**：Cassandra 是一个无单点故障的系统，它通过数据复制和一致性级别选择，确保即使在节点失败的情况下数据仍然可访问。
- **水平可扩展性**：Cassandra 能够通过添加更多节点到集群中轻松扩展，无需停机，这使得它能够处理不断增长的数据量和用户负载。
- **分布式数据存储**：数据在集群中的多个节点上分布存储，每个节点都是平等的，没有主从之分，这有助于提高性能和可靠性。
- **最终一致性**：Cassandra 允许开发者选择数据的一致性和可用性之间的权衡，通过可配置的一致性级别，可以在强一致性和高可用性之间找到合适的平衡点。
- **数据模型**：Cassandra 使用列族（column-family）的数据模型，允许以宽列的方式存储数据，非常适合存储半结构化或非结构化数据。
- **数据压缩和索引**：Cassandra 支持数据压缩和创建二级索引，以提高存储效率和查询性能。
- **多数据中心复制**：Cassandra 支持跨多个地理区域的数据中心复制，以实现数据的地理分布和灾难恢复。

dokcer 安装 Cassandra

```shell
docker pull cassandra:latest


docker run --name cassandra -d -p 9042:9042 -v /root/Docker/cassandra:/var/lib/cassandra cassandra:latest
```

安装完后 进入到容器中

```
docker exec -it cassandra /bin/sh
```

执行如下命令 可以进去到cqlsh命令行工具

```
cqlsh
```

![image-20241002153340665](../../ZealSingerBook/img/image-20241002153340665.png)

cqlsh是cassandra的特有的命令行工具，允许我们向Cassandra数据库发送执行进行增删改查等

## 相关概念

节点Node：Cassandra集群中，每一个服务器都属于一个节点，每个节点都会存储数据，相互之间没有主从关系，各个节点之间都是平等的

集群Cluster：多个节点组成的分布式系统称之为集群，集群中的节点一起工作，一起出来读写等请求并且存储数据

数据中心Data Center：集群中的节点可以分布在多个数据中心中，每个数据中心可以包括多个节点，数据中心的划分有利于跨地域的高可用性

键空间 Keyspace：键空间是一个逻辑容器，用于管理多个表，可以理解为MySQL中的库的结构层次，另外，键空间中定义了数据复制的策略

表 Table：表是存储数据的基本单位 有行和列组成，每张表都有一个唯一的名称和定义

主键Primary Key：每行数据都有一个唯一的主键，主键由分区键和可选的列组成，用于唯一标识数据行

分区键 Partition Key： 使用分区键的哈希值将数据分布保存到不同的节点上，从而实现负载均衡和数据的水平拓展，分区键可以是单个列也可以是多个列的组合（复合分区键）

## 常用语法

### 键空间

创建键空间--可以理解为创建一个MySQL中的库结构

```CQL
CREATE KEYSPACE IF NOT EXISTS zealsingerbook
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};  // 注意 语句以分号结尾  需要打分号

CREATE KEYSPACE IF NOT EXISTS：KEYSPACE代表键空间  创建键空间如果不存在
zealsingerbook：键空间的名字
WITH replication {.......} ： 指定keyspace键空间的复制策略和配置，复制策略决定了数据如何在集群中复制和分布

'class': 'SimpleStrategy' : 这里指定了复制策略的类型为 SimpleStrategy。SimpleStrategy 是一种基本的复制策略，适用于单数据中心的部署。它将数据均匀地分布到集群中的节点上。
'replication_factor': 1  这是复制因子，表示每个数据分区的副本数量。在这个例子中，replication_factor 设置为 1，意味着每个数据分区只有一个副本，这通常用于测试或开发环境，但在生产环境中可能不是最佳实践，因为缺乏冗余会导致数据丢失的风险增加。

```

![image-20241002154941253](../../ZealSingerBook/img/image-20241002154941253.png)

查看所有键空间和某个空间下的所有表

```cql
DESCRIBE KEYSPACES;
DESCRIBE TABLES;
```

![image-20241002154952012](../../ZealSingerBook/img/image-20241002154952012.png)

删除某个键空间

```CQL
DROP KEYSPACE IF EXISTS 键空间名字
```

选择操作某个键空间 类似于MySQL中选择库的语句作用

```cql
USE 键空间名字
```

![image-20241002155151016](../../ZealSingerBook/img/image-20241002155151016.png)

### 表

键空间下就是表  ，下面是和表相关的操作

创建表

执行如下语句，创建一张 `note_content` 笔记内容表。这里注意，由于我们是拿 Cassandra 充当 K-V 键值存储数据库，所以表中只包含两个字段（实际可以支持多字段），`id` 主键充当 `Key` , 笔记内容 `content` 充当 `Value` 

```cql
CREATE TABLE note_content (
    id UUID PRIMARY KEY,
    content TEXT
);
CREATE TABLE: 这是 Cassandra 中创建新表的命令。
note_content: 表的名称。
( 和 )：这些括号包含了表的列定义和主键定义。
id UUID PRIMARY KEY: 这里定义了表中的一个列 id，其数据类型是 UUID（通用唯一标识符）。PRIMARY KEY 指示 id 列是表的主键。在 Cassandra 中，主键用于唯一标识表中的每一行，同时也是数据在集群中分区的依据。
content TEXT: 这里定义了另一个列 content，其数据类型是 TEXT。TEXT 类型用于存储文本字符串。
```

往表中插入数据

```cql
INSERT INTO note_content (id, content) VALUES (uuid(), '这是一条测试笔记');
```

![image-20241002155745380](../../ZealSingerBook/img/image-20241002155745380.png)

查询某个表中的所有数据

```cql
SELECT * FROM note_content;
```

![image-20241002155753895](../../ZealSingerBook/img/image-20241002155753895.png)

条件查询

```cql
SELECT * FROM note_content WHERE id = 77b3f17f-0952-4145-8160-2f5ce69702f5;
```

![image-20241002155959690](../../ZealSingerBook/img/image-20241002155959690.png)

更新记录

```cql
UPDATE note_content SET content = '更新后的评论内容' WHERE id = 77b3f17f-0952-4145-8160-2f5ce69702f5;
```

![image-20241002160041682](../../ZealSingerBook/img/image-20241002160041682.png)

删除记录

```cql
DELETE FROM note_content WHERE id = 77b3f17f-0952-4145-8160-2f5ce69702f5;
```

# Java操作Cassandra

直接利用Cassandra提供的Java SDK进行操作即可

## 导入依赖

```xml
<!-- Cassandra 存储 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-cassandra</artifactId>
        </dependency>
```

## 配置文件中添加配置信息

```yaml
spring:
  cassandra:
    keyspace-name: zealsingerbook
    contact-points: 192.168.17.131
    port: 9042
```

![image-20241003105417728](../../ZealSingerBook/img/image-20241003105417728.png)

## 添加配置类  

**extends AbstractCassandraConfiguration: AbstractCassandraConfiguration 是 Spring Data Cassandra 提供的一个抽象基类，它包含了一些默认的方法实现，用于配置 Cassandra 连接。**
**getKeyspaceName(), getContactPoints(), 和 getPort() 方法:**
**这些方法都是覆盖（override）自父类 AbstractCassandraConfiguration 的抽象方法。它们分别返回 keyspace 名称、连接和端口号。**
**当 Spring 初始化 Cassandra 连接时，会调用这些方法来获取配置信息。**

```Java
@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keySpace;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    /*
     * Provide a keyspace name to the configuration.
     */
    @Override
    public String getKeyspaceName() {
        return keySpace;
    }

    @Override
    public String getContactPoints() {
        return contactPoints;
    }

    @Override
    public int getPort() {
        return port;
    }
}
```

![image-20241003105818236](../../ZealSingerBook/img/image-20241003105818236.png)

## 配置保存内容实体类

@Table注解类似于MybatisPlus的@TableName注解 表明类所对应的键空间

@PrimaryKey类似MybatisPlus中的@TableId  标识键空间主键列名

```Java
@Table("note_content")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteContent {
    @PrimaryKey("id")
    private UUID id;
    private String content;
}
```

![image-20241003105515896](../../ZealSingerBook/img/image-20241003105515896.png)

## Server接口

**继承CassandraRepository接口 传入两个参数指定泛型  类似于MyBatisPlus中的mapper层接口继承BaseMapper  第一个泛型指定对应的存储的实体类  第二个为主键的数据类型**

```java 
public interface NoteContentRepository extends CassandraRepository<NoteContent, UUID> {
}
```

![image-20241003105939405](../../ZealSingerBook/img/image-20241003105939405.png)

## 测试业务编写

直接在Server层中调用Server接口中继承下来的方法即可

![image-20241003110211302](../../ZealSingerBook/img/image-20241003110211302.png)

查看Cassandra中的效果  我们调用接口传入content就是"喵的测试真麻烦"这条数据  测试成功

![image-20241003110510432](../../ZealSingerBook/img/image-20241003110510432.png)
