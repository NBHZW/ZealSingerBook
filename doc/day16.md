# ES搜素模块

搜素功能我们自然还是使用ES，目前市场上比较好的也都还是用的ES

ES的基础基础知识和安装使用查看以前的SpringCloud笔记文档，这里直接开始zealsingerbook的业务分析

## 搜索业务需求

可以看到 如果我们在搜索栏目上进行关键字搜索之后，首先是**“全部”标签内容** 

其返回的内容有

- **封面图**；
- **笔记标题**；
- **被点赞量**；
- **发布者用户头像**；
- **发布者昵称**；
- **笔记最新更新时间**；

![image-20241204142404121](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204142404121.png)

除此之外，我们旁边还有个筛选的功能，所具备的筛选条件是

- **最新**（按发布时间降序）；
- **最多点赞**（按点赞量降序）；
- **最多评论**（按评论量降序）；
- **最多收藏**（按收藏量降序）；
- **笔记类型**；
- **发布时间**；

![image-20241204142807943](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204142807943.png)

## 创建笔记索引

根据上面的搜素展示和条件，我们需要对应的创建ES的索引

```json
# 新增笔记索引
PUT /note
{
	"settings": {
		"number_of_shards": 1,
		"number_of_replicas": 1
	},
	"mappings": {
	  "properties": {
	    "id": {"type": "long"},
	    "cover": {"type": "keyword"},
	    "title": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "topic": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "creator_nickname": {"type": "keyword"},
	    "creator_avatar": {"type": "keyword"},
	    "type": {"type": "integer"},
	    "create_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "update_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "like_total": {"type": "integer"},
	    "collect_total": {"type": "integer"},
	    "comment_total": {"type": "integer"}
	  }
	}
}

```

```
"settings": {
		"number_of_shards": 1,
		"number_of_replicas": 1
	}

本地开发 所以只要生成一个分片和一个副本即可
```

```
"mappings": {
	  "properties": {
	    "id": {"type": "long"},
	    "cover": {"type": "keyword"},
	    "title": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "topic": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "creator_nickname": {"type": "keyword"},
	    "creator_avatar": {"type": "keyword"},
	    "type": {"type": "integer"},
	    "create_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "update_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "like_total": {"type": "integer"},
	    "collect_total": {"type": "integer"},
	    "comment_total": {"type": "integer"}
	  }
	}
	
id:对应noteId,也就是leaf生成的分布式ID

cover：封面，对应的note数据库中的ImgUrl中的第一个图片，这个是URL，自然是不能分词的，所以类型的KeyWord

title: 标题，字段类型为text，存储笔记标题，可以分词并且需要被搜素，使用ik分词器
（
一般使用ik_max_word作为索引和分词，这样能保证搜素的最大效率和覆盖
使用ik_smart作为搜素，提高文档的查准确率
使用这两个一般能试应绝大多数的中文搜索场景
）

topic:text，话题，一样采用分词，也可以作为查询搜索条件

creator_nickname：发布者昵称，字段为keyword 不参与分词

type:笔记类型，区分是文本笔记还是视频

create_time 和 update_time：更新时间 字段类型为 date，用于存储笔记的创建时间和更新时间，格式为 yyyy-MM-dd HH:mm:ss。这允许对时间进行范围查询和排序
like_total：字段类型为 integer，用于存储笔记的点赞总数。

collect_total：字段类型为 integer，用于存储笔记的收藏总数。

comment_total：字段类型为 integer，用于存储笔记的评论总数
```

添加测试数据

```json
# 添加文档1
PUT /note/_doc/1824367890233557066
{
  "id": 1824367890233557066,
  "title": "【最美壁纸】宝子们，来领取今天的壁纸啦❤️❤️❤️",
  "cover": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "topic": "无水印壁纸",
  "creator_avatar": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "creator_nickname": "zealsingerbook101",
  "type": 0,
  "create_time": "2024-09-01 16:49:35",
  "update_time": "2024-09-02 15:22:55",
  "like_total": 9981,
  "collect_total": 6908,
  "comment_total": 678
}

# 添加文档2
PUT /note/_doc/1824370663234732114
{
  "id": 1824370663234732114,
  "title": "治愈系壁纸来啦！！🐾",
  "cover": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "topic": "",
  "creator_avatar": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "creator_nickname": "zealsingerbook101",
  "type": 0,
  "create_time": "2024-08-16 16:49:35",
  "update_time": "2024-09-02 15:22:55",
  "like_total": 406671,
  "collect_total": 20981,
  "comment_total": 2348
}

# 添加文档3
PUT /note/_doc/1824370663356366868
{
  "id": 1824370663356366868,
  "title": "✨新的微信背景图来喽！✨",
  "cover": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "topic": "",
  "creator_avatar": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "creator_nickname": "zealsingerbook101",
  "type": 0,
  "create_time": "2024-08-16 16:49:35",
  "update_time": "2024-09-02 15:22:55",
  "like_total": 32109,
  "collect_total": 2946,
  "comment_total": 3053
}

```

测试查询和数据是否添加完毕

![image-20241204145348892](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204145348892.png)

### Score打分

在ES中，返回数据的时候，顺序是按照score分数的，**ES内部有一个相关性算分 机制**，文档会根据搜索的内容的相关程度进行打分计算，按照分数降序排序

也可以变相的理解，score越高说明相关性越强，也就会排在前面

![image-20241204150311973](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204150311973.png)

默认的**算法机制是TF（词频）=词条出现次数/文档中词条的总条数**

该算法存在一定的局限性，当搜索的词条在所有文档中都存在的时候，该词条的分数参考意义不，所以进行了修改出现了**TF-IDF算法**

![image-20231227152603905](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20231227152603905.png)

通过**TF-IDF计算公式可以看到，出现该词条的文档总数越多那么IDF就会越小，就会导致score越小，反之越大，简单而言就是词频越高，分数越低，词频低的分数越高**

