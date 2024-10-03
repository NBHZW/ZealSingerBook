# 分布式ID

我们已经学习了Cassndra数据库相关的操作，紧接着就能完成笔记的新增，删除，查找接口的编写

记得biz写业务  api写外部调用接口

![image-20241003114004104](../../ZealSingerBook/img/image-20241003114004104.png)

但是我们这里发现一个问题 我们的主键ID 使用的都是UUID 这个对性能是否会存在很大影响？

## 性能指标

**对一个分布式项目 我们讨论性能主要是如下几个方面**

**并发用户量：在压测中，一般称之为虚拟用户，对应现实中的操作用户，简称VU**

**（并发用户和在线用户和注册用户有区别   注册用户一般是指在数据库中的已有数据的用户   在线用户只是挂在系统上 不会对服务器产生压力   但是并发用户一定会对服务器产生压力）**

**QPS  每秒查询次数**

**TPS  每秒事务数，也就是平常说的吞吐量  衡量系统性能的一个重要标准  事务通常是指一些列相关操作 而不是某一个  例如对于商城项目而言  关注的TPS就是每秒能完成多少笔交易**

**RPS 每秒请求数  RPS模式适合作为容量规划和作为限流管控的重要参考依据**

**RT  响应时间  指业务从客户端发起到接收的时间**

## 指标间的区别和关系

### QPS 和 RPS

QPS主要是数据库的查询频率，API接口的调用频率  ； RPS 主要是HTTP请求的频率，包括了POST 请求 GET请求等各种方式的请求  两者之间没有明确的频率比较 因为一个请求可以能在业务代码中会多次操作数据库，也有可能完全不操作

### TPS 和 QPS

- **TPS** 关注的是整个业务流程的完成情况，例如完成一笔订单交易。
- **QPS** 更加细化，专注于单个数据库查询或 API 调用的速度。一个TPS可能包含多个QPS

### TPS 和 RPS

- **TPS** 关注的是业务层面的事务处理速度。
- **RPS** 则是针对 HTTP 协议的请求处理速度，通常比 TPS 更加频繁。

### VU和TPS

> **公式描述：TPS = VU/RT，（RT单位：秒）。**

举例说明：假如1个虚拟用户在 1 秒内完成 1 笔事务，那么 TPS 明显就是1。如果某笔业务响应时间是1 ms，那么1个虚拟用户在1s内能完成1000笔事务，TPS就是1000 了；如果某笔业务响应时间是 1s，那么 1 个虚拟用户在1s内只能完成1笔事务，要想达到1000 TPS，就需要1000个虚拟用户。因此可以说1个虚拟用户可以产生1000 TPS，1000个虚拟用户也可以产生1000 TPS，无非是看响应时间快慢。

## JMeter压测KV相关接口

下载Jmeter  然后打开 准备测试

## （1）添加线程组

作用：**模拟用户行为。线程组用于模拟多个用户同时访问服务器/应用程序，一个线程代表一个VU  ； 控制并发数，线程组允许指定同时运行的线程数，即虚拟用户的数量，利于模拟不同级别的并发负载，从而评估系统稳定性  ；  设置循环次数，设置采样器的执行次数，这意味着每个虚拟用户将按照设定的次数进行重复请求，利于测试长时间压测评估**

![image-20241003141031499](../../ZealSingerBook/img/image-20241003141031499.png)

然后创建采样器发送HTTP请求

![image-20241003141145262](../../ZealSingerBook/img/image-20241003141145262.png)

按照需求进行一定的填写即可

![image-20241003141304982](../../ZealSingerBook/img/image-20241003141304982.png)

可以添加一定的监听器 例如结果树  方便后续查看和评测

![image-20241003141405102](../../ZealSingerBook/img/image-20241003141405102.png)

## （2）POST请求

压测新增笔记的接口 也就是压测POST请求 所以我们先基础配置如下

![image-20241003142335447](../../ZealSingerBook/img/image-20241003142335447.png)

然后我们POST请求自然还是需要请求体的 所以我们需要加入请求体  为HTTP请求配置元件  HTTP信息头管理器

首先设置数据格式为application/json

![image-20241003143017652](../../ZealSingerBook/img/image-20241003143017652.png)

然后添加请求体内容

![image-20241003143332615](../../ZealSingerBook/img/image-20241003143332615.png)

