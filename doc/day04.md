# 登录权限校验模块Auth

常见的登录的实现可以有**我们自己内部的账号+密码的形式进行登录 ； QQ登录 ； 微信登录 ；手机号登录**

可以看到 小红书中 我们的账号密码登录方式其实就是手机号登录 只是分为了**手机号+验证码 和 手机号+密码 两种登录方式**

![image-20240921100932402](../../ZealSingerBook/img/image-20240921100932402.png)

## 用户信息表

首先  登录则首先会需要保存用户账号信息，也就是需要一个用户信息表

![image-20240921102344558](../../ZealSingerBook/img/image-20240921102344558.png)

可以看到  用户中心的用户信息字段有如下：

| 字段               | 含义                                     |
| ------------------ | ---------------------------------------- |
| id                 | 字段主键（递增 无符号）                  |
| zealsinger_book_id | 账号（同 小红书号  用户唯一凭证 非null） |
| password           | 密码                                     |
| nickname           | 昵称（非null）                           |
| avatar             | 头像                                     |
| birthday           | 生日                                     |
| background_img     | 背景图片                                 |
| phone              | 手机号（唯一 非null）                    |
| sex                | 性别（0女1男）                           |
| status             | 用户状态（0启用 1禁用  非null）          |
| introduction       | 个人简介                                 |
| create_time        | 创建时间                                 |
| update_time        | 更新时间                                 |
| is_deleted         | 逻辑删除(0未删除 1已删除)                |



## SaToken框架

我们以前使用的较多的是Spring Security框架作为登录鉴权框架，其底层本质是过滤器链，功能比较强大，但也同时，带来了底层复杂的鉴权逻辑，我们需要重写其获取权限的类，同时需要编写对应的账户信息和权限封装Security可识别的autho对象，同时也要重新编写权限获取的方法，以及还有访问URL的权限配置文件也是很大的一个编写任务

相比较之下 SaToken灵活简单 上手极快  所以我们可以本次项目使用Sa-Token框架

首先是导入依赖

```xml
注：如果你使用的是 SpringBoot 3.x，只需要将 sa-token-spring-boot-starter 修改为 sa-token-spring-boot3-starter 即可。

<!-- Sa-Token 权限认证，在线文档：https://sa-token.cc -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot-starter</artifactId>
    <version>1.39.0</version>
</dependency>

```

因为我们是分布式服务 导入依赖均遵循 **父模块统一管理版本 子模块进行导入的原则**

整个项目的pom文件中

![image-20240920230833760](../../ZealSingerBook/img/image-20240920230833760.png)

然后在auth认证模块的pom中进行导入

![image-20240920230930083](../../ZealSingerBook/img/image-20240920230930083.png)

此时刷新maven之后启动Auth项目 就会看到Sa-Token相关的打印

![image-20240920231010149](../../ZealSingerBook/img/image-20240920231010149.png)

然后参考官网的配置文件  在配置文件中添加Sa-Token相关的配置信息

```yaml
// yaml格式
server:
    # 端口
    port: 8081
    
############## Sa-Token 配置 (文档: https://sa-token.cc) ##############
sa-token: 
    # token 名称（同时也是 cookie 名称）
    token-name: satoken
    # token 有效期（单位：秒） 默认30天，-1 代表永久有效
    timeout: 2592000
    # token 最低活跃频率（单位：秒），如果 token 超过此时间没有访问系统就会被冻结，默认-1 代表不限制，永不冻结
    active-timeout: -1
    # 是否允许同一账号多地同时登录 （为 true 时允许一起登录, 为 false 时新登录挤掉旧登录）
    is-concurrent: true
    # 在多人登录同一账号时，是否共用一个 token （为 true 时所有登录共用一个 token, 为 false 时每次登录新建一个 token）
    is-share: true
    # token 风格（默认可取值：uuid、simple-uuid、random-32、random-64、random-128、tik）
    token-style: uuid
    # 是否输出操作日志 
    is-log: true

```

然后测试对应的逻辑

