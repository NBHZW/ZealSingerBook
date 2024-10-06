package com.zealsinger.note.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.api.UserFeignApi;
import com.zealsinger.user.dto.FindUserByIdReqDTO;
import com.zealsinger.user.dto.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeign;

    public FindUserByIdRspDTO getUserInfoById(Long id){
        FindUserByIdReqDTO build = FindUserByIdReqDTO.builder().id(id).build();
        Response<FindUserByIdRspDTO> response = userFeign.findById(build);
        if(Objects.isNull(response) || !response.isSuccess()){
            return null;
        }
        return response.getData();
    }
}
