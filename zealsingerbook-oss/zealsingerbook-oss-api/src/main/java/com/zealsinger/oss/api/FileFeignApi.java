package com.zealsinger.oss.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.oss.constanst.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;


/**
 * 文件服务feign接口
 * @author zealsinger
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface FileFeignApi {

    String PREFIX = "/file";

    @PostMapping(value = PREFIX + "/test")
    Response<?> test();

    @PostMapping(value = PREFIX+"/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);

}