```Java
/*
可以看到  基于StpUtil这个工具类可以使用很多方法来进行登录相关的操作
*/
@GetMapping("/user/login")
    @ZealLog(description = "Sa-Token登录接口测试")
    public Response<?> testLogin(String username,String password){
        if("zealsinger".equals(username) && "123123".equals(password)){
            StpUtil.login(1000);
            return Response.success("登录成功");
        }
        return Response.fail("登录失败");
    }

// 检测登录的状态接口
    @GetMapping("/user/islogin")
    @ZealLog(description = "测试Sa-Token登录状态查询接口测试")
    public Response<?> isLogin(){
        return Response.success("当前会话登录状态: " + StpUtil.isLogin());
    }
```

![image-20240922100705705](../../ZealSingerBook/img/image-20240922100705705.png)

在浏览器中访问测试

直接访问登录接口  可以看到返回了登录失败

![image-20240922100838474](../../ZealSingerBook/img/image-20240922100838474.png)

访问检测登录状态的接口  可以发现也是没有登录

![image-20240922101047655](../../ZealSingerBook/img/image-20240922101047655.png)

然后添加路径参数访问 返回登录成功

![image-20240922100939395](../../ZealSingerBook/img/image-20240922100939395.png)

再次访问查询登录状态的接口  这个时候可以看到 登录状态变成了true

![image-20240922101137462](../../ZealSingerBook/img/image-20240922101137462.png)



## 手机号+验证码登录接口

首先理清楚验证码登录逻辑

![image-20240921134749000](../../ZealSingerBook/img/image-20240921134749000.png)

### redis配置

可以看到 整个过程会依赖于Redis进行验证码的存储和防止重复请求

所以首先我们配置Redis相关内容

首先是导入Redis相关的依赖

```xml
<!--redis-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Redis 连接池 -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>
```

然后是Redis的相关配置

```yml
spring:
  datasource:
	// 省略...
  data:
    redis:
      database: 0 # Redis 数据库索引（默认为 0）
      host: 127.0.0.1 # Redis 服务器地址
      port: 6379 # Redis 服务器连接端口
      password: qwe123!@# # Redis 服务器连接密码（默认为空）
      timeout: 5s # 读超时时间
      connect-timeout: 5s # 链接超时时间
      lettuce:
        pool:
          max-active: 200 # 连接池最大连接数
          max-wait: -1ms # 连接池最大阻塞等待时间（使用负值表示没有限制）
          min-idle: 0 # 连接池中的最小空闲连接
          max-idle: 10 # 连接池中的最大空闲连接    

```

然后是redisTemplate的序列化设置

![image-20240921203746837](../../ZealSingerBook/img/image-20240921203746837.png)

定义静态方法和常量，方便获取Redis的key，从而方便从Redis中获取数据

```Java
public class RedisConstant {
    public static final String VERIFICATION_CODE_KEY_PREFIX="zealsinger_verification_code:";

    public static String getVerificationCodeKeyPrefix(String phone){
        return VERIFICATION_CODE_KEY_PREFIX+phone;
    }
}
```

### Server层编写

按照流程图 很简单就能编写完成 

SendVerificationCodeReqVO中封装了phone，接收前端传来的手机号参数

然后通过定义的Redis相关静态变量，**得到key，判断Redis中是否存在对应的value  如果有 则抛出异常“不要频繁请求”（在异常枚举类中添加一个专门的错误类型）**

```Java
@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private AliyunHelper aliyunHelper;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        String phone = sendVerificationCodeReqVO.getPhone();
        String redisKey = RedisConstant.getVerificationCodeKeyPrefix(phone);
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        if(Boolean.TRUE.equals(hasKey)){
            // 存在 提是不要频繁请求
            throw new BusinessException(ResponseCodeEnum.REQUEST_FREQUENT);
        }
        // 不存在 生成验证码并且发送过去  利用 hutool工具类生成6位数的纯数字验证码
        String code = RandomUtil.randomNumbers(6);
        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, code);

        // todo 异步调用第三方服务发送消息
        taskExecutor.submit(()->{
            String signName = "Zeal书";
            String templateCode = "SMS_473835044";
            String resultCode = String.format("{\"code\":\"%s\"}", code);
            aliyunHelper.sendMessage(signName,templateCode,phone,resultCode);
        });

        log.info("成功向手机号 {}  发送验证码 {} , 有效期 3min",phone,code);
        // 存入redis并且设置过期时间
        redisTemplate.opsForValue().set(redisKey,code,3, TimeUnit.MINUTES);
        return Response.success();
    }
}
```

