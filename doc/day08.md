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

## 网关路由搭建

同样的 我们的所有的请求 都需要先通过网关gateway  对于user模块的接口 我们统一采用user开头  所以gateway的配置文件中需要添加如下内容

**访问 /user/test 会被路由到  注册中心上的 zealsingerbook-user服务的  /test接口上**

![image-20241001161029908](../../ZealSingerBook/img/image-20241001161029908.png)



## 业务编写

## （1）用户信息更新

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

### 检测工具类ParamUtils

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

#### Pattern和Matcher

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






### 主要业务

然后就是主体业务  主要是server中的逻辑

整体逻辑还是比较清楚的  我们利用一个needUpdate的boolean类型的变量标识是否需要被更新，从而减少不必要的数据库更新操作

对于**需要检测的字段，统一采用guava工具类中的方法实现**

```Java
/*
Guava是谷歌提供的一个Java依赖库  封装了不少方便的工具类 
我们之前有说到  某些字段我们需要一个检测 不能包含特殊字符 长度限制等  我们为此定义了一个ParamUtils工具类
这里 我们搭配Guava和ParamUtils一起使用

Preconditions.checkArgument(boolean  ，String errorMessage)

第一个参数boolean  即判断是否满足检测，true则通过，flase则不通过，当不通过的时候，就会抛出异常，异常信息为第二个参数errorMessage

将两者个搭配 我们就能类似如下的字段检测
Preconditions.checkArgument(ParameUtils.func(需要被检测的字段), ResponseCodeEnum.error.getErrorMessage)
ParameUtils.func()为对应工具类中的对应的检测方法   ResposneCodeEnum.error为对应的错误的枚举类类型
*/
```



```Java
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final int MAX_INTRODUCTION_LEN=100;
    @Resource
    private UserMapper userMapper;
    @Resource
    private FileFeignApi fileFeignApi;
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO vo) {
        // 创建初始user对象
        Long userId = LoginUserContextHolder.getUserId();
        User user = new User();
        user.setId(userId);

        // 是否需要更新的标识
        boolean needUpdate = false;

        // 头想 和 背景图片
        MultipartFile avatar = vo.getAvatar();
        MultipartFile background = vo.getBackground();
        if(avatar != null) {
            // TODO 调用对象存储服务
        }
        if(background != null) {
            //TODO 调用对象存储服务
        }

        // 昵称
        String nickname = vo.getNickname();
        if(StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getErrorMessage());
            user.setNickname(nickname);
            needUpdate = true;
        }

        //ID
        String zealsingerBookId = vo.getZealsingerBookId();
        if(StringUtils.isNotBlank(zealsingerBookId)) {
            Preconditions.checkArgument(ParamUtils.checkId(zealsingerBookId), ResponseCodeEnum.ZEALSINGERBOOK_ID_VALID_FAIL.getErrorMessage());
            user.setZealsingerBookId(zealsingerBookId);
            needUpdate = true;
        }

        // 性别
        Integer sex = vo.getSex();
        if(sex!=null){
            Preconditions.checkArgument(SexEnum.isValid(sex),ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            user.setSex(sex);
            needUpdate = true;
        }

        // 介绍
        String introduction = vo.getIntroduction();
        if(StringUtils.isNotBlank(introduction)){
            Preconditions.checkArgument(ParamUtils.checkLength(introduction,MAX_INTRODUCTION_LEN),ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            user.setIntroduction(introduction);
            needUpdate = true;
        }

        // 生日
        LocalDateTime birthday = vo.getBirthday();
        if (Objects.nonNull(birthday)) {
            user.setBirthday(birthday);
            needUpdate = true;
        }

        if(needUpdate){
            userMapper.updateById(user);
        }

        return Response.success();
    }
}

```

#### openFeign远程调用

可以看到  我们更新业务中需要用到oss模块中的业务功能，因为是分布式环境，我们自然不能直接导包的方式使用，而是需要远程调用，这里我们采用openFeign实现

回顾一下openFeign的使用

对于远程调用，自然就分为了服务提供者和服务调用者，在我们目前的业务需求而言，服务提供者就是zealsingerbook-oss-biz模块中的接口，服务调用者就是当前的user模块

首先，对于服务提供者，我们需要导入openFeign的相关依赖包

