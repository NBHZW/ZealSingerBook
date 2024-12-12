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