其中  AliyunHelper是调用了阿里的第三方短信发送服务  所以AliyunHelper中封装了对应的SDK  这个参考官方文档即可

#### 自定义线程池

调用阿里云发送短信 是一个网络请求操作，放到主线程中会比较影响性能，所以我们通过线程池提交任务的方式，异步完成验证码的发送

自定义线程池的创建我们在Netty中写过了，这里再重新复习一次

```Java

/*
我们将线程池对象作为Bean对象交给Spring管理
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();创建线程池对象
我们Netty中使用的线程池对象是ThreadPoolExecutor，  本次使用的 ThreadPoolTaskExecutor 是Spring对ThreadPoolExcutor的二次封装，能更好的融入Spring环境（例如 @Asyn注解）

核心参数也是那几个   核心线程数 最大线程数  任务队列容量  线程活跃时间（相比较之下 封装了不同单位的方法  而不是和ThreadPoolExcutor一样 需要单独的一个参数指定时间单位）   拒绝策略    优雅关闭（等待所有任务完成后关闭）  强制关闭时间（任务多久没有完成则会强制关闭 防止阻塞）
*/
@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(50);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程活跃时间（秒）
        executor.setKeepAliveSeconds(30);
        // 线程名前缀
        executor.setThreadNamePrefix("AuthExecutor-");

        // 拒绝策略：由调用线程处理（一般为主线程）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 设置等待时间，如果超过这个时间还没有销毁就强制销毁，以确保应用最后能够被关闭，而不是被没有完成的任务阻塞
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
```

定义好之后 就可以在Server中使用  利用subimit方法  将aliyunHelper的方法执行放到submit中 就能实现异步发送验证码

```Java
// todo 异步调用第三方服务发送消息
        taskExecutor.submit(()->{
            String signName = "Zeal书";
            String templateCode = "SMS_473835044";
            String resultCode = String.format("{\"code\":\"%s\"}", code);
            aliyunHelper.sendMessage(signName,templateCode,phone,resultCode);
        });
```

### Controller层的编写

controller只要接收前端参数 手机号 然后调用server中的服务然后返回 逻辑比较简单

```Java
@RestController
@RequestMapping("/verification")
public class VerificationCodeController {

    @Resource
    private VerificationCodeService verificationCodeService;

    /*
    @Validated注解能检测参数  参数有误则会抛出异常
    一般和JSR中的相关注解配合使用  此处我们使用了@NotNull
    */
    @PostMapping("/code/send")
    @ZealLog(description = "发送登录验证码")
    public Response<?> sendVerification(@Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO){
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }
}

// SendVerificationCodeReqVO对象类

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendVerificationCodeReqVO {
    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;
}
```

### 自定义注解实现手机号的正则检查

除了手机号不能为空 自然也不能是随便的字符串 为空这一个条件还是不够的  所以我们还需要更多的约束

这里我们采用自定义注解的方式实现   因为这种注解可能在别的地方也可能需要使用 可以作为公共组件 所以我们在FrameWork模块中编写  **但是如何让自定义注解具备约束条件？**

**@Constraint注解是Java Bean Validation框架中的一个注解，用于自定义约束注解，即自定义校验规则，可以通过这个实现我们的需求**

