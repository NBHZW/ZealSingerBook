# 对象存储服务模块

在ZealSingerBook项目中，会存在头想和背景图这些和图片相关的对象存储服务的需求，我们本次使用Minio对象存储

## Minio的搭建

使用docker搭建本地Minio

```
docker pull minio/minio  下载镜像
```

```bash
启动容器 挂载到本地  设置user和password
docker run -d \
   -p 9000:9000 \  外部操作的API端口
   -p 9090:9090 \  web管理端界面端口
   --name minio \
   -v /root/Docker/minio/data:/data \
   -e "MINIO_ROOT_USER=zealsinger" \
   -e "MINIO_ROOT_PASSWORD=zealsingerbook" \
   minio/minio server /data --console-address ":9090"

```

启动容器成功后 进入9090端口的管理端界面  按照设置的user和password成功登录即可

![image-20240928092825422](../../ZealSingerBook/img/image-20240928092825422.png)

在Minio下，最外一层是按照桶进行数据分区的，所以我们首先需要创建一个桶Bucket 设置桶名称  其余的默认即可创建成功

![image-20240928092941058](../../ZealSingerBook/img/image-20240928092941058.png)

![image-20240928093022789](../../ZealSingerBook/img/image-20240928093022789.png)

桶的初始属性是私有的，也就是不能被外部访问的，所以我们需要先修改Bucket的属性为publice  上图界面直接点机进入  就能进入到桶的属性界面 然后就可以修改访问权限属性

![image-20240928093157255](../../ZealSingerBook/img/image-20240928093157255.png)

测试一下加入图片  如下操作

![image-20240928093307677](../../ZealSingerBook/img/image-20240928093307677.png)

![image-20240928093330172](../../ZealSingerBook/img/image-20240928093330172.png)

可以看到我们刚刚上传的图片成功上传了  但是发现看不到这个图片是啥  如何看到和外部访问呢？

![image-20240928093406250](../../ZealSingerBook/img/image-20240928093406250.png)

方法一：在我们之前创建Minio容器的时候，挂载了data数据在我们的本地文件见，我们现在回去看，可以发现  自动创建了和Bucket同名字的文件夹，文件夹下面保存了上传到该桶的image图片

![image-20240928093642998](../../ZealSingerBook/img/image-20240928093642998.png)

方法二：***请求地址:端口号 + 桶名称 + 图片的名称* 可以进行网络访问**

如下 我们刚刚上传的图片的访问url为

```
192.168.17.131:9090/zealsinger-book-bucket/cmd1.png
```

![image-20240928093830444](../../ZealSingerBook/img/image-20240928093830444.png)

## 项目模块的搭建

类似于Auth模块，是一个单独功能的模块，其他模块远程调用我们的该模块中的接口从而实现图片上传和查找

Oss父模块基于整个zealsingerbook项目创建

![image-20240928094632991](../../ZealSingerBook/img/image-20240928094632991.png)

然后在该模块下创建子模块 oss-api  为后续RPC调用做准备

![image-20240928094709041](../../ZealSingerBook/img/image-20240928094709041.png)

然后同样创建子模块 oss-biz  属于OSS服务的业务模块  启动类模块  

同样的创建模块 但是这个是业务运行模块 自然是需要启动类和配置的

![image-20240928095440385](../../ZealSingerBook/img/image-20240928095440385.png)

maven依赖文件如下  需要启动 自然是需要SpringBoot的相关依赖

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.zealsinger</groupId>
        <artifactId>zealsingerbook-oss</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <groupId>com.zealsinger.oss</groupId>
    <artifactId>zealsingerbook-oss-biz</artifactId>
    <packaging>jar</packaging>

    <name>zealsingerbook-oss-biz</name>
    <description>对象存储业务层</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.zealsinger.framework</groupId>
            <artifactId>zealsingerbook-common</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>

```

application主配置文件信息  因为暂时没有写业务相关 所以暂时不配置其他配置文件的信息

```yml
server:
  port: 8081 # 项目启动的端口

spring:
  profiles:
    active: dev # 默认激活 dev 本地开发环境
```

日志记录配置logback-spring.xml  和 Auth模块改了服务名称为oss（如下）  其余的不变

```
<!-- 应用名称 -->
    <property scope="context" name="appName" value="oss"/>
```

# 策略模式+工厂模式 实现多Oss服务

目前这里是使用的Minio作为对象存储服务，但是后续，我们还可以加入使用阿里云OSS，七牛云等多种存储服务平台，为了方便后续拓展存储服务功能，我们对该模块进行策略模式+工厂模式编写Oss服务模块

之前在写RPC的时候 就已经用过了  简单而言就是 **创建一个接口  里面存在对应的方法  然后根据实现方式的定义不同的实现类  再定义一个工厂  通过读取配置文件信息 从而决定返回哪个实现类 从而实现对应方式的方法体**

例如这里  我们主体业务是将图片上传至Oss中，那么首先我们就有一个图片上传的总接口FileStrategy，该接口就定义了上传图片的方法

```Java
public interface FileStrategy {
    String upload(MultipartFile file, String bucketName);
}
```

我们假设暂时只会阿里云Oss服务和自己搭建的Minio服务，上传至不同的地方，所调用的API和方法体内的逻辑自然会有出入 ，所以我们**分别创建上传至Oss服务的对应的FileStrategy接口的实现类 和 上传至Minio的对应的FileStrategy的实现类**

```Java
@Slf4j
public class MinioFileStrategyImpl implements FileStrategy {
    @Override
    public String upload(MultipartFile file, String bucketName) {
        log.info("===>Minio进行存储服务中......");
        return null;
    }
}