可以看到，上述两个算法都会受到词频的影响，从而干扰分数，随着词频越来越大，分数也会越来越大，为了使得分数平缓一点，出现了第三种算法**BM25算法**

![image-20241204153512242](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204153512242.png)

**但是可以看到，无论是上面哪种算法，都不太满足我们的算法需求，我们查看一下上面的查询结果，可以看到，文档关键词都匹配到了“壁纸”，但是一般排在前面的被点赞量，被收藏量，评论较多的笔记，更容易被推荐到前面，所以我们需要自定义的算分算法，这里就需要使用到ES提供的function_score**

### function_score

格式举例

```json
GET /indexName/_search
{
	"query":{
		"function_score": {
// 原始的 match查询等查询条件的书写		
			"query": {"match": {"all":"外滩"}}, 
			"functions":[
				{
//filter 过滤作用 符合条件的文档才会被重新算分  这里是选择 id为1的
					"filter":{"term":{"id":"1"}},
//接着是写算分函数  算分函数的结果统称为 function score 将来会和query score运算 得到新的算分结果 
正常的格式应该是   函数名:结果计算规则
下面的 weight就是一种算分函数  将后面的常量值作为函数结果
除此之外还有
field_value_factor:用文档中的某个字段作为函数结果
random_score:随机生成
script_score:自定义计算公式作为函数结果
					"weight": 10
				}
			],
//得到了function_score 那么如何和query scoer计算呢  
下面这行设置boost_mode的属性值就是定义计算方式
multiply  默认算法  两者相乘
replace  替换 用function score代替 query score
sum  求和			max  求最大值   min  求最小    avg  求平均
			"boost_mode": "multiply"
		}
	}
}
```

那么根据我们的小红书需求，也就是自定义算法

```json
# 使用 function_score 自定义调整文档得分
POST /note/_search
{
  "query": {
    "function_score": {
      "query": {
        "multi_match": {
          "query": "壁纸",
          "fields": ["title^2", "topic"]
        }
      },
      "functions": [
        {
          "field_value_factor": {
            "field": "like_total",
            "factor": 0.5,
            "modifier": "sqrt",
            "missing": 0
          }
        },
        {
          "field_value_factor": {
            "field": "collect_total",
            "factor": 0.3,
            "modifier": "sqrt",
            "missing": 0
          }
        },
        {
          "field_value_factor": {
            "field": "comment_total",
            "factor": 0.2,
            "modifier": "sqrt",
            "missing": 0
          }
        }
      ],
      "score_mode": "sum",
      "boost_mode": "sum"
    }
  },
  "sort": [
    {
      "_score": {
        "order": "desc"
      }
    }
  ],
  "from": 0,
  "size": 10
}

```

这里标识正常的关键字查询，查询关键字“壁纸”，查询的字段 title 和 topic  其中  **title^2标识title字段权重未两倍，会被topic更重要**

```JSON
{
  "query": "壁纸",
  "fields": ["title^2", "topic"]
}

```

这里定义function函数计算逻辑

字段->like_total   

评分影响->factor = 0.5 标识该字段占比50%

对字段进行数学变化->modifier = sqrt  表示这里使用sqrt平方根，可以减少最大值对评分的影响，平滑评分分布

字段缺失的时候默认取值->missing = 0 表示字段缺失的时候默认为0

同理，下面的其他几个function也是一样的道理

```json
"functions": [
        {
          "field_value_factor": {
            "field": "like_total",
            "factor": 0.5,
            "modifier": "sqrt",
            "missing": 0
          }
        },
        {
          "field_value_factor": {
            "field": "collect_total",
            "factor": 0.3,
            "modifier": "sqrt",
            "missing": 0
          }
        },
        {
          "field_value_factor": {
            "field": "comment_total",
            "factor": 0.2,
            "modifier": "sqrt",
            "missing": 0
          }
        }
      ],
```

表示了最终的算分规则

score_mode 表示了定义的自定义函数之间的算法原则，也就是functions中的三个函数计算出来的结果之间的计算规则,这里是sum表示三个结果相加

boost_mode 表示了最终的文档得分如何结合查询匹配得分, 值`"sum"` 表示将关键词匹配得分与自定义函数得分相加（也就意味着关键字匹配得到的分数不会被忽略，也会加入到最终算分中）

```json
"score_mode": "sum",
"boost_mode": "sum"
```

分数排序展示规则  降序排序

```json
{
  "_score": {
    "order": "desc"
  }
}

```

测试一下 可以看到本次查询结果如下  和之前直接查询不一样了

![image-20241204155218911](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204155218911.png)

## 创建用户索引

默认是查询笔记，自然我们还有查询用户，这里需要对用户进行单独的索引创建和查询了

查询结果展示的数据有

- **用户头像**；
- **用户昵称**；
- **小红书号 ID**；
- **发布的笔记数**；
- **粉丝数**；

![image-20241204155305611](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241204155305611.png)

对应的创建用户查找的索引

```json
PUT /user
{
	"settings": {
		"number_of_shards": 1,
		"number_of_replicas": 1
	},
	"mappings": {
	  "properties": {
	    "id": {"type": "long"},
	    "nickname": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "avatar": {"type": "keyword"},
	    "xiaohashu_id": {"type": "keyword"},
	    "note_total": {"type": "integer"},
	    "fans_total": {"type": "integer"}
	  }
	}
}
```

- `id` : 用户 ID，数据类型为 `long`;
- `nickname` : 用户昵称。昵称需要参与分词搜索，需要指定创建倒排索引所使用的分词器为 `ik_max_word`，以及搜索时使用分词器为 `ik_smart`；
- `avatar` : 用户头像，类型为 `keyword` , 不参与分词；
- `xiaohashu_id` : 小哈书 ID, 类型为 `keyword` , 不参与分词；
- `note_total` : 发布的笔记数，类型为 `integer`;
- `fans_total` : 粉丝总数，类型为 `integer`;

