package com.zealsinger.oss.strategy.Impl;

import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.oss.config.MinioProperties;
import com.zealsinger.oss.enums.ResponseCodeEnum;
import com.zealsinger.oss.strategy.FileStrategy;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import jakarta.annotation.Resource;
import lombok.extern.flogger.Flogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;


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
            throw new BusinessException(ResponseCodeEnum.FILE_BLANK_ERROR);
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
