# 用户模块搭建

接下来搭建用户模块 用户模块的功能自然主要是和用户信息相关

## 基础搭建

首先依旧是准备工作 

基于**项目父模块创建User模块 基于User模块创建api  RPC调用模块和biz 业务模块**

![image-20240929204555926](../../ZealSingerBook/img/image-20240929204555926.png)

因为用户模块和之前的网关不同  业务功能更多 所以自然是和Auth认证模块一样 需要引入各种依赖，包括但不限于

**SpringBoot-Start	； MyBatisPlus  ； MySQL-Drive ； Redis  ； hutool ； nacos-discover  ；nacos-config ； druid**

然后将五张表对应的实体类和Server 和Mapper都配置好  以及配置文件和logback文件  还有ResposneCodeEnum枚举类型和全局异常捕获器

![image-20240929204924070](../../ZealSingerBook/img/image-20240929204924070.png)

## 业务编写

### （1）用户信息更新

需求分析

需要能在页面修改个人资料 通过如下图片可以看到 允许修改的字段有

| 字段             | 数据类型                                | 含义           |
| ---------------- | --------------------------------------- | -------------- |
| zealsingerBookId | String                                  | 账号           |
| nickname         | String                                  | 姓名（昵称）   |
| sex              | Integer                                 | 性别（0女1男） |
| avatar           | 接收用MultipartFile<br />保存库用String | 头像           |
| background       | 接收用MultipartFile<br />保存库用String | 背景图         |
| birthday         | LocalDateTime                           | 生日           |
| introduction     | String                                  | 个人简介       |

![image-20240929205241172](../../ZealSingerBook/img/image-20240929205241172.png)

那么根据这个可修改的字段 我们可以写出如下VO实体类封装请求内容

```Java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserInfoReqVO {
    /**
     * zealsingerBook Id 账号唯一标识
     */
    private String zealsingerBookId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 性别 0:女 1:男
     */
    private Integer sex;

    /**
     * 头像
     */
    private MultipartFile avatar;

    /**
     * 背景图
     */
    private MultipartFile background;

    /**
     * 生日
     */
    private LocalDateTime birthday;

    /**
     * 个人介绍
     */
    private String introduction;

}
```

但是同时，我们写业务之前，我们还需要关注一些业务细节，比如**说字段的修改是否有条件**

例如在小红书中，**我们的姓名修改不能带有特殊字符并且为2-24个字符 ； 账号ID只能存在英文 数字 下划线并且为6-15个字符 ； 个人简介中0~100个字符**

![image-20240929205908442](../../ZealSingerBook/img/image-20240929205908442.png)

所以我们的字符也需要有对应的检测机制，为了实现这个检测，我们写一个工具类帮我们进行检测

#### 检测工具类ParamUtils

因为这个其实是一个参数检测工具类 各个模块都有可能需要使用 我们创建到common模块中 实现共享

```Java
public final class ParamUtils {
    private ParamUtils() {
    }

    // ============================== 校验昵称 ==============================
    // 定义昵称长度范围
    private static final int NICK_NAME_MIN_LENGTH = 2;
    private static final int NICK_NAME_MAX_LENGTH = 24;

    // 定义特殊字符的正则表达式
    private static final String NICK_NAME_REGEX = "[!@#$%^&*(),.?\":{}|<>]";

    /**
     * 昵称校验
     *
     * @param nickname
     * @return
     */
    public static boolean checkNickname(String nickname) {
        // 检查长度
        if (nickname.length() < NICK_NAME_MIN_LENGTH || nickname.length() > NICK_NAME_MAX_LENGTH) {
            return false;
        }

        // 检查是否含有特殊字符
        Pattern pattern = Pattern.compile(NICK_NAME_REGEX);
        return !pattern.matcher(nickname).find();
    }

    // 定义 ID 长度范围
    private static final int ID_MIN_LENGTH = 6;
    private static final int ID_MAX_LENGTH = 15;

    // 定义正则表达式  这个是作用域ID的  只包含英文 数字和下划线
    private static final String ID_REGEX = "^[a-zA-Z0-9_]+$";

    /**
     * zealsinger book ID 校验
     *
     * @param id
     * @return
     */
    public static boolean checkId(String id) {
        // 检查长度
        if (id.length() < ID_MIN_LENGTH || id.length() > ID_MAX_LENGTH) {
            return false;
        }
        // 检查格式
        Pattern pattern = Pattern.compile(ID_REGEX);
        return pattern.matcher(id).matches();
    }

    /**
     * 字符串长度校验
     *
     * @param str
     * @param length
     * @return
     */
    public static boolean checkLength(String str, int length) {
        // 检查长度
        if (str.isEmpty() || str.length() > length) {
            return false;
        }
        return true;
    }
}
```

##### Pattern和Matcher

在Java中实现正则表达式的匹配 我们使用Pattern和Matcher

- Pattern类的作用在于编译正则表达式后创建一个匹配模式.
- Matcher类使用Pattern实例提供的模式信息对正则表达式进行匹配

常用方法有：

- **Pattern complie(String regex)**
  由于Pattern的构造函数是私有的,不可以直接创建,所以通过静态方法compile(String [regex](https://so.csdn.net/so/search?q=regex&spm=1001.2101.3001.7020))方法来创建,将给定的正则表达式编译并赋予给Pattern类

- **String pattern()** 返回[正则表达式](https://so.csdn.net/so/search?q=正则表达式&spm=1001.2101.3001.7020)的字符串形式,其实就是返回Pattern.complile(String regex)的regex参数

  ```Java
  String regex = "\\?|\\*";
  Pattern pattern = Pattern.compile(regex);  // 获得一个对应regex的匹配的pattern对象
  String patternStr = pattern.pattern();//返回\?\*  // 获得pattern对象所匹配的正则表达式
  ```

- **matcher(String str)** pattern对象拥有的方法  该方法返回一个str的natcher对象

- **matches()** 该方法为 mathcer对象拥有的方法，检测matcher是对象是否满足对应的pattern正则表达式

  ```Java
  Pattern pattern = Pattern.compile("\\?{2}");
  Matcher matcher = pattern.matcher("??");
  boolean matches = matcher.matches();// true  检测 "??" 是否满足  "\\?{2}" 这个正则表达式
  ```

  然后就是主体业务

  

  