```Java
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
// 上面两个元注解 指定可注解的位置和生命时期为Runtime

// @Constraint 可以配置validatedBy属性值来指定注解对应的约束验证器  对于约束验证器  其实就是一个实现了ConstraintValidator<注解名, String> 接口
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface PhoneNumber {
    /*
    如下 message属性和group属性是必须的  @Constraint发挥作用的必须两个属性
    如果你移除了 message 和 group 属性，你的自定义注解将不再符合 Constraint 的规范，并且在使用时可能会出现错误
    
    */
    String message() default "手机号格式不正确, 需为 11 位数字"; //message 属性允许你为验证失败的情况提供一个默认的错误消息。这是用户界面或日志中显示给用户的信息，用以指示数据不符合预期的约束。

    Class<?>[] groups() default {}; // groups() 属性允许你将约束分组，以便在不同的场景下应用不同的验证规则。作用：通过定义不同的验证组，你可以根据不同的业务场景执行不同的验证集合。例如，你可能有一个用户实体，在创建用户时需要验证某些字段，而在更新用户时需要验证不同的字段集。使用：你可以定义接口来代表不同的验证组，然后在 groups 属性中指定这些接口。在验证时，你可以指定要验证的组。

    Class<? extends Payload>[] payload() default {};
}



/*
约束验证器  实现该接口中的两个方法 
initialize 方法是用来执行初始化操作的。这个方法在校验器实例化后会被调用，通常用来读取注解中的参数来设置校验器的初始状态。在这里，我们没有任何初始化操作，所以方法体是空的。
isValid方法就是检验判断逻辑 将我们需要的约束定义在里面进行校验 如果通过则返回True 反之为false
*/
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {
    @Override
    public void initialize(PhoneNumber constraintAnnotation) {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        /*
        phoneNumber：需要验证的字符串，即被注解的属性值。
	    context：提供了一些校验的上下文信息，通常用来设置错误消息等。
        */
        // 比如这里我们只要求属性s为11位数字即可 所以直接这么返回即可  更复杂的也可以多处理后在返回true/false
        return s!=null && s.matches("\\d{11}");
    }
}

```

### RBAC权限模型

在学Spring Security的时候 就接触过RBAC权限模型了，但实际上，我们之前接触的RBAC模型是比较简单的RBAC

**RBAC的核心 就是通过角色和用户和权限的关联 从而进行权限控制   用户有角色这类属性（用户和角色有联系）  权限不会和用户直接绑定 而是直接和角色绑定 从而实现用户和权限的联系**

![image-20240922083109939](../../ZealSingerBook/img/image-20240922083109939.png)

**一个用户可以是多个角色  每个角色可以有多个权限  每个权限也可以被多个角色拥有  形成 n:n:n**

![image-20240922083423206](../../ZealSingerBook/img/image-20240922083423206.png)

我们上面说到的RBAC模型，是最基础的RBAC，也就是RBAC0，随着业务的复杂度提高已经各种业务场景的出现，RBAC模型也出现了改良优化，推出了RBAC1   RBAC2  RBAC3

#### RBAC1

RBAC 1 在 RBAC 0 的基础上增加了角色**层次结构**（Role Hierarchies）。角色层次结构允许角色之间存在**继承关系**，**一个角色可以继承另一个角色的权限  ；  继承关系是传递的，如果角色 C 继承角色 B，而角色 B 继承角色 A，那么角色 C 将拥有角色 A 和角色 B 的所有权限**

**类似于类的继承 A继承B  那么A天生就拥有B所拥有的权限，除此之外，A还能有自己的特有权限**

优点是：

- **简化权限管理**：通过角色继承，可以减少重复定义权限的工作。
- **提高灵活性**：可以方便地对角色进行分层管理，满足不同层次用户的权限需求

```
场景举例
高级经理继承经理   经理继承员工
那么经理只有员工的管理权限和自己的区别于员工的私有权限
但是高级经理永远有经理和员工的所有权限 还能拥有区别于经理和员工的私有权限
```

#### RBAC2--基于约束的RBAC

所谓的基于约束，就是对角色一种前置要求，如果想要设置用户的角色是B，那么必须要先成为其前置角色A 或者是 某个用户不能同时设置为某两个角色（可以看特点里面的举例）；约束是用于加强访问控制策略的规则或条件，可以限制用户、角色和权限的关联方式

主要特点

- **互斥角色**：某些角色不能同时赋予同一个用户。例如，审计员和财务员角色不能同时赋予同一个用户，以避免暗黑交易。
- **先决条件**：用户要获得某个角色，必须先拥有另一个角色。例如，公司研发人员要成为高级程序员，必须先成为中级程序员。
- **基数约束**：限制某个角色可以被赋予的用户数量。例如，某个项目的经理角色只能赋予一个用户，以确保项目的唯一责任人。