准备好后我们可以点击执行先测试一下  响应没问题  数据库中也新增了  说明请求成功发送

![image-20241003143454024](../../ZealSingerBook/img/image-20241003143454024.png)

![image-20241003143519553](../../ZealSingerBook/img/image-20241003143519553.png)

## （3）正式压测新增笔记接口

既然压测 那么需要测试并发承受  我们修改一下线程组的设置

设置线程数为100  循环次数为永远

![image-20241003143653405](../../ZealSingerBook/img/image-20241003143653405.png)

然后添加一个汇总报告  

![image-20241003143742670](../../ZealSingerBook/img/image-20241003143742670.png)

然后就可以开始测试执行  可以多测试一点时间

可以看到汇总报告里面 我们测试了  两分半  

**一共160w的测试样本  平均时长为8ms 最大值为350ms 无异常  吞吐量为11602 也就是说每秒11602个事务  我们Cassandra安装的设备配置如下5G运行内存  2处理器 16核** 

![image-20241003150807964](../../ZealSingerBook/img/image-20241003150807964.png)

![image-20241003151054469](../../ZealSingerBook/img/image-20241003151054469.png)

## （4）正式压测查询笔记接口

同样的操作，我们压测一手查询接口

同样是测试两分半  可以看到这次只有110W的样本测试数据  要知道 我们刚刚通过了压测新增接口 相当于给数据库已经添加了160W的数据了  在这种情况下 我们测试接口**平均响应时间为10ms 出现了异常值  吞吐量为4896**

![image-20241003151856160](../../ZealSingerBook/img/image-20241003151856160.png)

## （5）线程中传递Token

有些接口的测试是需要传递token的 那么这种该如何实现呢？

方法一自然是写死一个token  但是这种就是模拟一个用户  如果我们需要动态传递 Token 令牌呢？举个栗子，比如想要测试用户退出接口，流程如下，需要先请求登录接口，从返参中获取 Token 令牌，然后请求用户登出接口时，再设置到请求头中 这个就需要用到动态传递了

我们首先来配置一个登录接口的压测  先确保登陆逻辑成功

![image-20241003153411249](../../ZealSingerBook/img/image-20241003153411249.png)

然后我们给其添加一个后置处理器 - json提取器

- `Names of created variables` : 创建一个变量名，这里命名为 `token`;
- `JSON Path expressions` : 提取的表达式，`$` 代表返参，根据接口的实际返参格式，如下，令牌值在 `data` 节点下，故而填写 `$.data` 

![image-20241003153523057](../../ZealSingerBook/img/image-20241003153523057.png)

然后debug测试后置处理器是否配置成功

![image-20241003153609024](../../ZealSingerBook/img/image-20241003153609024.png)

添加后置处理器debug之后 再次运行线程组  可以看到  现在的返回结果左边比之前之前的多了小箭头 点击打开后 可以看到下面多了jmeter加入的信息  里面就有**我们配置的token这一栏**

![image-20241003153710644](../../ZealSingerBook/img/image-20241003153710644.png)

我们拿到token之后 需要借助脚本传输给下一个线程组

![image-20241003153954365](../../ZealSingerBook/img/image-20241003153954365.png)

脚本代码可以直接用Jmeter的工具脚本生成

![image-20241003154026850](../../ZealSingerBook/img/image-20241003154026850.png)

接下来 就可以准备写退出登录的压测

先正常写一个退出登录的测试 确保没问题

![image-20241003154520536](../../ZealSingerBook/img/image-20241003154520536.png)

然后我们还要在这个头中 再次使用函数助手 帮我们拿到那个上个线程组的token

![image-20241003154752294](../../ZealSingerBook/img/image-20241003154752294.png)

![image-20241003154810082](../../ZealSingerBook/img/image-20241003154810082.png)

这样子之后就能实现拿到Token了  

但是还需要注意一个点  我们同时进行登录压测和退出登录压测 两个测试是并行的  就可能出现登录token没拿到 退出先执行导致token为null  所以我们需要严格控制执行先后顺序  在测试计划中进行勾选就行了  （上面两个线程测试可以先禁用 防止干扰）

![image-20241003155039705](../../ZealSingerBook/img/image-20241003155039705.png)

都准备完成之后 就可以进行测试 从结果可以看到 高并发登录还是需要改善的

## 分库分表