测试数据

```json
# 添加文档1
PUT /user/_doc/27
{
  "id": 27,
  "nickname": "zealsinger",
  "avatar": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "xiaohashu_id": "10100678",
  "note_total": 28,
  "fans_total": 999999
}

# 添加文档2
PUT /user/_doc/28
{
  "id": 28,
  "nickname": "zealsinger",
  "avatar": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "xiaohashu_id": "10100679",
  "note_total": 1,
  "fans_total": 6798
}

# 添加文档3
PUT /user/_doc/29
{
  "id": 29,
  "nickname": "犬二哈",
  "avatar": "http://116.62.199.48:9000/weblog/c89cc6b66f0341c0b7854771ae063eac.jpg",
  "xiaohashu_id": "zealsinger",
  "note_total": 66,
  "fans_total": 1576
}

```

用户查询我们一般就使用粉丝多的优先被推荐，所以这里使用的排序就直接指定排序字段即可

```json
GET /user/_search
{
  "query": {
    "match": {"nickname": "小哈"}
  },
    "sort": [
    {
      "fans_total": {
        "order": "desc"
      }
    }
  ],
  "from": 0,
  "size": 10
}
```

## 关键词高亮

关键词高亮的效果其实就是通过ES在查找结果的基础上加上前端的<strong>标签，ES中语法如下

```Json
GET /user/_search
{
  "query": {
    "multi_match": {
      "query": "quanxiaoha_xxx",
      "fields": ["nickname", "xiaohashu_id"]
    }
  },
  "sort": [
    {
      "fans_total": {
        "order": "desc"
      }
    }
  ],
  "from": 0,
  "size": 10,
  "highlight": { // 高亮语法
    "fields": {
      "nickname": { //字段名
        "pre_tags": ["<strong>"],  //前置内容
        "post_tags": ["</strong>"]  //后置内容
      }
    }
  }
}

```

## 用户搜索接口编写

那我们现在可以准备开始编写用户搜索的业务逻辑了

![image-20241211104045792](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241211104045792.png)

入参

```json
{
    "keyword": "quanxiaoha_xxx", // 搜索关键词
    "pageNo": 1 // 查询页码
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
			"userId": 100, // 用户ID
			"nickname": "犬小哈", // 昵称
			"avatar": "http://127.0.0.1:9000/xiaohashu/14d8b7c3adad49f5b81dfa68417c0ab3.jpg", // 头像
			"xiaohashuId": "quanxiaoha_xxx", // 小哈书ID
			"noteTotal": 0, // 笔记发布总数
			"fansTotal": 0 // 粉丝总数
		}
	],
	"pageNo": 1, // 当前页码
	"totalCount": 1, // 总文档数
	"pageSize": 10, // 每页展示文档数
	"totalPage": 1 // 总页数
}

```

那么对应的编写RequestVO和ResponseVO

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchUserReqVO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1; // 默认值为第一页

}


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchUserRspVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 小哈书ID
     */
    private String zealsingerBookId;

    /**
     * 笔记发布总数
     */
    private Integer noteTotal;

    /**
     * 粉丝总数
     */
    private String  fansTotal;

    /**
     * 昵称：关键词高亮
     */
    private String highlightNickname;

}

```

这里其实就是一个简单的查询ES接口的逻辑，唯一的难点就是对于ES的API的使用

操作ES需要使用ES提供的**RestHighLevelClient**  这个对象需要我们配置相关信息然后注册为Bean在我们的代码中使用

```java
@Configuration
public class ElasticsearchRestHighLevelClient {

    @Resource
    private ElasticsearchProperties elasticsearchProperties;

    private static final String COLON = ":";
    private static final String HTTP = "http";

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        String address = elasticsearchProperties.getAddress();

        // 按冒号 ： 分隔
        String[] addressArr = address.split(COLON);
        // IP 地址
        String host = addressArr[0];
        // 端口
        int port = Integer.parseInt(addressArr[1]);

        HttpHost httpHost = new HttpHost(host, port, HTTP);

        return new RestHighLevelClient(RestClient.builder(httpHost));
    }
}

其中  elaticsearchProperties 是自定义个一个链接ES的信息类  其实也就是包含了ES的地址  读取配置文件  写死其实也没太大的问题
public class ElasticsearchProperties {
    private String address;
}

```

得到RestHighLevelClient之后 就可以给ES发送请求从而操作ES

当然 为了方便操作索引，我们可以创建对应的索引的实体类

```Java
public class UserIndex {

    /**
     * 索引名称
     */
    public static final String NAME = "user";

    /**
     * 用户ID
     */
    public static final String FIELD_USER_ID = "id";

    /**
     * 昵称
     */
    public static final String FIELD_USER_NICKNAME = "nickname";

    /**
     * 头像
     */
    public static final String FIELD_USER_AVATAR = "avatar";

    /**
     * ZealsingerBookID
     */
    public static final String FIELD_USER_ZEALSINGER_BOOK_ID = "zealsinger_book_id";

    /**
     * 发布笔记总数
     */
    public static final String FIELD_USER_NOTE_TOTAL = "note_total";