优点：

- **加强安全性**：通过约束规则，可以避免权限滥用和利益冲突。
- **精细化管理**：可以更精细地控制用户的角色分配和权限管理。

#### RBAC 3----统一模型

RBAC3 = RBAC1 + RBAC2  拥有RBAC1 和 2 的所有特点，将两者结合使用的

#### 建库建表

RBAC相关内容介绍完毕，可以准备创建对应的数据库表格了。

因为我们知道**RBAC主要的三个表是 用户表  角色表  权限表 三张表  然后 用户与角色  角色与权限 之间需要有联系  也就是还需要 用户-角色表  角色-权限表  所以也就是五张表**

用户表t_user之前已经创立过了，就不多说了

##### 角色表t_role

```sql
CREATE TABLE `t_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名',
  `role_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色唯一标识',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态(0：启用 1：禁用)',
  `sort` int unsigned NOT NULL DEFAULT 0 COMMENT '管理系统中的显示顺序',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一次更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

```

##### 权限表t_permission

```sql
CREATE TABLE `t_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父ID',
  `name` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `type` tinyint unsigned NOT NULL COMMENT '类型(1：目录 2：菜单 3：按钮)',
  `menu_url` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单路由',
  `menu_icon` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单图标',
  `sort` int unsigned NOT NULL DEFAULT 0 COMMENT '管理系统中的显示顺序',
  `permission_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限标识',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态(0：启用；1：禁用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

/*
pareant_id主要是方便搭建权限结构树形结构
type标识权限类型，前台模块，可以理解为操作，
menu_url菜单路由 当权限类型为目录的时候 保存前端对应的路由地址
menu_icon菜单图标 图标Lego
permission_key权限唯一标识，如 system:role:add , 用于表示后台角色新增的权限 ，供权限框架使用
*/

```

##### 用户角色表t_user_role_rel

```SQL
CREATE TABLE `t_user_role_rel` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色表';

```

角色权限表t_role_permission_rel

```SQL
CREATE TABLE `t_role_permission_rel` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `permission_id` bigint unsigned NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限表';

```

## 鉴权架构

我们创建Auth模块，目的就是为了进行登录校验和鉴权，但是整个流程该如何设计呢？

考虑到我们是分布式架构，我们的服务自然是不会暴露在外网环境，而是会通过nginx和路由网关，将外部的访问路由到内部环境中

![image-20240922085939179](../../ZealSingerBook/img/image-20240922085939179.png)

那么在此架构之下，我们的鉴权方式有如下几种：

### 每个微服务（模块）中进行鉴权

也就是**网关处不进行鉴权，而是当服务转发到确定的微服务模块上**之后，再进行对应的鉴权操作，这样的**好处在于**：

- **每个服务的鉴权是分开的，只需要处理自己服务的鉴权业务，模块压力小，避免单点瓶颈；**
- **业务分散，独立性强**
- **灵活性高，每个微服务模块都能根据自己模块业务需求定制专属的鉴权规则**

**缺点是：**

- 每个微服务都要单独实现，维护成本高
- 不一致风险，每个微服务之间的由于个性化定制的鉴权规则，导致鉴权逻辑不一致从而权限难以把握，引发系统安全风险
- 增加开发和部署成本



### 网关统一鉴权

在网关处进行了统一鉴权，减去那完毕后再转发

**优点是：**

- 在网关处统一鉴权，保证了鉴权逻辑的一致性，减少了不一致风险，同时也避免了代码重复
- 简化模块的开发，让微服务每个模块能更加专注于业务的处理，简化开发和维护
- 统一鉴权可以利用负载均衡，缓存等技术进行性能优化，提升整体效率

**缺点是：**

- 网关成为了唯一了鉴权处理地点，负责所有的鉴权处理，网关设备的性能和故障会直接影响整个系统
- 网关可能对于不同的路由需要不同的鉴权逻辑，导致网关的实现复杂度和维护难度大大提高
- 所有请求都需要在网关处鉴权后再路由，增加系统响应时间

### 混合模式

