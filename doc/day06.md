# GateWay网关搭建

> Gateway 网关（API Gateway）是一个服务器，充当所有客户端请求的单一入口点。它接收来自客户端的所有请求，处理这些请求，然后将它们转发给下游适当的微服务。

Gateway 网关通常具有以下功能：

- **路由转发**：将路由请求转发到适当的微服务实例。

- **负载均衡**：在多个服务实例之间分配请求，以实现负载均衡。

- **认证和授权**：对请求进行身份验证和授权，以确保只有授权的客户端才能访问服务。

  > PS : 咱们这个项目中，用户认证的工作，是由具体的认证服务来处理的。

- **日志和监控**：记录请求和响应的日志，并监控流量和性能指标。

- **限流和熔断**：控制流量，以防止服务过载，并提供熔断机制来应对服务故障。





创建模块后修改依赖文件 加入相关依赖

```xml
		<dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>

        <!-- 服务发现 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <!-- 网关 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- 负载均衡 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>

```

修改配置文件

```yml
server:
  port: 8000 # 指定启动端口
spring:
  cloud:
    gateway:
      routes:
        - id: auth
          uri: lb://zealsingerbook-auth
          predicates:
            - Path=/auth/**
          filters:
            - StripPrefix=1


spring.cloud.gateway.routes： 用于定义网关的路由规则。
- id: auth
id 用于唯一标识这个路由。这里将路由的 id 设置为 auth，表示这个路由的配置是针对认证服务的。
uri: lb://zealsingerbook-auth
uri 定义了请求将被路由到的目标服务地址。这里使用 lb://zealsingerbook-auth，其中 lb 代表的是负载均衡（Load Balancer），zealsingerbook-auth 是认证服务的名称。Spring Cloud Gateway 会使用注册中心（如 Nacos ）来解析并负载均衡到具体的服务实例。

predicates: 用于定义匹配规则，决定哪些请求会被路由到这个目标服务。每个 predicate 都是一个条件表达式，可以用来匹配请求路径、请求方法、请求头等信息。

- Path=/auth/** ： 这个 Path 断言用于匹配请求路径。这里 /auth/** 表示所有以 /auth/ 开头的路径都会匹配这个路由规则。例如，/auth/login、/auth/register 都会被这个路由处理。/** 是一个通配符，表示任意的后续路径。
filters : 定义路由过滤器。

- StripPrefix=1: 表示去掉路径中的第一个部分。例如，当请求路径为 /auth/verification/code/send 时，去掉第一个前缀部分后，实际传递给 zealsingerbook-auth 服务的路径将变成 /verification/code/send。
```

测试

正常返回token并且AOP日志也正常打印

![image-20240924155204879](../../ZealSingerBook/img/image-20240924155204879.png)

# sa-Token权限校验

Sa-Token实现接口鉴权 参考文档

