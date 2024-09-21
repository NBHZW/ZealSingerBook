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

