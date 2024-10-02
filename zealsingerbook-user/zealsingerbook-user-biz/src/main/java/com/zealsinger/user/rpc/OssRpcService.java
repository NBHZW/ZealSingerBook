package com.zealsinger.user.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.oss.api.FileFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对oss模块的调用服务
 */
@Component
public class OssRpcService {
    @Resource
    private FileFeignApi fileFeignApi;

    public String uploadFile(MultipartFile file) {
        Response<?> response = fileFeignApi.uploadFile(file);
        if (response.isSuccess()) {
            return (String) response.getData();
        }
        return null;
    }

}
