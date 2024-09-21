package com.zealsinger.zealsingerbookauth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AliyunSmsClientConfig {
    @Resource
    private AliyunAccessKeyProperties properties;

    @Bean
    public Client client(){
        Config config = new Config().setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint("dysmsapi.aliyuncs.com");
        try {
            return new Client(config);
        } catch (Exception e) {
            log.error("阿里短信服务客户端初始化失败~");
            throw new RuntimeException(e);
        }
    }
}
