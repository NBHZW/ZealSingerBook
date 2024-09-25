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