这里我们对于每一个模块服务 我们都是采用的   **biz + api 两个子模块的方式搭建  biz模块是业务模块 也就是业务主体  api模块是外部调用模块  实际上就是外部模块调用api中的接口 api的接口对应上biz模块中的接口  从而实现远程调用  其实也可以直接调用biz  我们分开只是为了规范化和解耦**

```xml
<!-- OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- 负载均衡 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
```

![image-20241001154428886](../../ZealSingerBook/img/image-20241001154428886.png)

我们需要先做一个测试  我们在oss的controller中先添加一个测试接口  那么目前我们的测试任务就是调用oss中的这个接口

![image-20241001154923889](../../ZealSingerBook/img/image-20241001154923889.png)

我们知道 openFeign是基于Http的远程调用，对于我们的test接口 如果进行Http访问的话 就是需要访问  /file/test 的post请求进行访问  那么其实openFeign做的也就是这件事，相当于在user模块对oss模块所运行的服务器上发一个发一个 /file/test 的Post请求

因为我们需要将外部api和业务分开  也就是说openFeign发送的这个请求实际上是打到了 api模块中

![image-20241001155437932](../../ZealSingerBook/img/image-20241001155437932.png)

```java 
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface FileFeignApi {

    String PREFIX = "/file";  // 标识oss的controller的url的前缀 对应fileController中的RequestMapping("/file")

    @PostMapping(value = PREFIX + "/test")
    Response<?> test();

}
```

- ```
  @FeignClient
  ```

  是用来标记这个接口是一个 Feign 客户端的注解。

  - `name = ApiConstants.SERVICE_NAME` 指定了这个 Feign 客户端所调用的服务名称。这个名称通常是在注册中心（如 Eureka 或 Nacos）中注册的服务名称。

- `String PREFIX = "/file";` : 定义了一个前缀常量，用于接口中 URI 的路径前缀。

- ```
  @PostMapping
  ```

  注解标记这个方法将执行一个 HTTP POST 请求。

  - `value = PREFIX + "/test"` 指定了这个 POST 请求的路径，这里是 `"/file/test"`。

```java 
public interface ApiConstants {
    String SERVICE_NAME = "zealsingerbook-oss";
}
```

然后在user模块中 ，作为服务调用者，首先需要标识允许远程调用，所以需要在启动类上添加注解@EnableFeignClients(basePackages = "com.zealsinger")

![image-20241001155931207](../../ZealSingerBook/img/image-20241001155931207.png)

然后我们在user中导入oss服务模块的api包  从而调用其中的方法

![image-20241001160036706](../../ZealSingerBook/img/image-20241001160036706.png)

整体流程就是

user模块实际上调用的是api包下的对应的api接口 但是接口中自然是不会有方法体的  方法体哪里来呢，发现oss的api接口中有@FeignClient(name = ApiConstants.SERVICE_NAME)注解 openFeign就会根据这个name 在注册中心中（也就是nacos）中找到对应服务，然后通过@PostMapping(value = PREFIX + "/test") 注解的 value属性 找到对应的服务url的那个接口方法 从而实现了远程调用



测试一下

我们直接访问整个user的update接口  apifox访问 8000端口的/user/updat（8000是gatewa运行的端口）  可以看到访问成功

![image-20241001161151486](../../ZealSingerBook/img/image-20241001161151486.png)

然后我们看user模块的控制台输出  可以看到ThreadLocal的相关日志 和 请求过滤器中拿到上游传来的解析token后得到的userId

![image-20241001161325211](../../ZealSingerBook/img/image-20241001161325211.png)

然后看到oss模块中的控制台输出日志    可以看到  自定义注解和Repsonse都对应的上 说明确实被调用了

![image-20241001161533083](../../ZealSingerBook/img/image-20241001161533083.png)

#### openFeign配置支持表单参数

上述我们尝试调用了test方法并没有问题，那么对于我们的真正业务而言，我们需要调用的是upload方法 需要传入一个文件类型的参数 在远程调用的时候 文件类型的参数以及还有一些实体对象类型  表单类型的参数  都有可能会出现问题 所以我们需要解决这个问题

![image-20241001162031405](../../ZealSingerBook/img/image-20241001162031405.png)

openFeign提供了一个额外的拓展组件库feign-form   帮助我们解决这个问题

