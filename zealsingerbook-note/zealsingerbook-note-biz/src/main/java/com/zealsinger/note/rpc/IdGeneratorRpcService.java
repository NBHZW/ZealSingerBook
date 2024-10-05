package com.zealsinger.note.rpc;

import com.zealsinger.id.generator.api.IdGeneratorApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 远程调用Leaf生成ID服务
 */
@Component
public class IdGeneratorRpcService {

    @Resource
    private IdGeneratorApi generatorApi;

    private final String NOTE_ID_KEY = "note_id";

    /**
     * 生成笔记ID （那个鬼UUID）
     */
    public String getNoteId() {
        return generatorApi.getSnowflakeId(NOTE_ID_KEY);
    }
}