    /**
     * 粉丝总数
     */
    public static final String FIELD_USER_FANS_TOTAL = "fans_total";

}
```



```Java
@Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO) {
        String keyword = searchUserReqVO.getKeyword();
        Integer pageNo = searchUserReqVO.getPageNo();
        // 构建查询请求
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);
        // 构建查询内容
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 构建 multi_match 查询，查询 nickname 和 zealsingerbook_id 字段
        sourceBuilder.query(QueryBuilders.multiMatchQuery(keyword,UserIndex.FIELD_USER_NICKNAME,UserIndex.FIELD_USER_ZEALSINGER_BOOK_ID));

        // 排序，按 fans_total 降序
        SortBuilder<?> sortBuilder = new FieldSortBuilder(UserIndex.FIELD_USER_FANS_TOTAL)
                .order(SortOrder.DESC);
        sourceBuilder.sort(sortBuilder);

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(UserIndex.FIELD_USER_NICKNAME)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchUserRspVO> searchUserRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);

            // 执行查询请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchUserRspVOS = new ArrayList<>();

            // 获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long userId = ((Number) sourceAsMap.get(UserIndex.FIELD_USER_ID)).longValue();
                String nickname = (String) sourceAsMap.get(UserIndex.FIELD_USER_NICKNAME);
                String avatar = (String) sourceAsMap.get(UserIndex.FIELD_USER_AVATAR);
                String zealsingerBookId = (String) sourceAsMap.get(UserIndex.FIELD_USER_ZEALSINGER_BOOK_ID);
                Integer noteTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_NOTE_TOTAL);
                Integer fansTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);
                // 获取高亮字段
                String highlightedNickname = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(UserIndex.FIELD_USER_NICKNAME)) {
                    highlightedNickname = hit.getHighlightFields().get(UserIndex.FIELD_USER_NICKNAME).fragments()[0].string();
                }

                // 构建 VO 实体类
                SearchUserRspVO searchUserRspVO = SearchUserRspVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .zealsingerBookId(zealsingerBookId)
                        .noteTotal(noteTotal)
                        .fansTotal(NumberUtils.formatNumberString(fansTotal))
                        .highlightNickname(highlightedNickname)
                        .build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchUserRspVOS, pageNo, total);
    }
```

## 内容搜索接口编写

同样的  我们先弄清楚入参和出参  以及创建对应的实体类

```
{
    "keyword": "壁纸", // 搜索关键词
    "pageNo": 1 // 查询页码
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchNoteReqVO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1; // 默认值为第一页

}
```

```
{
	"success": true,
	"message": null,
	"errorCode": null,
	"data": [
		{
			"noteId": 1862481582414102528, // 笔记 ID
			"cover": "http://116.62.199.48:9000/weblog/c58c6db953d24922803a65ca4f79a0a9.png", // 封面
			"title": "【最美壁纸】宝子们，来领取今天的壁纸啦❤️❤️❤️", // 标题
			"highlightTitle": "【最美<strong>壁纸</strong>】宝子们，来领取今天的<strong>壁纸</strong>啦❤️❤️❤️", // 高亮标题
			"avatar": "http://127.0.0.1:9000/xiaohashu/46f41e9a32564cd084f3b874a548c16f.jpg", // 发布者头像
			"nickname": "犬小哈呀", // 发布者昵称
			"updateTime": "2024-11-29", // 最后更新时间
			"likeTotal": "9981" // 点赞数
		}
	],
	"pageNo": 1, // 当前页码
	"totalCount": 1, // 总文档数
	"pageSize": 10, // 每页展示文档数
	"totalPage": 1 // 总页数
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchNoteRspVO {

    /**
     * 笔记ID
     */
    private Long noteId;

    /**
     * 封面
     */
    private String cover;

    /**
     * 标题
     */
    private String title;

    /**
     * 标题：关键词高亮
     */
    private String highlightTitle;

    /**
     * 发布者头像
     */
    private String avatar;

    /**
     * 发布者昵称
     */
    private String nickname;

    /**
     * 最后一次编辑时间
     */
    private LocalDateTime updateTime;

    /**
     * 被点赞总数
     */
    private String likeTotal;

}
```

然后一样的 就是编写ES的查询查询

### 创建笔记索引

```
# 新增笔记索引
PUT /note
{
	"settings": {
		"number_of_shards": 1,
		"number_of_replicas": 1
	},
	"mappings": {
	  "properties": {
	    "id": {"type": "long"},
	    "cover": {"type": "keyword"},
	    "title": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "topic": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
	    "creator_nickname": {"type": "keyword"},
	    "creator_avatar": {"type": "keyword"},
	    "type": {"type": "integer"},
	    "create_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "update_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "like_total": {"type": "integer"},
	    "collect_total": {"type": "integer"},
	    "comment_total": {"type": "integer"}
	  }
	}
}

```

### 笔记搜索ES语句

```Java
# 使用 function_score 自定义调整文档得分
POST /note/_search
{
  "query": {
    "function_score": {   // 自定义算分函数
      "query": {
        "multi_match": {
          "query": "壁纸",
          "fields": ["title^2", "topic"]  // title权重为2  topic为1
        }
      },
      "functions": [
        {
          "field_value_factor": {
            "field": "like_total",
            "factor": 0.5,
            "modifier": "sqrt",
            "missing": 0
          }
        },
        {
          "field_value_factor": {
            "field": "collect_total",
            "factor": 0.3,
            "modifier": "sqrt",
            "missing": 0
          }
        },
        {
          "field_value_factor": {
            "field": "comment_total",
            "factor": 0.2,
            "modifier": "sqrt",
            "missing": 0
          }
        }
      ],
      "score_mode": "sum",
      "boost_mode": "sum"
    }
  },
  "sort": [
    {
      "_score": {    // 按照score倒叙排序
        "order": "desc"
      }
    }
  ],
  "from": 0,
  "size": 10,
  "highlight": {
    "fields": {
      "title": {
        "pre_tags": ["<strong>"],
        "post_tags": ["</strong>"]
      }
    }
  }
}


```

### 编写搜索代码

也比较简单，主要是ES的相关API的不熟悉可能会导致编写有难度，对照API进行编写就会好很多

```Java
public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        String keyword = searchNoteReqVO.getKeyword();
        Integer pageNo = searchNoteReqVO.getPageNo();
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 创建查询条件
        QueryBuilder queryBuilder = QueryBuilders.multiMatchQuery(keyword)
                // 手动设置笔记标题的权重值为 2.0
                .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f)
                // 不设置，权重默认为 1.0
                .field(NoteIndex.FIELD_NOTE_TOPIC);


        // 创建算分机制
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                .factor(0.5f)
                                .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                .missing(0)),

                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                .factor(0.3f)
                                .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                .missing(0)),

                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                .factor(0.2f)
                                .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                .missing(0)),
        };
        FunctionScoreQueryBuilder  functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(queryBuilder,filterFunctionBuilders)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                .boostMode(CombineFunction.SUM);

        // 设置查询
        searchSourceBuilder.query(functionScoreQueryBuilder);

        // 创建排序
        searchSourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

        // 设置分页
        searchSourceBuilder.from((pageNo-1)*10).size(10);

        // 设置高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE);
        highlightBuilder.preTags("<strong>")
                .postTags("</strong>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 将构造的查询放入请求中
        searchRequest.source(searchSourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);
            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = new ArrayList<>();

            // 获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
                String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
                String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
                String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
                String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
                // 获取更新时间
                String updateTimeStr = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
                LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
                Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);

                // 获取高亮字段
                String highlightedTitle = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                    highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
                }

                // 构建 VO 实体类
                SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                        .noteId(noteId)
                        .cover(cover)
                        .title(title)
                        .highlightTitle(highlightedTitle)
                        .avatar(avatar)
                        .nickname(nickname)
                        .updateTime(updateTime)
                        .likeTotal(NumberUtils.formatNumberString(likeTotal))
                        .build();
                searchNoteRspVOS.add(searchNoteRspVO);
            }
        } catch (IOException e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);

    }
