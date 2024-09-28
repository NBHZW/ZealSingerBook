package com.zealsinger.oss.factory;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.oss.strategy.FileStrategy;
import com.zealsinger.oss.strategy.Impl.AliyunOssFileStrategyImpl;
import com.zealsinger.oss.strategy.Impl.MinioFileStrategyImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@RefreshScope
public class FileStrategyFactory {
    @Value("${storage.type}")
    private String storageType;

    @Bean
    @RefreshScope
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
