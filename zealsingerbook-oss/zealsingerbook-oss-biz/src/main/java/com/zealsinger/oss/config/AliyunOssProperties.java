package com.zealsinger.oss.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * aliyun oss配置
 * @author zealsinger
 */
@ConfigurationProperties(prefix = "aliyun")
@Component
@Data
public class AliyunOssProperties {
    @Value("${aliyun.endpoint}")
    private String endpoint;
    @Value("${aliyun.accessKey}")
    private String accessKey;
    @Value("${aliyun.secretKey}")
    private String secretKey;
}