```

## 更多条件的搜索

### 排序依据和笔记类型

在搜索完毕后 可以进行筛选进行搜索

![image-20241214151007786](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214151007786.png)

那么这里 就需要先设置入参，在原来的基础上加上排序依据和笔记类型

![image-20241214152213039](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214152213039.png)

那么在之前的搜索逻辑中，我们是没有加其他的条件的，这里就需要在ES搜索的时候再加入其他的条件，我们先单独写过滤类型的ES语句如下

对应的 我们这里整个条件就是  关键字为keyword并且排序类型为X，笔记类型为Y的笔记  这相当于是一些列条件组合查询，这些条件之间的关系为And关系

在ES中组合查询使用的是BoolQuery

![image-20241214152619991](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214152619991.png)

```json
POST /note/_search
{
  "query": {
    "bool": {
      "must": [  // must是必须满足的条件
        {
          "multi_match": {
            "query": "壁纸",
            "fields": [
              "title^2",
              "topic"
            ]
          }
        }
      ],
      "filter": [   // filter也是必须满足的条件  但是不会参与算分
        {
          "term": {
            "type": 0
          }
        }
      ]
    }
  },
  "from": 0,
  "size": 10,
  "highlight": {
    "fields": {
      "title": {
        "pre_tags": [
          "<strong>"
        ],
        "post_tags": [
          "</strong>"
        ]
      }
    }
  }
}

```

对应的 代码中进行修改  将原本的searchQueryBuilder变为boolQueryBuilder，然后判断type类型添加对应的判断

![image-20241214154434042](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214154434042.png)

然后是排序方法的筛选，这个主要影响的也只有sort字段

```json
POST /note/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "壁纸",
            "fields": [
              "title^2.0",
              "topic^1.0"
            ]
          }
        }
      ]
    }
  },
  "sort": [
    {
      "create_time": {
        "order": "desc"
      }
    }
  ],
  "from": 0,
  "size": 10,
  "highlight": {
    "pre_tags": [
      "<strong>"
    ],
    "post_tags": [
      "</strong>"
    ],
    "fields": {
      "title": {}
    }
  }
}

```

修改排序的时候需要注意，sort为null即综合排序的时候，我们采用的是自定义算分排序，而sort不为null的时候才会条件排序，这两种情况不能同时有，所以我们需要if-else进行区分，当sort不为null的时候就需要自定义算分了

```Java
public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        String keyword = searchNoteReqVO.getKeyword();
        Integer pageNo = searchNoteReqVO.getPageNo();
        Integer type = searchNoteReqVO.getType();
        Integer sort = searchNoteReqVO.getSort();
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 创建查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(
                QueryBuilders.multiMatchQuery(keyword)
                        .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f) // 手动设置笔记标题的权重值为 2.0
                        .field(NoteIndex.FIELD_NOTE_TOPIC)); // 不设置，权重默认为 1.0

        if(type!=null){
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        // 创建排序
        if(sort!=null){
            SearchNoteSortEnum searchNoteSortEnum = SearchNoteSortEnum.valueOf(sort);
            switch (searchNoteSortEnum) {
                // 按笔记发布时间降序
                case LATEST -> searchSourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                // 按笔记点赞量降序
                case MOST_LIKE -> searchSourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                // 按评论量降序
                case MOST_COMMENT -> searchSourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                // 按收藏量降序
                case MOST_COLLECT -> searchSourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            searchSourceBuilder.query(boolQueryBuilder);
        }else{
            searchSourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));
            // 创建算分机制
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                    .factor(0.5f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)),

                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                    .factor(0.3f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)),

                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                    .factor(0.2f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)),
            };
            FunctionScoreQueryBuilder  functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder,filterFunctionBuilders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .boostMode(CombineFunction.SUM);

            // 设置查询
            searchSourceBuilder.query(functionScoreQueryBuilder);
        }
        searchSourceBuilder.query(boolQueryBuilder);
        // 设置分页
        searchSourceBuilder.from((pageNo-1)*10).size(10);

        // 设置高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE);
        highlightBuilder.preTags("<strong>")
                .postTags("</strong>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 将构造的查询放入请求中
        searchRequest.source(searchSourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);
            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = new ArrayList<>();

            // 获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
                String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
                String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
                String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
                String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
                // 获取更新时间
                String updateTimeStr = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
                LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
                Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);

                // 获取高亮字段
                String highlightedTitle = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                    highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
                }

                // 构建 VO 实体类
                SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                        .noteId(noteId)
                        .cover(cover)
                        .title(title)
                        .highlightTitle(highlightedTitle)
                        .avatar(avatar)
                        .nickname(nickname)
                        .updateTime(updateTime)
                        .likeTotal(NumberUtils.formatNumberString(likeTotal))
                        .build();
                searchNoteRspVOS.add(searchNoteRspVO);
            }
        } catch (IOException e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);

    }
