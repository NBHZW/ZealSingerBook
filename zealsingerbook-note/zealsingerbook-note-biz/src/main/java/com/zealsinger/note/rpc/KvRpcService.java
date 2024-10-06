package com.zealsinger.note.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.api.KVFeignApi;
import com.zealsinger.kv.dto.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 远程调用KV存储服务
 */
@Component
public class KvRpcService {
    @Resource
    private KVFeignApi kvFeignApi;

    /**
     * 新增笔记
     */
    public Boolean addNoteContent(String uuid,String noteContent){
        AddNoteContentReqDTO reqDTO = new AddNoteContentReqDTO();
        reqDTO.setId(uuid);
        reqDTO.setContent(noteContent);
        Response<?> response = kvFeignApi.addNoteContent(reqDTO);
        if(Objects.isNull(response)){
            return false;
        }
        return response.isSuccess();
    }

    /**
     * 删除笔记
     */
    public Boolean deleteNoteContent(String uuid){
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = new DeleteNoteContentReqDTO();
        deleteNoteContentReqDTO.setId(uuid);
        Response<?> response = kvFeignApi.deleteNoteContent(deleteNoteContentReqDTO);
        if(Objects.isNull(response)){
            return false;
        }
        return response.isSuccess();
    }

    public FindNoteContentRspDTO findNoteContentById(String uuid){
        FindNoteContentReqDTO findNoteContentReqDTO = FindNoteContentReqDTO.builder().id(uuid).build();
        Response<FindNoteContentRspDTO> noteContent = kvFeignApi.findNoteContent(findNoteContentReqDTO);
        if(Objects.isNull(noteContent) || !noteContent.isSuccess()){
            return null;
        }
        return noteContent.getData();
    }

    public Boolean updateNoteContent(String contentUuid,String content){
        UpdateNoteContentReqDTO updateNoteContentReqDTO = UpdateNoteContentReqDTO.builder().contentUuid(contentUuid).content(content).build();
        Response<?> response = kvFeignApi.updateNoteContent(updateNoteContentReqDTO);
        if(Objects.isNull(response)){
            return false;
        }
        return response.isSuccess();
    }
}
