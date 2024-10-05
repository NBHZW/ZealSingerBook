package com.zealsinger.user.rpc;

import com.zealsinger.id.generator.api.IdGeneratorApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 远程调用Leaf生成ID服务
 */
@Component
public class IdGeneratorRpcService {
    private final String ZEALSINGER_BOOK_ID_GENERATOR = "leaf-segment-zealsingerbook-id";
    private final String ZEALSINGER_BOOK_USER_NAME_ID = "leaf-segment-user-id";

    @Resource
    private IdGeneratorApi generatorApi;

    public String getZealsingerBookId() {
        return generatorApi.getSegmentId(ZEALSINGER_BOOK_ID_GENERATOR);
    }

    public String getZealsingerBookUserName() {
        return "zealsingerbook"+generatorApi.getSegmentId(ZEALSINGER_BOOK_USER_NAME_ID);
    }
}