```

### 按照发布时间进行过滤

下面这个需求 就是对搜索的时候  进行create_time进行过滤

![image-20241214160609754](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214160609754.png)

同样的 首先修改入参

![image-20241214160709453](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214160709453.png)

创建对应的枚举类

```Java
@AllArgsConstructor
@Getter
public enum SearchNoteTimeRangeEnum {
    // 一天内
    DAY(0),
    // 一周内
    WEEK(1),
    // 半年内
    HALF_YEAR(2),
    ;

    private final Integer code;

    /**
     * 根据类型 code 获取对应的枚举
     *
     * @param code
     * @return
     */
    public static SearchNoteTimeRangeEnum valueOf(Integer code) {
        for (SearchNoteTimeRangeEnum notePublishTimeRangeEnum : SearchNoteTimeRangeEnum.values()) {
            if (Objects.equals(code, notePublishTimeRangeEnum.getCode())) {
                return notePublishTimeRangeEnum;
            }
        }
        return null;
    }
}
```

按照时间范围查到 对于ES和数据库而言其实就是  创建时间>起始时间 <终止时间  所以实质上是一个范围查找  ES中范围查找为RangeQuery

```
// range查询是按照范围查询的  gte表示大于等于  lte表示小于等于
// 这两个条件不需要全部都写上去 可以只要一个
// gte 和 lte后面的e都是表示 equal等于的意思  所以取出e之后  gt和lt就是大于和小于

GET /indexName/_search
{
	"query":{
		"range":{
			"FIELD":{
				"gte": ....,
				"lte": ....
			}
		}
	}
}
```

![image-20231227130144160](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20231227130144160.png)

我们在ES中时间数据格式为 2024-09-02 15:22:55   属于LocalDateTime类型  所以我们在工具类中加一个将LocalDateTime类型转化为String类型的工具类

```
public static String localDateTime2String(LocalDateTime time) {
        return time.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
    }
```

![image-20241214172525789](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214172525789.png) 写到这里差不多了  自然我们还可以继续优化

目前小红书搜素的返回  

**1：不是统一的返回年份-月份-日 的格式  而是一个相对时间  几分钟前  几天前  然后再是确切的某个日子**

**2：按照最多评论排序的时候，评论量需要被返回**

**3：按照找最多收藏排序的时候，收藏量需要被返回**

![image-20241214172740397](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214172740397.png)

那么根据上述三个需求  适当的修改一些返回类型

```
public class SearchNoteRspVO {

    /**
     * 笔记ID
     */
    private Long noteId;

    /**
     * 封面
     */
    private String cover;

    /**
     * 标题
     */
    private String title;

    /**
     * 标题：关键词高亮
     */
    private String highlightTitle;

    /**
     * 发布者头像
     */
    private String avatar;

    /**
     * 发布者昵称
     */
    private String nickname;

    /**
     * 最后一次编辑时间
     */
    private LocalDateTime updateTime;

    /**
     * 被点赞总数
     */
    private String likeTotal;


    /**
     * 被评论数
     */
    private String commentTotal;

    /**
     * 被收藏数
     */
    private String collectTotal;

}
```



```Java
 /**
     * LocalDateTime 转友好的相对时间字符串
     * @param dateTime
     * @return
     */
    public static String formatRelativeTime(LocalDateTime dateTime) {
        // 当前时间
        LocalDateTime now = LocalDateTime.now();

        // 计算与当前时间的差距
        long daysDiff = ChronoUnit.DAYS.between(dateTime, now);
        long hoursDiff = ChronoUnit.HOURS.between(dateTime, now);
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);

        if (daysDiff < 1) {  // 如果是今天
            if (hoursDiff < 1) {  // 如果是几分钟前
                return minutesDiff + "分钟前";
            } else {  // 如果是几小时前
                return hoursDiff + "小时前";
            }
        } else if (daysDiff == 1) {  // 如果是昨天
            return "昨天 " + dateTime.format(DateConstants.DATE_FORMAT_H_M);
        } else if (daysDiff < 7) {  // 如果是最近一周
            return daysDiff + "天前";
        } else if (dateTime.getYear() == now.getYear()) {  // 如果是今年
            return dateTime.format(DateConstants.DATE_FORMAT_M_D);
        } else {  // 如果是去年或更早
            return dateTime.format(DateConstants.DATE_FORMAT_Y_M_D);
        }
    }