为了高可用 自然需要分库分表 单库单表存在以下缺点：

1：单点故障

2：单机性能瓶颈

3：备份和恢复麻烦  单机存储 数据都堆积在一个库中 自然数据量会很大 对于大数据量的备份工作和恢复等运维工作难度很大

4：并发场景下各种操作的锁竞争  导致逻辑问题和性能降低

所以 为了解决上述问题 就出现了分库分表 将数据均匀的分布到多个库表中  从而解决上面的问题

![image-20241003160422762](../../ZealSingerBook/img/image-20241003160422762.png)

分库分表中 主键ID的生成就会出现问题  在单库单表中 主键可以通过AUTO_INCREMENT自动生成递增管理  但是分库分表的条件下因为数据存放在不同的库表  **需要注意ID不能重复，具有可拓展性，并且ID生成机制不能成为性能瓶颈**

### 分布式ID常见方案

#### UUID

直接使用UUID作为主键，优点就是方便简单，因为UUID的生成无需其他中间件 工具 也没网络和磁盘IO的消耗

但是同时也存在一个问题

1：过长 不宜存储 16 字节 128 位，通常以 36 长度的字符串表示。在海量数据场景下，会消耗较大的存储空间  

2：难以做到单调递增   

3：基于 MAC 地址生成 UUID 的算法可能会造成 MAC 地址泄露，这个漏洞曾被用于寻找梅丽莎病毒的制作者位置

4：UUID过长作为主键，在MySQL文档中是明确说明了过长的主键不适合

#### 基于DB的自增ID

单独创建一张表 使用自增字段来生成ID 然后再存入到业务表中

优点：实现简单  ID单调递增

缺点：强依赖DB 如果这个DB不可用 导致整个需要ID的业务都不能正常使用，搭配主从复制可以减缓但是数据一致性又会出现一定问题

#### 基于分布式协调服务

利用zookeeper  Etcd等分布式协调服务 可以做到ID协调分配 但是映入了中间件 增加了系统复杂性

#### 基于分布式缓存

利用Redis的INCRBY，类似我们的zealsingerbookId的生成一样，利用缓存的自增，实现ID的生成

#### 基于雪花算法

雪花算法是推特推出的，结合了时间戳，接卸ID ，序列号生成的64位ID，结构如下

通过一时间 统一机器设备下 能生成 2的12次方的数据 可以说很好了

![image-20241003172826896](../../ZealSingerBook/img/image-20241003172826896.png)

- **1bit**: 符号位（标识正负），不作使用，始终为 0，代表生成的 ID 为正数。
- **41-bit 时间戳**: 一共 41 位，用来表示时间戳，单位是毫秒，可以支撑 2 ^41 毫秒（约 69 年）
- **datacenter id + worker id (10 bits)**: 一般来说，前 5 位表示机房 ID，后 5 位表示机器 ID（项目中可以根据实际需求来调整）。这样就可以区分不同集群/机房的节点。
- **12-bit 序列号**: 一共 12 位，用来表示序列号。 序列号为自增值，代表单台机器每毫秒能够产生的最大 ID 数(2^12 = 4096),也就是说单台机器每毫秒最多可以生成 4096 个 唯一 ID。理论上 snowflake 方案的QPS约为 409.6w /s，这种分配方式可以保证在任何一个 IDC 的任何一台机器在任意毫秒内生成的 ID 都是不同的。

- 优点：
  - 毫秒数在高位，自增序列在低位，整个ID都是趋势递增的。
  - 不依赖数据库等第三方系统，以服务的方式部署，稳定性更高，生成ID的性能也是非常高的。
  - 可以根据自身业务特性分配bit位，非常灵活。
- 缺点：
  - 强依赖机器时钟，如果机器上时钟回拨，会导致发号重复或者服务会处于不可用状态。

#### 其他开源框架

##### 百度UidGenerator

GitHub 地址：https://github.com/baidu/uid-generator

UidGenerator是Java实现的基于雪花算法的一种唯一ID生成器，支持自定义workerId位数和初始化策略，从而使用于docker等虚拟环境下的实例自动重启和漂移等场景，借助未来时间来解决雪花算法的并发限制，采用RingBuffer来缓存已经生成的UID，并行化UID的生成和消费，同时对CacheLine进行补齐，避免了RingBuffer带来的硬件伪共享的问题，单机QPS可达600w