首先在项目的最外层依赖中统一管理版本号

```xml
    <properties>
		// 省略...
        <feign-form.version>3.8.0</feign-form.version>
    </properties>

    <!-- 统一依赖管理 -->
    <dependencyManagement>
        <dependencies>
            // 省略...

            <!-- Feign 表单提交 -->
            <dependency>
                <groupId>io.github.openfeign.form</groupId>
                <artifactId>feign-form</artifactId>
                <version>${feign-form.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

```

然后在对应**的服务提供者（服务提供者的api模块中 也就是实际调用子模块  本次业务中为 oss-api模块）**的pom中也加入对应的依赖

```xml
        // 省略...
        
        <!-- Feign 表单提交 -->
        <dependency>
            <groupId>io.github.openfeign.form</groupId>
            <artifactId>feign-form-spring</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.openfeign.form</groupId>
            <artifactId>feign-form</artifactId>
        </dependency>
        
        // 省略...

```

该拓展类提供了一个编码对象，我们只需要在服务提供者中注册让spring管理这个bean，就能帮助我们处理文件类型和表单类型

![image-20241001162815926](../../ZealSingerBook/img/image-20241001162815926.png)

```Java
@Configuration
public class FeignFormConfig {
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();
    }
}
```

那么现在我们来尝试正式user调用oss模块中的方法，完善用户更新接口

首先修改oss-api中的对外的调用api接口

```Java
@PostMapping(value = PREFIX+"/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);
```

![image-20241001163329727](../../ZealSingerBook/img/image-20241001163329727.png)

然后是修改user  我们之前是直接在user中注入了 fileFeignApi 对象 我们可以再次解耦分层一下  将所有的对oss的调用任务交给另外一个server  然后通过这个server实现调用  我们创建一个RPC的目录 下面存放所有的对外部模块调用的服务  例如OssRpcServer均是对Oss模块进行的调用   以后还需要调用别的模块  加入xxxRpcServer即可

**每个RpcServer中  对应上真正Oss模块中接口即可  注意@Component注解  直接是个服务类  需要被Spring管理注入**

![image-20241001164100221](../../ZealSingerBook/img/image-20241001164100221.png)

那么在我们userServer中就可以注入对应的RpcServer对象即可

```Java
 // 头想 和 背景图片
        MultipartFile avatar = vo.getAvatar();
        MultipartFile background = vo.getBackground();
        if(avatar != null) {
            // TODO 调用对象存储服务
            String rpcResponse = ossRpcService.uploadFile(avatar);
            if(StringUtils.isBlank(rpcResponse)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);
            user.setAvatar(rpcResponse);
            needUpdate = true;
        }
        if(background != null) {
            //TODO 调用对象存储服务
            String rpcResponse = ossRpcService.uploadFile(background);
            if(StringUtils.isBlank(rpcResponse)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", background);
            user.setBackgroundImg(rpcResponse);
            needUpdate = true;
        }
```

![image-20241001164844798](../../ZealSingerBook/img/image-20241001164844798.png)

#### 最终自测

apifox发送请求 最后成功 日志成功打印 数据库也存入成功

![image-20241001165811140](../../ZealSingerBook/img/image-20241001165811140.png)

![image-20241001165820441](../../ZealSingerBook/img/image-20241001165820441.png)



## （2）代码重构

在之前的auth模块中，我们会需要用的userServer，然后这里面就会有用到register注册   selsectByPhone相关的逻辑  这个主要应对的表业务也是user相关  所以  为了保证微服务之间不进行直接的业务交融  实现模块单一职责  我们需要对之前的auth中的相关业务进行重构

auth模块中关于user模块中的业务 主要就是如下接口  **用户登录    修改密码**

![image-20241002091532495](../../ZealSingerBook/img/image-20241002091532495.png)

### 用户登录

看之前的代码 可以看到  用到userMapper的地方都需要修改   全部修改成Rpc远程调用User模块的方式进行  整体架构和上述User模块调用Oss模块类型