结合 **微服务中单独鉴权和网关统一鉴权**  两种方式进行权限校验  利用网关进行粗粒度的初步的权限校验，然后再每个微服务中再进行细粒度的二次鉴权

因为我们的**仿小红书项目 是个To C的产品**  消费者面更广 服务的标准化和便捷化要求都高 所以我们的项目 最终使用 **混合模式**

**网关中只对普通用户的操作进行鉴权，其他角色，如管理员等等，到时候在具体的管理后台服务中再鉴权，以保证网关做最少的工作，实现最大的吞吐量**

## 权限数据获取方案

对于每个模块的权限数据获取 还是比较好操作的 直接操作数据库即可一般 但是问题在于  **网关处如何获取权限数据从而进行第一次粗粒度的初步校验**

1. ### **网关服务中也继承ORM框架（Mybatis这种），直接从数据库中获取数据**

   **直接从数据库中获取  实时性强  能保证是最新的数据  ； 不设置额外的缓存层，也不会需要远程调用，结构简单**

   **但是同时，会导致更多的SQL查询，增加数据库的压力，性能下降，不适合高并发量，可拓展性差，SQL或成为性能瓶颈**

   

2. ### **先从Redis中获取权限数据，如果没有则从数据库中获取**

   **添加了缓存层，大部分请求可以被缓存处理，减少了数据库压力，提高了一定的性能**

   **同时 引入了新的问题  缓存和数据库的双写一致性的问题，添加了缓存层，系统复杂性增强**

   

3. ### **网关先从 Redis 中获取权限数据，若获取不到，走 RPC 调用权限服务获取数据**

   **同上  减少数据库压力，提高性能，通过RPC调用权限模块的接口，相较于1和2不需要在网关处进行权限库的操作，实现了网关和权限模块的解耦**

   **同时 走RPC自然会带来更多的网络开销，也容易收到网络的影响，同样也增加了系统复杂性**

   

4. ### **只走Redis获取权限数据，不走RPC也不走数据库**（采用）

   **只有Redis，没有RPC和数据库操作，是性能最高和数据库压力最小的方案**

   **同时 对于Redis中数据的实时更新要求更大  否则容易出现数据不一致问题  并且因为网关只会从Redis中获取  如果Redis挂了则会有较大影响，易出现单点故障**

因为我们这次主要是为了搭建一个高并发读写的项目 所以主要是能承受最大高并发的方案4

## Sa-Token整合Redis

我们上面用Sa-Token做了简单的登录和登录状态检测接口 但是会发现一个问题 我们登录之后 重启项目之后 再次检测登录状态 就会变为未登录

这个是因为Sa-Token中记录的登录状态没有存储到Redis中 ，没有持久 重启之后不能记录 不适用于分布式的环境

Sa-Token中提供了集成Redis的方法