##### 滴滴TinyID

GitHub 地址：https://github.com/didi/tinyid/wiki

Java实现的分布式ID系统 基于数据库号段算法实现，相关文档可参考[Leaf——美团点评分布式ID生成系统 - 美团技术团队 (meituan.com)](https://tech.meituan.com/2017/04/21/mt-leaf.html)和[Tinyid原理介绍 · didi/tinyid Wiki (github.com)](https://github.com/didi/tinyid/wiki/tinyid原理介绍)  对雪花算法和自增ID都进行了一定的拓展 支持多DB 同时i提供了java-sdk使得ID生成本地化  更好的性能和可用性

##### 美团Leaf

GitHub 地址：https://github.com/Meituan-Dianping/Leaf

参考文档[Leaf——美团点评分布式ID生成系统 - 美团技术团队 (meituan.com)](https://tech.meituan.com/2017/04/21/mt-leaf.html)

最终 我们本项目选用美团的Leaf作为分布式ID的技术选型

# 美团Leaf介绍和使用

## 介绍

背景：美团对于金融 支付 订单 骑手  酒店 餐饮数据涉及面巨大，数据分库分表后的需要一个唯一ID作为数据的标识，显然 数据库的自增ID已经满足不了需求  所以自研了Leaf，QPS接近5w/s

Leaf有两种生成ID的模式  **分别是Leaf-segment （对自增ID的方式的优化）和Leaf-snowflake（对雪花算法的优化）两种方式**

Leaf-segment在原有的数据库递增主键的基础上，采用**号段获取的方式**  mysql自带的自增ID，每次获取自增ID都需要读取一次数据库，对数据库造成更多的压力，为了改善这个，采取Proxy server批量获取，也就是每次分配不只是拿一个号，而是基于每个服务拿取一个范围内的号段segment，范围区域大小取决于step字段的数值，用完之后再去到数据库获取新的号段，从而减轻数据库压力

Leaf-snowflake在原有雪花算法的基础上，依旧是采用 1+41+10+12的方式（标识位+时间戳+工作ID（机房ID+机械ID）+序号ID） ，对于工作ID的分配，当服务集群数量比较少的时候，完全可以采用手动配置，当数量比较多的时候，手动配置成本过高，可以采用zookeeper持久顺序节点的特性对snowflake节点配置workerID，Leaf-snowflake按照如下几个步骤启动：

1：启动Leaf-snowflake，连接zookeeper，在leaf_forever父节点下检查是否已经注册过（是否有该顺序子节点）

2：如果注册过的直接拿取自己的workerID（zk自动生成的int类型的ID），启动服务

3：如果没有注册过，就在该父节点下创建一个持久顺序节点，创建成功后取回顺序号当作自己的workerID启动服务

## 使用

到github上下载源码

![image-20241003195658880](../../ZealSingerBook/img/image-20241003195658880.png)

准备好数据库

biz_tag  标识业务，用于业务隔离

max_id 对于该业务下分配的最大ID

step  号段范围 每次分配分配多少号段

如下表创建完成后，再插入一条业务标识为 `leaf-segment-test` 的记录，`step` 为 2000，表示号段长度为 2000， 即每次生成 2000 个 ID 。

```sql
CREATE TABLE `leaf_alloc` (
  `biz_tag` varchar(128)  NOT NULL DEFAULT '',
  `max_id` bigint(20) NOT NULL DEFAULT '1',
  `step` int(11) NOT NULL,
  `description` varchar(256)  DEFAULT NULL,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB;

insert into leaf_alloc(biz_tag, max_id, step, description) values('leaf-segment-test', 1, 2000, 'Test leaf Segment Mode Get Id')

```

![image-20241003195717507](../../ZealSingerBook/img/image-20241003195717507.png)

打开leaf源码 修改起数据库的配置信息

![image-20241003200043462](../../ZealSingerBook/img/image-20241003200043462.png)

然后注意一下数据库的配置和依赖 我们主要用MySQL8.0  所以依赖和配置需要修改一下

```java 
// Config dataSource
dataSource = new DruidDataSource();   dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
// 省略...
dataSource.setValidationQuery("select 1");
dataSource.init();

```

![image-20241003201034892](../../ZealSingerBook/img/image-20241003201034892.png)

![image-20241003201026908](../../ZealSingerBook/img/image-20241003201026908.png)

修改之后 就可以启动服务（可能还需要注意一下Java版本的问题 可能存在jdk 9 之后的模块化问题）

可以看到 controller中就存在两个生成UID的方式 我们可以用APIfox测试一下

### Leaf-segment

```Java
@RequestMapping(value = "/api/segment/get/{key}")
key标识业务  也就是我们之前创建的表中的biz_tag字段
```

![image-20241003201138770](../../ZealSingerBook/img/image-20241003201138770.png)

我们根据数据库中的测试信息 key = leaf-segment-test  可以看到返回了一个ID  继续刷新 就会一直往下递增

![image-20241003201452653](../../ZealSingerBook/img/image-20241003201452653.png)

![image-20241003201518936](../../ZealSingerBook/img/image-20241003201518936.png)

同时 访问  可以监控一些已经获取到的数据

```
http://localhost:8080/cache
```

![image-20241003201611880](../../ZealSingerBook/img/image-20241003201611880.png)

### Leaf-snowflake

leaf-snowflake是基于雪花算法的，对于工作号的生成还涉及到zookeeper的持久顺序节点，所以访问之前，我们需要搭一个zookeeper

```
docker pull zookeeper:3.5.6
docker run -d --name zookeeper -p 2181:2181 -e TZ="Asia/Shanghai" -v /Docker/zookeeper/data:/data -v /Docker/zookeeper/conf:/conf zookeeper:3.5.6
```

容器启动成功后 进入容器

```
docker exec -it zookeeper bash
```

使用zookeeper的命令行界面CLI  可以直接和zookeeper交互

```
./bin/zkCli.sh
```

![image-20241003203350164](../../ZealSingerBook/img/image-20241003203350164.png)

#### ZK的基本指令

```
ls 路径
列出路径下的所有子节点

例如  列出根目录下的所有子节点  ls /
```

![image-20241003203516464](../../ZealSingerBook/img/image-20241003203516464.png)

```
create /节点名字 "初始数据"
创建一个节点并带有初始数据


create /myNode "专栏"
以上命令，将创建一个名为 /myNode 的节点，并初始化其数据为 "专栏"
```

![image-20241003203712455](../../ZealSingerBook/img/image-20241003203712455.png)

```
get  /节点名
获取指定节点名的数据和状态信息
```

![image-20241003203756606](../../ZealSingerBook/img/image-20241003203756606.png)

```
set /节点名字  内容
设置和更新节点数据
set /zealsinger "test2"  将原本的test更新为test2
```

![image-20241003203910347](../../ZealSingerBook/img/image-20241003203910347.png)

```
delete /节点名
删除指定节点
```

![image-20241003204006937](../../ZealSingerBook/img/image-20241003204006937.png)

```
quit  退出zkCLi界面
```

------

熟悉之后 在leaf-server的配置文件中添加zookeeper的相关配置信息

看到如下输出 说明leaf-snowflake初始化成功

![image-20241003204339031](../../ZealSingerBook/img/image-20241003204339031.png)

然后直接访问controller中关于leaf-snowflake的接口

（key其实随便填就行 在leaf-snowflake的底层中 其实这个key没有任何用 所以随便就可以）

```
/api/snowflake/get/{key}
```

跟踪controller到服务的实现类 可以看到  传入的参数key在server中根本就没有被使用  所以ID的生成和key没有关系

![image-20241003204610587](../../ZealSingerBook/img/image-20241003204610587.png)

访问对应的controller接口后可以看到返回了对应的UID

![image-20241003204726889](../../ZealSingerBook/img/image-20241003204726889.png)

到目前为止 我们成功启动了Leaf并且测试成功  接下来 我们就可以将其集成到我们的项目中并且使用

# zealsingerbook集成Leaf

创建模块  专门用于ID生成

![image-20241003205658098](../../ZealSingerBook/img/image-20241003205658098.png)

删除src目录 只保留pom.xml依赖管理文件

然后创建api和biz子模块![image-20241003210250004](../../ZealSingerBook/img/image-20241003210250004.png)

api模块加入common基础依赖和openFeign和负载均衡依赖

![image-20241003210310871](../../ZealSingerBook/img/image-20241003210310871.png)

biz模块加入common基础模块和springboot基础依赖和服务注册和发现模块

![image-20241003210400900](../../ZealSingerBook/img/image-20241003210400900.png)
