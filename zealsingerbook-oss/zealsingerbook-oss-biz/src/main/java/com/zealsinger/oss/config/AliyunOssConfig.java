package com.zealsinger.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AliyunOssConfig {
    @Resource
    private AliyunOssProperties aliyunOssProperties;
    @Bean
    public OSS aliyunOSSClient() {
        return new OSSClientBuilder().build(aliyunOssProperties.getEndpoint(), aliyunOssProperties.getAccessKey(), aliyunOssProperties.getSecretKey());
    }
}
