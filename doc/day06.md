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