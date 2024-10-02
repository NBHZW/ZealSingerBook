package com.zealsinger.oss.strategy.Impl;

import com.alibaba.nacos.api.common.ResponseCode;
import com.aliyun.oss.OSS;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.oss.config.AliyunOssProperties;
import com.zealsinger.oss.enums.ResponseCodeEnum;
import com.zealsinger.oss.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

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
            throw new BusinessException(ResponseCodeEnum.FILE_BLANK_ERROR);
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
