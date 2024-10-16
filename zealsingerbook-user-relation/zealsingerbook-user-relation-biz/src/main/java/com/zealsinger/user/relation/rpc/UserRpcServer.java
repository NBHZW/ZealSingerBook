package com.zealsinger.user.relation.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.api.UserFeignApi;
import com.zealsinger.user.dto.CheckUserExistReqDTO;
import com.zealsinger.user.dto.FindUserByIdRspDTO;
import com.zealsinger.user.dto.FindUsersByIdsReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

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


    public List<FindUserByIdRspDTO> findUserByIds(List<Long> userIds){
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO(userIds);
        Response<List<FindUserByIdRspDTO>> rcpResponse = userFeignApi.findByIds(findUsersByIdsReqDTO);
        if(rcpResponse==null || !rcpResponse.isSuccess()){
            return null;
        }
        return rcpResponse.getData();
    }
}