@Slf4j
public class AliyunOssFileStrategyImpl implements FileStrategy {
    @Override
    public String upload(MultipartFile file, String bucketName) {
        log.info("===>阿里云Oss进行存储服务中");
        return null;
    }
}
```

然后**我们需要区分出什么时候使用阿里云什么时候使用Minio ，所以我们工厂和配置信息来进行决定，将使用存储类型放到配置文件中，后续可以放到nacos的配置信息中，从而方便热修改部署**

```yml
server:
  port: 8081 # 项目启动的端口
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
  profiles:
    active: dev # 默认激活 dev 本地开发环境

storage:
  type: aliyun # 对象存储类型
```

```Java
@Configuration
public class FileStrategyFactory {
    @Value("${storage.type}")
    private String storageType;

    @Bean  // 这个Bean不可少 如果少了 就会出现工厂注入失败 从而导致实现类注入失败 导致整个逻辑无法正常进行
    public  FileStrategy getFileStrategy() {
        if (storageType == null) {
            throw new IllegalArgumentException("不可用的存储类型");
        }

        switch (storageType) {
            case "aliyun":
                return new AliyunOssFileStrategyImpl();
            case "minio":
                return new MinioFileStrategyImpl();
            default:
                throw new IllegalArgumentException("不可用的存储类型");
        }

        /*  增强Switch方式返回
         return switch (storageType) {
            case "aliyun" -> new AliyunOssFileStrategyImpl();
            case "minio" -> new MinioFileStrategyImpl();
            default -> throw new IllegalArgumentException("不可用的存储类型");
        };
        */
    }

}
```

这里介绍了策略模式+工厂模式在项目中的应用，案例也是按照我们的模块需求讲述的，所以这里模块的搭建也很明了了

前面还是很经典的Controller调用Service  不同的地方在于 Service中注入工厂  通过工厂获得FileStrategry的实现类 从而实现不同逻辑的上传图片

![image-20240928110648766](../../ZealSingerBook/img/image-20240928110648766.png)

![image-20240928110806294](../../ZealSingerBook/img/image-20240928110806294.png)

最终MInio实现类和Oss实现类如下

```Java
@Slf4j
@Service
public class MinioFileStrategyImpl implements FileStrategy {
    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioProperties minioProperties;


    @Override
    public String upload(MultipartFile file, String bucketName) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        log.info("===>Minio进行存储服务中......");
        if(file==null || file.getSize()==0){
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new BusinessException(ResponseCodeEnum.FILE_BLANK_ERRO);
        }
        try{
            // 文件原始名字
            String originalFileName = file.getOriginalFilename();
            // 文件的 Content-Type
            String contentType = file.getContentType();
            // 获取文件的后缀，如 .jpg
            String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
            // 最终存入的名字
            String fileName =  UUID.randomUUID() + suffix;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());

            // 返回文件的访问链接
            String url = String.format("%s/%s/%s", minioProperties.getEndpoint(), bucketName, fileName);
            log.info("===>Minio存储服务完成，文件访问链接为：{}", url);
            return url;
        }catch (Exception e){
            log.error("===>Minio存储服务异常：{}", e.getMessage());
            throw new BusinessException(ResponseCodeEnum.MINIO_ERROR);
        }


    }
}
```

```Java
// 需要注意的是 阿里云OSS服务需要修改公共访问权限 否则URL贼复杂而且存在时效
@Slf4j
@Service
public class AliyunOssFileStrategyImpl implements FileStrategy {

    @Resource
    private OSS ossClient;

    @Resource
    private AliyunOssProperties aliyunOssProperties;

    @Override
    public String upload(MultipartFile file, String bucketName) {
        log.info("===>阿里云Oss进行存储服务中......");
        if(file==null || file.getSize()==0){
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new BusinessException(ResponseCodeEnum.FILE_BLANK_ERRO);
        }
        try{
            // 文件原始名字
            String originalFileName = file.getOriginalFilename();
            // 获取文件的后缀，如 .jpg
            String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
            // 最终存入的名字
            String fileName =  UUID.randomUUID() + suffix;
            InputStream fileStream = file.getInputStream();
            ossClient.putObject(bucketName,fileName,fileStream);
            ossClient.shutdown();
            String url ="https://"+bucketName+"."+aliyunOssProperties.getEndpoint()+"/"+fileName;
            return url;
        }catch (Exception e){
            log.error("==> Oss上传文件异常：{}",e.getMessage());
            throw new BusinessException(ResponseCodeEnum.ALIOSS_ERROR);
        }
    }
}
```