```java 
@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Resource
    private UserRpcServer userRpcServer;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private PasswordEncoder passwordEncoder;



    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        // 根据不同的类型进行判断逻辑
        // 如果为验证码登录
        SaTokenInfo tokenInfo = null;
        Long userId = null;
        String phoneNumber = userLoginReqVO.getPhoneNumber();
        if(LoginTypeEnum.VERIFICATION_CODE.getValue().equals(userLoginReqVO.getType())){
            // 验证码登录 先检测验证码
            String key = RedisConstant.getVerificationCodeKeyPrefix(phoneNumber);
            Object o = redisTemplate.opsForValue().get(key);
            if(o == null){
                throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
            }else{
                // 如果验证码正确
                if(String.valueOf(o).equals(userLoginReqVO.getCode())){
                    // 检测是否存在用户 如果有 则直接登录 没有则登录+注册添加用户信息
                    userId = userRpcServer.registerUser(phoneNumber);
                    if(Objects.isNull(userId)){
                        throw new BusinessException(ResponseCodeEnum.LOGIN_FAIL);
                    }
                    StpUtil.login(userId);
                    log.info("===>用户 {} 登录成功",userId);
                    tokenInfo = StpUtil.getTokenInfo();
                    return Response.success(tokenInfo.tokenValue);
                }else{
                    // 验证码不正确 抛出异常
                    throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }
            }
        }else{
            // 账号密码登录
            FindUserByPhoneRspDTO userByPhone = userRpcServer.findUserByPhone(userLoginReqVO.getPhoneNumber());
            if(Objects.isNull(userByPhone)){
                throw new BusinessException(ResponseCodeEnum.USER_NOT_FOUND);
            }else{
                boolean matches = passwordEncoder.matches(userLoginReqVO.getPassword(), userByPhone.getPassword());
                if(matches){
                    StpUtil.login(userByPhone.getId());
                    log.info("===>用户 {} 登录成功",userByPhone.getId());
                    tokenInfo = StpUtil.getTokenInfo();
                    return Response.success(tokenInfo.tokenValue);
                }else{
                    throw new BusinessException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }
            }
        }
    }

    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();
        log.info("===>用户 {} 退出登录",userId);
        StpUtil.logout(userId);
        return Response.success();
    }

    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        String encodePassword = passwordEncoder.encode(updatePasswordReqVO.getPassword());
        userRpcServer.updatePassword(encodePassword);
        return Response.success();
    }
}
```

如上 我们将  “ // 检测是否存在用户 如果有 则直接登录 没有则登录+注册添加用户信息 ” 下面的逻辑 从原本的调用自己的接口 变成了通过userRpcServer远程调用user模块 如下是userRpcServer对应的内容

可以看到 **UserRpcServer 是专门的调用user模块的类  注入了UserFeignApi类对象  UserFeignApi对象自然是user-api模块定义的对外Feign远程调用的客户端接口  对应了user-biz模块中的controller接口**

![image-20241002092401789](../../ZealSingerBook/img/image-20241002092401789.png)

```Java
@Component
public class UserRpcServer {
    @Resource
    private UserFeignApi userFeignApi;

    public Long registerUser(String phone) {
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();
        registerUserReqDTO.setPhone(phone);
        Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);
        if(response.isSuccess()){
            return response.getData();
        }
        return null;
    }


    public FindUserByPhoneRspDTO findUserByPhone(String phone) {
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();
        findUserByPhoneReqDTO.setPhone(phone);

        Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(findUserByPhoneReqDTO);

        if (!response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    public void updatePassword(String password) {
        UpdatePasswordReqDTO updatePasswordReqDTO = new UpdatePasswordReqDTO();
        updatePasswordReqDTO.setPassword(password);
        userFeignApi.updatePassword(updatePasswordReqDTO);
    }
}
```

自然 我们user-biz模块中也同时需要添加对应的Controller接口  供外部调用使用  标记一个分割线 区分自己使用和外部调用

```Java
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {
    @Resource
    private UserService userService;

    @PostMapping(value = "/update")
    public Response<?> updateUserInfo(@Validated @ModelAttribute UpdateUserInfoReqVO vo) {
        return userService.updateUserInfo(vo);
    }

    // ===================================== 对其他服务提供的接口 ===================================== //
    @PostMapping("/register")
    @ZealLog(description = "用户注册")
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO) {
        return userService.register(registerUserReqDTO);
    }

    @PostMapping("/findByPhone")
    @ZealLog(description = "根据手机号查询用户信息")
    public Response<FindUserByPhoneRspDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        return userService.findByPhone(findUserByPhoneReqDTO);
    }

    @PostMapping("/updatePassword")
    @ZealLog(description = "修改密码")
    public Response<?> updatePassword(@Validated @RequestBody UpdatePasswordReqDTO updatePasswordReqDTO) {
        return userService.updatePassword(updatePasswordReqDTO);
    }

}

```

