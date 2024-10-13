package com.zealsinger.user.relation.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.api.UserFeignApi;
import com.zealsinger.user.dto.CheckUserExistReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class UserRpcServer {
    @Resource
    private UserFeignApi userFeignApi;

    public Boolean checkUserExist(Long userId){
        CheckUserExistReqDTO checkUserExistReqDTO = new CheckUserExistReqDTO(userId);
        Response<Boolean> booleanResponse = userFeignApi.checkUserExist(checkUserExistReqDTO);
        if(booleanResponse==null){
            return false;
        }
        return booleanResponse.getData();
    }
}