```

# 额外知识

ES可以动态添加自定义词库（ik的配置文件IKAnalyzer.cfg.xml中可以配置）和 增加同义词（例如 黑猴  黑悟空  wukong  可以被认定为同义词 这个需要在创建索引的时候就要被设计）

```json
# 新增笔记索引(测试同义词)
PUT /note2
{
	"settings": {
		"number_of_shards": 1,
		"number_of_replicas": 1,
		"analysis": {
		  "filter": {
		    "custom_synonym_filter": {
		      "type": "synonym",
		      "synonyms_path": "analysis-ik/custom/synonyms.txt"
		    }
		  },
		  "analyzer": {
		    "ik_synonym_smart": {
		      "type": "custom",
		      "tokenizer": "ik_smart",
		      "filter": ["custom_synonym_filter"]
		    },
		    "ik_synonym_max_word": {
		      "type": "custom",
		      "tokenizer": "ik_max_word",
		      "filter": ["custom_synonym_filter"]
		    }
		  }
		}
	},
	"mappings": {
	  "properties": {
	    "id": {"type": "long"},
	    "cover": {"type": "keyword"},
	    "title": {"type": "text", "analyzer": "ik_synonym_max_word", "search_analyzer": "ik_synonym_smart"},
	    "topic": {"type": "text", "analyzer": "ik_synonym_max_word", "search_analyzer": "ik_synonym_smart"},
	    "creator_nickname": {"type": "keyword"},
	    "creator_avatar": {"type": "keyword"},
	    "type": {"type": "integer"},
	    "create_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "update_time": {
	      "type": "date",
	      "format": "yyyy-MM-dd HH:mm:ss"
	    },
	    "like_total": {"type": "integer"},
	    "collect_total": {"type": "integer"},
	    "comment_total": {"type": "integer"}
	  }
	}
}

```

![image-20241214210214233](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241214210214233.png)

# Canal做ES的增量

Canall我们之前就有接触，其作用和相关原理不再过多阐述，直接开始配置

首先需要开启MySQL的binlog功能 然后创建一个专门给canal使用的账户密码

github上下载tg.zip压缩包然后解压

![image-20241215100424717](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241215100424717.png)

进入conf文件的example中修改配置文件

![image-20241215100601526](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241215100601526.png)

![image-20241215100923468](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241215100923468.png)

然后在外层conf文件下的那个配置文件中  这个属性可以注意一下

![image-20241215101030979](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241215101030979.png)

然后进入到bin目录下执行startup.sh文件即可

启动成功即可



然后SpringBoot整合Canal

配置文件

![image-20241215112403247](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241215112403247.png)

读取配置信息到实体类封装好

![image-20241215112422874](https://zealsinger-book-bucket.oss-cn-hangzhou.aliyuncs.com/img/image-20241215112422874.png)

注册CanalConnector的Bean对象

```Java
@Component
@Slf4j
public class CanalClient implements DisposableBean {
    @Resource
    private CanalProperties canalProperties;

    private CanalConnector canalConnector;

    /**
     * 实例化 Canal 链接对象
     * @return
     */
    @Bean
    public CanalConnector getCanalConnector() {
        // Canal 链接地址
        String address = canalProperties.getAddress();
        String[] addressArr = address.split(":");
        // IP 地址
        String host = addressArr[0];
        // 端口
        int port = Integer.parseInt(addressArr[1]);

        // 创建一个 CanalConnector 实例，连接到指定的 Canal 服务端
        canalConnector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(host, port),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword());

        // 连接到 Canal 服务端
        canalConnector.connect();
        // 订阅 Canal 中的数据变化，指定要监听的数据库和表（可以使用表名、数据库名的通配符）
        canalConnector.subscribe(canalProperties.getSubscribe());
        // 回滚 Canal 消费者的位点，回滚到上次提交的消费位置
        canalConnector.rollback();
        return canalConnector;
    }

    /**
     * 在 Spring 容器销毁时释放资源
     * @throws Exception
     */
    @Override
    public void destroy() throws Exception {
        if (Objects.nonNull(canalConnector)) {
            // 断开 canalConnector 与 Canal 服务的连接
            canalConnector.disconnect();
        }
    }
}
```

定义Canal的定时任务 读取记录

```Java
@Component
@Slf4j
public class CanalSchedule implements Runnable {
    @Resource
    private CanalConnector canalConnector;
    @Resource
    private CanalProperties canalProperties;

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private SelectMapper selectMapper;

    private final  String USER_TABLE_NAME = "t_user";

    private final  String NOTE_TABLE_NAME = "t_note";


    @Override
    @Scheduled(fixedDelay = 100)  // 每隔100ms执行一次
    public void run() {
        // 批次ID 初始化为-1 标识没开始或者未获取到数据
        long batchId = -1;
        try{
            // canal批量拉取消息 返回的数据量由batchSize控制 如果不足则会直接拉取已有的数据
            Message message= canalConnector.getWithoutAck(canalProperties.getBatchSize());
            // 获取当前拉取批次的ID
            batchId = message.getId();
            // 拉取数量
            List<CanalEntry.Entry> entryList = message.getEntries();
            int messageSize = entryList.size();
            if(batchId==-1 || messageSize==0 ){
                try{
                    // 没有拉取到数据 则直接睡眠1s 防止频繁拉取
                    TimeUnit.SECONDS.sleep(1);
                }catch (InterruptedException e){}
            }else{
                printEntry(entryList);
            }
            // 对当前消息进行ACK确认  标识该批次成功被消费
            canalConnector.ack(batchId);
        }catch (Exception e){
            log.error("消费 Canal 批次数据异常", e);
            // 如果出现异常，需要进行数据回滚，以便重新消费这批次的数据
            canalConnector.rollback(batchId);
        }
    }