[集成 Redis (sa-token.cc)](https://sa-token.cc/doc.html#/up/integ-redis)

```xml
<!-- Sa-Token 整合 Redis （使用 jdk 默认序列化方式） -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis</artifactId>
    <version>1.39.0</version>
</dependency>

// 我们使用的这个
<!-- Sa-Token 整合 Redis （使用 jackson 序列化方式） -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
    <version>1.39.0</version>
</dependency>

```

然后需要配置Redis实例  但是我们项目本身也会需要Redis连接 所以我们需要一个Redis连接池  commons-pool2 这个库就能提供 我们之前redis配置的时候就导入仪过了，配置也写好了，所以无需额外配置了

```
<!-- 提供Redis连接池 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>


spring: 
    # redis配置 
    redis:
        # Redis数据库索引（默认为0）
        database: 1
        # Redis服务器地址
        host: 127.0.0.1
        # Redis服务器连接端口
        port: 6379
        # Redis服务器连接密码（默认为空）
        # password: 
        # 连接超时时间
        timeout: 10s
        lettuce:
            pool:
                # 连接池最大连接数
                max-active: 200
                # 连接池最大阻塞等待时间（使用负值表示没有限制）
                max-wait: -1ms
                # 连接池中的最大空闲连接
                max-idle: 10
                # 连接池中的最小空闲连接
                min-idle: 0

```

集成Redis之后，Sa-Token会自动将token认证码保存到redis中

![image-20240922104157777](../../ZealSingerBook/img/image-20240922104157777.png)

![image-20240922104212742](../../ZealSingerBook/img/image-20240922104212742.png)

（需要注意 login对应的传入的id是value  sa-token生成的token凭证才是key）

![image-20240922104423453](../../ZealSingerBook/img/image-20240922104423453.png)

# 用户登录/注册接口

逻辑流程图

![image-20240923095627603](../../ZealSingerBook/img/image-20240923095627603.png)

可以看到 小红书的登录逻辑 最默认的就是**手机号+验证码**的方式 并且如果该用户是第一次进行入系统 是会进行**自动注册**的，所以我们也按照这个逻辑进行编写即可

## 前提准备

首先 对于请求体的定义如下

```Java
/*
我们采用手机号+验证码 and  手机号+密码 两种方式登录 
所以 手机号是必须的 不能为空  带有注解notnull必须被校验
但是由于方式的不同 code和password可以允许为空  所以不进行入参字段的强制校验
type用于区分操作类型
*/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginReqVO {
    @NotNull(message = "手机号不能为空")
    @PhoneNumber
    private String phoneNumber;

    /**
     * 验证码
     */
    private String code;

    /**
     * 密码
     */
    private String password;

    /**
     * 登录类型：1手机号验证码，或者是2账号密码
     */
    @NotNull(message = "登录类型不能为空")
    private Integer type;
}
```

我们在登录和注册的时候，自然也需要将用户登录信息和token和用户信息保存到redis中，并且对于用户需要进行身份的存储,所以这里存在部分内容需要在redis中保存记录对应的Key的常量值

其中，用户登录信息和token为一组，主要有sa-token自动存放到redis中，sa-token登录的时候会需要传入一个id，我们使用user表中的主键为id即可，token自动生成，这一组key-value就存放完毕；

然后 对于新用户 我们注册的时候 我们用户需要一个账号ID 这个id是自动生成的，但是需要注意，**id自然是需要保证唯一，不重复，并且在表中最好保持递增性，这个就可以通过redis的incr对同一个key的自增实现 ； 有了ID之后，我们可以通过“指定字符串前缀+ID”的格式生成默认的用户昵称**

验证码是存放在redis中的会过期的（暂定为3min  为了测试可以先永久）数据，一个用户在过期时间内不能频繁请求验证，验证码是随机数生成可能会重复，所以一般采用 **用户为key，验证码code为value**的形式保存，因为key和用户相关，**要能key和用户一一对应**，user表中具备唯一属性的字段只有主键;账号;手机号三者，但是考虑到**新用户登录的话自动注册后才会有主键和账号，而手机号是前端传参获得，必定是不会为空的，所以我们key采用“指定字符串前缀：手机号"的形式保存**

所以为了方便Redis操作，我们需要定义一些Redis的常量Key先规范保存

```Java
public class RedisConstant {
    /*
    * redis中存放验证码的字符串前缀
    */
    public static final String VERIFICATION_CODE_KEY_PREFIX="zealsinger_verification_code:";

    /**
     * 用户账户ID  在redis中保存一个永久的key value从100开始
       通过 INCR 命令即可实现每次对其自增 1
     */
    public static final String ZEALSINGER_BOOK_ID_GENERATOR_KEY="zealsinger_id_generator";

    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";

    /**
     * 构建角色对应的权限集合 KEY
     * @param roleId
     * @return
     */
    public static String buildRolePermissionsKey(Long roleId) {
        return ROLE_PERMISSIONS_KEY_PREFIX + roleId;
    }

    /**
     * 构建验证码KEY  特点前缀:手机号
     * @param phone
     * @return
     */
    public static String buildUserRoleKey(String phone) {
        return USER_ROLES_KEY_PREFIX + phone;
    }

    public static String getVerificationCodeKeyPrefix(String phone){
        return VERIFICATION_CODE_KEY_PREFIX+phone;
    }
}
```

自然，我们的用户也需要具备角色属性，角色属性我们通过主键ID标识每个用户的角色身份，这个也可以定义为常量

```Java
public class RoleConstants {
    /**
     * 普通用户的角色 ID
     */
    public static final Long COMMON_USER_ROLE_ID = 1L;

}
```

那么自此，我们就可以开始写登录注册接口

## 手机号+验证码方式

### Controller

接收参数  调用server中的方法 很简单  不多说

```Java
@PostMapping("/login")
    @ZealLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return userService.loginAndRegister(userLoginReqVO);
    }
```

### Service

Service中就代码逻辑丰富了 一行一行看

```Java
@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserRoleMapper userRoleMapper;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    
    // 这里采用编程式事务
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        // 根据不同的类型进行判断逻辑
        // 如果为验证码登录
        SaTokenInfo tokenInfo = null;
        Long userId = null;
        if(LoginTypeEnum.VERIFICATION_CODE.getValue().equals(userLoginReqVO.getType())){
            // 验证码登录 先检测验证码
            String key = RedisConstant.getVerificationCodeKeyPrefix(userLoginReqVO.getPhoneNumber());
            Object o = redisTemplate.opsForValue().get(key);
            if(o == null){
                throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
            }else{
                // 如果验证码正确
                if(String.valueOf(o).equals(userLoginReqVO.getCode())){
                    // 检测是否存在用户 如果有 则直接登录 没有则登录+注册添加用户信息
                    User user = userMapper.selectByPhone(userLoginReqVO.getPhoneNumber());
                    if(user==null){
                        userId = registerUser(userLoginReqVO.getPhoneNumber());
                        log.info("===>用户 {} 注册成功",userId);
                    }else{
                        userId = user.getId();
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

        }
        return null;
    }

    /**
     * 系统自动注册用户
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        transactionTemplate.execute(status->{
            try{
                // 获取全局自增的小哈书 ID
                Long zealId = redisTemplate.opsForValue().increment(RedisConstant.ZEALSINGER_BOOK_ID_GENERATOR_KEY);
                User userDO = User.builder()
                        .phone(phone)
                        // 自动生成 账号ID  我们从100开始递增作为账号 
                        .zealsingerBookId(String.valueOf(zealId))
                        // 自动生成昵称  前缀+账号ID, 如：zealsingerbook100
                        .nickname("zealsingerbook" + zealId)
                        // 账号状态为启用
                        .status(StatusEnum.ENABLE.getValue())
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        // 设置逻辑删除状态  这个枚举类在Common模块中
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build();

                // 添加入库
                userMapper.insert(userDO);

                // 获取刚刚添加入库的用户 ID
                Long userId = userDO.getId();

                // 给该用户分配一个默认角色
                UserRole userRoleDO = UserRole.builder()
                        .userId(userId)
                        .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build();
                userRoleMapper.insert(userRoleDO);

                // 登录逻辑;将该用户的角色 ID 存入 Redis 中
                List<Long> roles = Lists.newArrayList();
                roles.add(RoleConstants.COMMON_USER_ROLE_ID);
                String userRolesKey = RedisConstant.buildUserRoleKey(phone);
                redisTemplate.opsForValue().set(userRolesKey, JsonUtil.ObjToJsonString(roles));
                return userId;
            }catch (Exception e) {
                // 标记为事件回滚
                status.setRollbackOnly();
                log.error("系统注册服务出现故障!!!");
                return null;
            }
        });
        return null;
    }
}
```

测试结果

![image-20240923102510396](../../ZealSingerBook/img/image-20240923102510396.png)

![image-20240923102520206](../../ZealSingerBook/img/image-20240923102520206.png)

![image-20240923102844922](../../ZealSingerBook/img/image-20240923102844922.png)

### 角色权限信息保存

上图看来，用户数据和用户-角色数据都正常保存了，但是Autho模块还有重要的鉴权功能，但是我们这里目前只有角色数据，而没有角色-权限的对应数据，也就是说目前我们只知道用户对应的角色，但是不知道角色对应的权限，那么自然也就无法知道用户的权限

**所以我们还需要将角色-权限信息保存到redis中**

这个改如何实现呢？**因为角色-权限信息修改可能比较少，而且这个一般是没有接口直接触发的，所以我们考虑在项目启动之后自动加载角色-权限信息**



