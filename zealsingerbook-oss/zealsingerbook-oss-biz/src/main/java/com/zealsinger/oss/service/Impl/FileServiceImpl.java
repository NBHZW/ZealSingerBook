package com.zealsinger.oss.service.Impl;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.oss.factory.FileStrategyFactory;
import com.zealsinger.oss.service.FileService;
import io.minio.errors.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class FileServiceImpl implements FileService {
    @Resource
    private FileStrategyFactory fileStrategyFactory;

    private final String BUCKET_NAME="zealsinger-book-bucket";

    @Override
    public Response<?> uploadFile(MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String url = fileStrategyFactory.getFileStrategy().upload(file, BUCKET_NAME);
        return Response.success(url);
    }
}