![image-20241002092523284](../../ZealSingerBook/img/image-20241002092523284.png)

### Feign远程调用中将userId传递到下游

在重构修改密码的逻辑的时候  遇到了一个问题 

重构后的auth更新密码的接口  controller中调用server  server利用userRpc调用user-biz模块中的updatePassword接口 也就是下图代码

```java 
 @Override
    public Response<?> updatePassword(UpdatePasswordReqDTO updatePasswordReqDTO) {
        try{
            Long userId = LoginUserContextHolder.getUserId();
            String password = updatePasswordReqDTO.getPassword();
            LambdaUpdateWrapper<User> userUpdateWrapper = new LambdaUpdateWrapper<>();
            userUpdateWrapper.eq(User::getId, userId).set(User::getPassword,password);
            userMapper.update(userUpdateWrapper);
            return Response.success();
        }catch (Exception e){
            throw  new BusinessException(ResponseCodeEnum.PASSWORD_UPDATE_FAIL);
        }
    }
```

![image-20241002092736380](../../ZealSingerBook/img/image-20241002092736380.png)

可以很明显的看到 也很容易理解 我们更新密码 肯定是针对当前登录的用户而言 那么我们肯定会需要用到我们之前封装的ThreadLocal静态对象 从中拿取userId作为登录用户信息标识  但是在实际运行的后台日志中可以看到  **ThreadLocal并没有拿到userId!!!**

![image-20241002092708429](../../ZealSingerBook/img/image-20241002092708429.png)

这是因为  我们配置的ThreadLocal能拿到userId的设计是：gateway解析token得到userId ，将userId放到request请求头中，在自定义的ThreadLocal中 我们通过全局拦截器  拦截请求从而将请求头中的信息捕获到并且保存到静态变量ThreadLocal中

但是我们openFeign远程调用是不会走网关的 我们当前逻辑的访问路径为：网关--->auth模块---->user模块    

也就是说 userId 通过完成了网关--->auth模块的穿透 但是没有完成 auth模块---->user模块  的传递

如何解决呢？

方法一：既然auth能拿到userId，那么我们在auth中利用userRpcServer调用的时候，每次都固定的传输一个userId，这样固然可以，但是明显就很麻烦，所有的远程调用的接口都需要添加userId参数

方法二：Feign核心模块中提供了Feign请求的专门的拦截器，我们可以从这里下手

导入Feign的核心模块依赖

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-core</artifactId>
</dependency>
```

自定义一个类 实现接口  Feign中的 RequestInterceptor 接口  实现其方法 就能对Feign对各个模块间的调用的请求进行拦截处理  requestTemplate就是各个模块调用的每次的请求体一样的感觉  类似于HtppServerRequest  这样就可以让userId通过openFeign的时候也穿透传递到下游

```Java
@Slf4j
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 先尝试获取当前上下文中的用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 若不为空，则添加到请求头中
        if (Objects.nonNull(userId)) {
            requestTemplate.header(GlobalConstants.USER_ID, String.valueOf(userId));
            log.info("########## feign 请求设置请求头 userId: {}", userId);
        }
    }
}

```

![image-20241002094352072](../../ZealSingerBook/img/image-20241002094352072.png)

# 遇坑细节

（1）用户信息接口更新出现序列化异常的问题

问题复现：

![image-20241002085535867](../../ZealSingerBook/img/image-20241002085535867.png)
![image-20241002085603841](../../ZealSingerBook/img/image-20241002085603841.png)

用户信息更新我们知道，入参内容比较多，封装成了对象，入参接收估计有点问题 controller都没进去 直接走到了全局异常捕获里面了 从而出现了P1的报错

这个问题看下报错 和Jackson有关，那么可以猜想一下是序列化的问题

解决：

**@ZealLog自定义切面类注解中，我们序列化的方式是jackson，jackson对于MultiparFile类型等文件类型的序列化存在一定的局限性，暂时没找到相关资料，目前的解决方法是，将自定义注解去掉....**