[网关统一鉴权 (sa-token.cc)](https://sa-token.cc/doc.html#/micro/gateway-auth)

首先自认是需要配置全局鉴权配置类，配置哪些接口需要对应的权限，哪些接口可以直接放行  如下是Sa-Token给的案例配置

```Java
/**
 * [Sa-Token 权限认证] 配置类 
 * @author click33
 */
@Configuration
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器 
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
            // 拦截地址 
            .addInclude("/**")    /* 拦截全部path */
            // 开放地址 
            .addExclude("/favicon.ico")
            // 鉴权方法：每次访问进入 
            .setAuth(obj -> {
                // 登录校验 -- 拦截所有路由，并排除/user/doLogin 用于开放登录 
                SaRouter.match("/**", "/user/doLogin", r -> StpUtil.checkLogin());
                
                // 权限认证 -- 不同模块, 校验不同权限 
                SaRouter.match("/user/**", r -> StpUtil.checkPermission("user"));
                SaRouter.match("/admin/**", r -> StpUtil.checkPermission("admin"));
                SaRouter.match("/goods/**", r -> StpUtil.checkPermission("goods"));
                SaRouter.match("/orders/**", r -> StpUtil.checkPermission("orders"));
                
                // 更多匹配 ...  */
            })
            // 异常处理方法：每次setAuth函数出现异常时进入 
            .setError(e -> {
                return SaResult.error(e.getMessage());
            })
            ;
    }
}

```

我们自己的配置稍微修改修改为如下

```Java
@Configuration
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 开放地址
                .addExclude("/favicon.ico")
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    // 登录校验 -- 拦截所有路由，并排除/user/doLogin和发送验证码/verification/code/send 接口用于开放登录
                    SaRouter.match("/**")
                            .notMatch("/auth/user/login")
                            .notMatch("/auth/verification/code/send")
                            .check(r->StpUtil.checkLogin());

                    // user开头的需要有user权限
                    SaRouter.match("/auth/user/**", r -> StpUtil.checkPermission("user"));
                    // 访问logout接口需要common_user的角色身份
                    SaRouter.match("/auth/user/logout", r -> StpUtil.checkRole("common_user"));

                    /*
                    // 权限认证 -- 不同模块, 校验不同权限
                    SaRouter.match("/admin/**", r -> StpUtil.checkPermission("admin"));
                    SaRouter.match("/goods/**", r -> StpUtil.checkPermission("goods"));
                    SaRouter.match("/orders/**", r -> StpUtil.checkPermission("orders"));
                    */
                    // 更多匹配 ...  */
                });
    }
}
```

那么问题来了  "user" 这种权限字段属性和common_user这种身份字段该是怎么获取的呢？  

起始这里“user”权限字段对应的就是我们的permission_key字段属性 而role身份自然是对应的role身份表中的身份名称

![image-20240925105820511](../../ZealSingerBook/img/image-20240925105820511.png)

这里就需要用到Sa-Token中的StpInterface 接口了  重写里面的两个方法getPermissionList 和 getRoleList ，Sa-Token就是根据这两个方法获取对应loginId用户的权限列表和角色列表的

```java
/**
 * 自定义权限验证接口扩展 
 */
@Component   
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 返回此 loginId 拥有的权限列表 
        return ...;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 返回此 loginId 拥有的角色列表
        return ...;
    }

}
```

因为我们之前将角色-权限对应关系保存到了redis中 那么我们的鉴权相关的角色和权限获取 就可以直接从redis中获取

所以我们直接从redis中尝试获取权限列表和角色列表即可

```Java
@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {
    @Resource
    private RedisTemplate<String,String> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 返回loginId用户对应的权限列表
     * @param loginId
     * @param loginType
     * @return
     */
    @SneakyThrows
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.info("## 获取用户权限列表, loginId: {}", loginId);
        // 构建 用户-角色 Redis Key
        String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        // 根据用户 ID ，从 Redis 中获取该用户的角色集合
        String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);
        if (StringUtils.isBlank(useRolesValue)) {
            return null;
        }
        // 将 JSON 字符串转换为 List<String> 角色集合
        List<String> userRoleKeys = objectMapper.readValue(useRolesValue, new TypeReference<>() {});
        if (CollUtil.isNotEmpty(userRoleKeys)) {
            // 查询这些角色对应的权限
            // 构建 角色-权限 Redis Key 集合
            List<String> rolePermissionsKeys = userRoleKeys.stream()
                    .map(RedisKeyConstants::buildRolePermissionsKey)
                    .toList();

            // 通过 key 集合批量查询权限，提升查询性能。 redisTemplate的multiGet方法  传入key的集合 返回对应的value的集合 相当于批量读取数据
            List<String> rolePermissionsValues = redisTemplate.opsForValue().multiGet(rolePermissionsKeys);
            if (CollUtil.isNotEmpty(rolePermissionsValues)) {
                List<String> permissions = Lists.newArrayList();
                // 遍历所有角色的权限集合，统一添加到 permissions 集合中
                rolePermissionsValues.forEach(jsonValue -> {
                    try {
                        // 将 JSON 字符串转换为 List<String> 权限集合
                        // 这里使用的是jackson的readValue使得json数据转化为List<String> 类型
                        // new TypeReference<>() {} 是一个匿名内部类，用于指定要转换的目标类型。由于Java的类型擦除，直接使用List<String>.class是不行的，因为泛型信息在运行时会被擦除。TypeReference的匿名子类在创建时保留了泛型信息，这样ObjectMapper就能够知道要转换成哪种具体的泛型类型。
                        List<String> rolePermissions = objectMapper.readValue(jsonValue, new TypeReference<>() {});
                        permissions.addAll(rolePermissions);
                    } catch (JsonProcessingException e) {
                        log.error("==> JSON 解析错误: ", e);
                    }
                });
                // 返回此用户所拥有的权限
                return permissions;
            }
        }
        return null;
    }
    /**
     * 返回loginId用户对应的角色列表
     * @param loginId
     * @param loginType
     * @return
     */
    @SneakyThrows
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        log.info("## 获取用户角色列表, loginId: {}", loginId);
        // 构建 用户-角色 Redis Key
        String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        // 根据用户 ID ，从 Redis 中获取该用户的角色集合
        String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);
        if (StringUtils.isBlank(useRolesValue)) {
            return null;
        }
        // 将 JSON 字符串转换为 List<String> 集合
        return objectMapper.readValue(useRolesValue, new TypeReference<>() {});
    }
}
```

# 统一返回格式

Sa-Token的响应报错是如下格式的

```json
{
	"code": 500,
	"msg": "无此角色：admin",
	"data": null
}

```

但是我们自己定义的返回 是这样的

```json
{
	"success": true,
	"message": null,
	"errorCode": null,
	"data": null
}
```

所以我们需要转化一下  采用全局异常捕获器实现

首先 sa-token之所有有自己的返回格式 是因为他的默认配置中就存在异常处理器  **所以首先我们需要将其自己的异常捕获处理配置去掉**

![image-20240925211458969](../../ZealSingerBook/img/image-20240925211458969.png)

然后开始编写我们的全局异常捕获器

首先还是需要定义一下Response响应体中的errorCode和message对应的枚举类型

```Java
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("500", "系统繁忙，请稍后再试"),
    UNAUTHORIZED("401", "权限不足"),
    // ----------- 业务异常状态码 -----------
    ;
    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}

```

**然后再是全局异常捕获  这里稍微不同 我们在别的模块中是使用的@ControllerAdvice注解 但是我们这里是网关 它的本质并不是我们传统的web servelet项目，而是一个WebFlux架构  @ControllerAdvice只适合在mvc架构中的controller层中捕获异常**

**实现ErrorWebExceptionHandler接口实际上是在过滤器链的最后一层，专门捕获异常的，是gateway中重要的一部分**

```Java
@Component
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {
    @Resource
    private ObjectMapper objectMapper;
    
    /*
    参数exchange包含请求和响应信息的上下文对象。它提供了对请求和响应的访问，以及与请求处理相关的其他属性和方法。通过这个参数，异常处理器可以获取请求的详细信息，如请求头、请求体、路径变量等，并且可以修改响应。
    
    参数Throwable是Java中所有错误和异常的超类。这个参数代表了在处理请求时抛出的异常或错误。异常处理器可以使用这个参数来获取异常的类型、消息、堆栈跟踪等信息，并根据这些信息决定如何处理异常。
    
    Mono<VOid>是Project Reactor中的响应式类型，它表示一个可能包含0或1个元素的异步序列。在这个上下文中，Mono<Void>表示异常处理器处理异常后，将返回一个空的异步结果。这意味着异常处理器在完成其逻辑后，不会返回任何值，但可以修改响应的状态码、头部和体。其实也就类似Void  标识这个方法没有返回值
    */
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 获取响应对象
        ServerHttpResponse response = exchange.getResponse();

        log.error("==> 全局异常捕获: ", ex);

        // 响参
        Response<?> result;
        // 根据捕获的异常类型，设置不同的响应状态码和响应消息
        if (ex instanceof SaTokenException) { // Sa-Token 异常
            // 权限认证失败时，设置 401 状态码
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            // 构建响应结果
            result = Response.fail(ResponseCodeEnum.UNAUTHORIZED.getErrorCode(), ResponseCodeEnum.UNAUTHORIZED.getErrorMessage());
        } else { // 其他异常，则统一提示 “系统繁忙” 错误
            result = Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
        }

        // 设置响应头的内容类型为 application/json;charset=UTF-8，表示响应体为 JSON 格式
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON_UTF8);
        // 设置 body 响应体
        return response.writeWith(Mono.fromSupplier(() -> { // 使用 Mono.fromSupplier 创建响应体 懒执行
            DataBufferFactory bufferFactory = response.bufferFactory();
            try {
                // 使用 ObjectMapper 将 result 对象转换为 JSON 字节数组
                return bufferFactory.wrap(objectMapper.writeValueAsBytes(result));
            } catch (Exception e) {
                // 如果转换过程中出现异常，则返回空字节数组
                return bufferFactory.wrap(new byte[0]);
            }
        }));
    }
}
```

但是这样就会发现一个问题

```Java
 // 根据捕获的异常类型，设置不同的响应状态码和响应消息
        if (ex instanceof SaTokenException) { // Sa-Token 异常
            // 权限认证失败时，设置 401 状态码
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            // 构建响应结果
            result = Response.fail(ResponseCodeEnum.UNAUTHORIZED.getErrorCode(), ResponseCodeEnum.UNAUTHORIZED.getErrorMessage());
        } else { // 其他异常，则统一提示 “系统繁忙” 错误
            result = Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
        }
```

无论任何的报错 我们都是返回的ResponseCodeEnum.UNAUTHORIZED的信息 也就是显示 **权限不足** 但是这样子是不友好的 因为我们实际上可能是token没有 或者无效 或者别的愿意  所以我们**需要精细化这个返回信息**

Sa-Token的超类异常SaTokenException下分为了很多的小异常，我们需要在异常捕获器分别捕获，然后大应对应的不同的信息

```
-- SaTokenException
    -- NotLoginException // 未登录异常
    -- NotPermissionException // 权限不足异常
    -- NotRoleException // 不具备对应角色异常
    -- ...
```

```Java
if (ex instanceof NotLoginException) {
            // 设置 401 状态码
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            // 构建响应结果  这里返回的信息使用异常自带的信息而不是枚举类中code对应的是因为 这个异常NotLoginException既包含了未登录，也包含了token无效（比如过期了）的异常情况 所以不好统一处理 直接使用自带的信息即可
            result = Response.fail(ResponseCodeEnum.UNAUTHORIZED.getErrorCode(), ex.getMessage());
        } else if (ex instanceof NotPermissionException) {
            // 权限认证失败时，设置 401 状态码
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            // 构建响应结果
            result = Response.fail(ResponseCodeEnum.UNAUTHORIZED.getErrorCode(), ResponseCodeEnum.UNAUTHORIZED.getErrorMessage());
        } else { // 其他异常，则统一提示 “系统繁忙” 错误
            result = Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
        }
```

**全局异常捕获完成之后，就需要在sa-token配置中，将删除的setError属性更改配置后再次添加**

**setError是sa-token内部的异常捕获 会在上面setAuth方法进行token，角色，权限校验出问题的时候进行异常的抛出，我们就在其内部进行特定的异常抛出即可，抛出后就会被全局异常捕获器捕获到，从而进行统一处理**

```Java
// 异常处理方法：每次setAuth函数出现异常时进入
                .setError(e -> {
                    // return SaResult.error(e.getMessage());
                    // 手动抛出异常，抛给全局异常处理器
                    if (e instanceof NotLoginException) { // 未登录异常
                        throw new NotLoginException(e.getMessage(), null, null);
                    } else if (e instanceof NotPermissionException || e instanceof NotRoleException) { // 权限不足，或不具备角色，统一抛出权限不足异常
                        throw new NotPermissionException(e.getMessage());
                    } else { // 其他异常，则抛出一个运行时异常
                        throw new RuntimeException(e.getMessage());
                    }
                })
```

# 将userId传递到下游

现在有个很大的问题，如果是我们的单体架构项目，我们可以将登录信息存储到ThreadLocal中，相当于将登录信息存储到程序上下文中，方便后续需要用户信息使用

但是我们现在是分布式服务，登录逻辑是在网关调用的auth接口，然后进行登录的，也就是说，**userID实际上只有auth模块能知道是什么，对于其他模块是隐藏的，这个就很不方便后续获取登录信息，这个该如何解决呢？**

我们的**网关模块，虽然不能直接得到userId，但是登录后返回的token以及后续的token校验都是在网关模块中，网关可以通过解析token从而得到userId的信息**，所有的服务请求肯定率先是传递给网关，那么**第二个问题来了，网关如何将解析出来的userID穿透传递给下游呢？ 因为模块与模块之间的引用实际上都是Http协议调用实现的  所以我们其实可以将解析出来的userId封装到Http请求中，从而让下游服务能得到登录用户信息**

**在gateway中，提供了一个全局过滤器GlobalFilter，能过滤所有的网关的请求，我们可以在过滤器中对Htpp进行二次封装，将用户登录信息封装进去**

```Java
@Component
@Slf4j
public class AddUserId2HeaderFilter implements GlobalFilter {
    /**
     * 请求头中，用户 ID 的键
     */
    private static final String HEADER_USER_ID = "userId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("==================> TokenConvertFilter");
        // 用户 ID
        Long userId = null;
        try {
            // 获取当前登录用户的 ID
            // StpUtil.getLoginIdAsLong(); : 通过 SaToken 工具类获取当前用户 ID。如果请求中携带了 Token 令牌，则会获取成功；如果未携带，能执行到这里，说明请求的接口无需权限校验，这个时候获取用户 ID 会报抛异常， catch() 到异常后，不做任何修改，将请求传递给过滤器链中的下一个过滤器，发行请求即可，这里抛异常会被后续的异常捕获器捕获到不会进入到下游中
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            // 若没有登录，则直接放行
            return chain.filter(exchange);
        }

        log.info("## 当前登录的用户 ID: {}", userId);

        Long finalUserId = userId;
        ServerWebExchange newExchange = exchange.mutate()
                // 将用户 ID 设置到请求头中
                .request(builder -> builder.header(HEADER_USER_ID, String.valueOf(finalUserId)))
                .build();
        return chain.filter(newExchange);
    }
}

GlobalFilter : 这是一个全局过滤器接口，会对所有通过网关的请求生效。
filter() 入参解释：
ServerWebExchange exchange：表示当前的 HTTP 请求和响应的上下文，包括请求头、请求体、响应头、响应体等信息。可以通过它来获取和修改请求和响应。
GatewayFilterChain chain：代表网关过滤器链，通过调用 chain.filter(exchange) 方法可以将请求传递给下一个过滤器进行处理。
chain.filter(exchange) : 将请求传递给过滤器链中的下一个过滤器进行处理。当前没有对请求进行任何修改。
```

这里有涉及到了一个**过滤器的优先级问题**，因为大家可以发现，**Sa-Token的对于接口访问的权限和角色过滤，实际上也是一个过滤器（也就是Sa-Token的那个配置文件）**

![image-20240926201832922](../../ZealSingerBook/img/image-20240926201832922.png)

那么现在就会出现一个问题，如果GlobalFilter过滤器比Sa-Token的鉴权过滤器先执行，那么就会出现存入无用用户信息或者出现用户信息为null的情况，这样肯定是不对的，我们应该让Sa-Token的鉴权过滤比全局过滤器优先执行，这样就能**保证放入到下游的userId不为null，并且通过的鉴权，保证用户登录安全和可用性，或者是不需要权限鉴权的操作**

如何实现这个执行先后关系呢？这个**需要用到Spring中的一个过滤器的权重注解@Order**

可以看到sa-token中的过滤器中标记了注解@Order(-100)  值越小 权重越大  优先级越高  而我们的全局GlobalFilter没有设置数值的，也就是默认值，自然是优先级比sa-toekn小的，所以必定会先经过sa-token的鉴权过滤器，然后才是我们的过滤器去添加用户信息

![image-20240926202752487](../../ZealSingerBook/img/image-20240926202752487.png)

## 下游接收user信息

顺带说一下下游接收处理，因为网关是将userid信息放到了请求头中

```
.request(builder -> builder.header(HEADER_USER_ID, String.valueOf(finalUserId)))
```

所以我们可以直接从请求头中获取  如图 给推出登录的接口进行接收打印 

![image-20240926203309718](../../ZealSingerBook/img/image-20240926203309718.png)

apifox请求测试一下  可以看到  auth模块中和网关日志中均有体现userId的获取

![image-20240926203527603](../../ZealSingerBook/img/image-20240926203527603.png)

![image-20240926203639148](../../ZealSingerBook/img/image-20240926203639148.png)

![image-20240926203651274](../../ZealSingerBook/img/image-20240926203651274.png)

# 完善退出登录逻辑

退出登录的逻辑很简单，就是利用SaToken的工具类调用logout方法即可

```java
StpUtil.logout(userId)
```

![image-20240926204242404](../../ZealSingerBook/img/image-20240926204242404.png)

![image-20240926204302386](../../ZealSingerBook/img/image-20240926204302386.png)

![image-20240926204310437](../../ZealSingerBook/img/image-20240926204310437.png)

当我们再次用相同的token调用退出登录接口的时候，就会出现token无效的异常信息  说明退出登录成功

![image-20240926204346581](../../ZealSingerBook/img/image-20240926204346581.png)

# 下游获取userId统一配置

## ThreadLocal方式统一获取userId，保存至程序上下文

可以看到,我们的下游获取userId的方式是在接口处添加请求头中信息的获取,这样子就会很麻烦  总不能每一个都多写一个对请求头的中userID的获取吧?!

![image-20240926211615620](../../ZealSingerBook/img/image-20240926211615620.png)

所以,我们可以在下游封装一个过滤器,用于将请求中的信息封装到ThreadLocal中,从而方便后续拿取数据  集成类 OncePerRequestFilter (Token过滤器中经常使用的) 当然 还得准备一个封装了ThreadLocal和ThreadLocal相关操作的类,用于设置与获取上下文数据

```Java
public class LoginUserContextHolder {
    // 初始化一个 ThreadLocal 变量 withInitial方法接收一个Supplier函数接口 可以让每个线程第一次调用get方法时延迟初始化变量 也就是惰性加载 要使用的时候才会ThreadLocal中创建HashMap
    private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
            = ThreadLocal.withInitial(HashMap::new);

    public static void set(String key, Object value) {
        Map<String, Object> map = LOGIN_USER_CONTEXT_THREAD_LOCAL.get();
        map.put(key, value);
    }

    public static Object get(String key) {
        Map<String, Object> map = LOGIN_USER_CONTEXT_THREAD_LOCAL.get();
        return Objects.isNull(map) ? null : map.get(key);
    }


    /**
     * 设置用户 ID
     *
     * @param value
     */
    public static void setUserId(Object value) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.get().put(GlobalConstants.USER_ID, value);
    }

    /**
     * 获取用户 ID
     *
     * @return
     */
    public static Long getUserId() {
        Object value = LOGIN_USER_CONTEXT_THREAD_LOCAL.get().get(GlobalConstants.USER_ID);
        if (Objects.isNull(value)) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    /**
     * 删除 ThreadLocal
     */
    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }
}
```

```Java
@Component
@Slf4j
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(GlobalConstants.USER_ID);
        log.info("## HeaderUserId2ContextFilter, 用户 ID: {}", userId);
        if(StringUtils.isNotBlank(userId)){
            LoginUserContextHolder.setUserId(userId);
            log.info("## 用户 ID: {} 存入ThreadLocal", userId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                // 一定要删除 ThreadLocal ，防止内存泄露  需要注意ThreadLocal中的内存泄露问题(看八股笔记)
                LoginUserContextHolder.remove();
                log.info("===== 删除 ThreadLocal， userId: {}", userId);
            }
        }else{
            filterChain.doFilter(request, response);
        }
    }
}
```

那么在下游的接口中,就能使用ThreadLocal获取userId  修改之前的退出登录的逻辑  不采用传参和@RequestHandler的方式接收uerId

![image-20240926212219186](../../ZealSingerBook/img/image-20240926212219186.png)

## ThreadLocal异步问题

根据实际情况和ThreadLocal底层原理其实不难发现，ThreadLocal之所以能实现线程上下文共享数据，是因为内部是一个以Thread为key的Map，所以同一个Thread内肯定是能拿到数据的，但是问题又来了，例如我们的阿里云发送消息的逻辑，是**依靠线程池完成的异步任务，而异步任务自然是不同于主线程的，这就会导致异步任务中如果需要userId，就无法从我们上面定义的ThreaLocal中获取**

```Java
// 假设退出登录的逻辑我们这样写
@Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();

        log.info("==> 用户退出登录, userId: {}", userId);

        threadPoolTaskExecutor.submit(() -> {
            Long userId2 = LoginUserContextHolder.getUserId();
            log.info("==> 异步线程中获取 userId: {}", userId2);
        });

        // 退出登录 (指定用户 ID)
        StpUtil.logout(userId);

        return Response.success();
    }
```

- 解决设想一    使用  **`InheritableThreadLocal`** （能解决部分 但是还有部分场景无法解决）

**`InheritableThreadLocal`**是ThreadLocal的子类，是Java中提供的，它允许存入里面的数据，除了操作线程之间会进行共享，也允许父子线程之间进行数据共享，如下

```java 
    public static void main(String[] args) {
    
    	// 初始化 InheritableThreadLocal
        ThreadLocal<Long> threadLocal = new InheritableThreadLocal<>();
        
        // 假设用户 ID 为 1
        Long userId = 1L;
        
        // 设置用户 ID 到 InheritableThreadLocal 中
        threadLocal.set(userId);
        
        System.out.println("主线程打印用户 ID: " + threadLocal.get());

		// 异步线程  主线程下创建的子线程  也可以拿到这个数据
        new Thread(() -> {
            System.out.println("异步线程打印用户 ID: " + threadLocal.get());
        }).start();
    }

```

可以看到，**InheritableThreadLocal还是比ThreadLocal能应付更多的场景，但是要知道，我们分布式服务中为了保证每个服务尽可能的多承受，我们一般不会使用子线程的，而是使用线程池，对于线程池而言，我们每个线程的时候都是创建一个新线程，和主线程不一定存在明确的父子关系，新线程的创建也不一定是全部由主线程创建的，子线程之间也不会存在一定的关系，所以也不是很适用**

![image-20240927085344164](../../ZealSingerBook/img/image-20240927085344164.png)

- 使用阿里的 TransmittableThreadLocal（解决方案）

`TransmittableThreadLocal` 是阿里巴巴开源的一个库，专门为了解决在使用线程池或异步执行框架时，`InheritableThreadLocal` 不能传递父子线程上下文的问题。`TransmittableThreadLocal` 能够将父线程中的上下文在子线程或线程池中执行时也能够保持一致。

导入依赖

```Java
	// 父模块中
	<properties>
		// 省略...
        <transmittable-thread-local.version>2.14.2</transmittable-thread-local.version>
    </properties>
    
        <!-- 统一依赖管理 -->
    <dependencyManagement>
        <dependencies>
			// 省略...

            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>transmittable-thread-local</artifactId>
                <version>${transmittable-thread-local.version}</version>
            </dependency>


        </dependencies>
    </dependencyManagement>


// 认证模块中
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
</dependency>


```

我们先看原本的逻辑中，我们测试退出登录接口，实现线程池和子线程的方式分别从ThreadLocal中获取userId并且打印，可以看到如下结果  可以看到 无论是子线程还是线程池 都是获取不到的

![image-20240927091618414](../../ZealSingerBook/img/image-20240927091618414.png)

![image-20240927092215115](../../ZealSingerBook/img/image-20240927092215115.png)

然后我们修改ThreadLocal的类型

```Java
//原本
private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
            = ThreadLocal.withInitial(HashMap::new);
            
//现在
private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
            = TransmittableThreadLocal.withInitial(HashMap::new);
```

然后我们再次访问接口查看输出  这次可以看到 无论是子线程还是线程池中都能获取到了     So Cool

![image-20240927092918626](../../ZealSingerBook/img/image-20240927092918626.png)