    /**
     * 打印这一批次中的数据条目（和官方示例代码一致，后续小节中会自定义这块）
     * @param entrys
     */
    private void printEntry(List<CanalEntry.Entry> entrys) throws Exception {
        for (CanalEntry.Entry entry : entrys) {
            // 只关注行数据 不关注其他的事务和类型
            if(entry.getEntryType() == CanalEntry.EntryType.ROWDATA){
                // 获取行数据的事件类型
                CanalEntry.EventType eventType = entry.getHeader().getEventType();
                // 获取发生变化的数据库库名称
                String database = entry.getHeader().getSchemaName();
                // 获取表名
                String table = entry.getHeader().getTableName();

                // 解析出RowChange对象  该对象中包含了RowData行数据和事件相关的信息
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                // 遍历获取所有的行数据
                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    // 每行数据中所有列的最新数值
                    List<CanalEntry.Column> afterColumnsList = rowData.getAfterColumnsList();
                    // 解析为Map方便后续操作
                    // 将列数据解析为 Map，方便后续处理
                    Map<String, Object> columnMap = parseColumns2Map(afterColumnsList);

                    log.info("EventType: {}, Database: {}, Table: {}, Columns: {}", eventType, database, table, columnMap);

                    // TODO：处理事件任务
                    processEvent(columnMap,table,eventType);
                }
            }
        }
    }

    /**
     * 处理事件
     * @param columnMap
     * @param table
     * @param eventType
     */
    private void processEvent(Map<String, Object> columnMap, String table, CanalEntry.EventType eventType) throws Exception {
        // 根据不同的事件类型进行不同的方法处理
        switch (table){
            case USER_TABLE_NAME -> handleNoteEvent(columnMap,eventType);
            case NOTE_TABLE_NAME -> handleUserEvent(columnMap,eventType);
            default -> log.warn("Table: {} not support", table);
        }
    }

    /**
     * 处理用户表事件
     * @param columnMap
     * @param eventType
     */
    private void handleUserEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws Exception {
        // 获取用户 ID
        Long userId = Long.parseLong(columnMap.get("id").toString());

        // 不同的事件，处理逻辑不同
        switch (eventType) {
            case INSERT -> syncUserIndex(userId); // 记录新增事件
            case UPDATE -> { // 记录更新事件
                // 用户变更后的状态
                Integer status = Integer.parseInt(columnMap.get("status").toString());
                // 逻辑删除
                Integer isDeleted = Integer.parseInt(columnMap.get("is_deleted").toString());

                if (Objects.equals(status, StatusEnum.ENABLE.getValue())
                        && Objects.equals(isDeleted, 0)) { // 用户状态为已启用，并且未被逻辑删除，将状态重新更新上去即可
                    // 更新用户索引、笔记索引
                    syncNotesIndexAndUserIndex(userId);
                } else if (Objects.equals(status, StatusEnum.DISABLED.getValue()) // 用户状态为禁用  zha
                        || Objects.equals(isDeleted, 1)) { // 被逻辑删除
                    // TODO: 删除用户文档
                    deleteUserDocument(String.valueOf(userId));
                }
            }
            default -> log.warn("Unhandled event type for t_user: {}", eventType);
        }
    }
    /**
     * 删除指定 ID 的用户文档
     * @param documentId
     * @throws Exception
     */
    private void deleteUserDocument(String documentId) throws Exception {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(UserIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /**
     * 同步用户索引、笔记索引（可能是多条）
     * @param userId
     */
    private void syncNotesIndexAndUserIndex(Long userId) throws Exception {
        // TODO
        // 创建一个 BulkRequest
        BulkRequest bulkRequest = new BulkRequest();

        // 1. 用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);

        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : userResult) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将每个 IndexRequest 加入到 BulkRequest
            bulkRequest.add(indexRequest);
        }

        // 2. 笔记索引
        List<Map<String, Object>> noteResult = selectMapper.selectEsNoteIndexData(null, userId);
        for (Map<String, Object> recordMap : noteResult) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将每个 IndexRequest 加入到 BulkRequest
            bulkRequest.add(indexRequest);
        }

        // 执行批量请求
        restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    /**
     * 同步用户索引
     * @param userId
     */
    private void syncUserIndex(Long userId) throws Exception {
        // 1. 同步用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);

        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : userResult) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将数据写入 Elasticsearch 索引
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 处理笔记表事件
     * @param columnMap
     * @param eventType
     */
    private void handleNoteEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws Exception {
        long noteId = Long.parseLong(columnMap.get("id").toString());
        // 不同的事件，处理逻辑不同
        switch (eventType){
            // 处理新增事件
            case INSERT -> syncNoteIndex(noteId);
            // 处理更新事件
            case UPDATE -> {
                // 笔记变更后的状态
                Integer status = Integer.parseInt(columnMap.get("status").toString());
                // 笔记可见范围
                Integer visible = Integer.parseInt(columnMap.get("visible").toString());
                if(Objects.equals(status, NoteStatusEnum.NORMAL.getCode())){
                    if(Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode())){
                        // 正常且公开的数据我们才放入到ES中
                        syncNoteIndex(noteId);
                    }
                }else if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode()) // 仅对自己可见
                        || Objects.equals(status, NoteStatusEnum.DELETED.getCode())
                        || Objects.equals(status, NoteStatusEnum.DOWNED.getCode())) { // 被逻辑删除、被下架
                    // TODO: 删除笔记文档
                    deleteNoteDocument(String.valueOf(noteId));
                }
            }
            // 其余事件  从业务上而言我们的是逻辑删除 实际上的对应的操作是MySQL中更新操作 所以不需要单独的处理
            default -> log.warn("Unhandled event type for t_note: {}", eventType);
        }
    }

    /**
     * 笔记删除删除
     */
    private void deleteNoteDocument(String documentId) throws Exception {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(NoteIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /**
     * 笔记新增事件
     * @param noteId
     */
    private void syncNoteIndex(long noteId) throws IOException {
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId,null);
        for(Map<String, Object> recordMap : result){
            // 创建索引请求对象
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档ID
            indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
            // 设置文档内容
            indexRequest.source(recordMap);
            //发请求写入ES
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 将行数据对象转化为Map方便后续处理key为列字段 value为对应的值
     * @param columns
     * @return
     */
    private Map<String, Object> parseColumns2Map(List<CanalEntry.Column> columns) {
        Map<String,Object> map = new HashMap<>();
        columns.forEach(column -> {
            if(Objects.isNull(column)) return;
            map.put(column.getName(), column.getValue());
        });
        return map;
    }
}

```

