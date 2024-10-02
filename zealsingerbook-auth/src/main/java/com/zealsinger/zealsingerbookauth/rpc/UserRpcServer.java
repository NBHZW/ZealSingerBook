package com.zealsinger.zealsingerbookauth.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.api.UserFeignApi;
import com.zealsinger.user.dto.FindUserByPhoneReqDTO;
import com.zealsinger.user.dto.FindUserByPhoneRspDTO;
import com.zealsinger.user.dto.RegisterUserReqDTO;
import com.zealsinger.user.dto.UpdatePasswordReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 用户模块的rpc服务
 */
@Component
public class UserRpcServer {
    @Resource
    private UserFeignApi userFeignApi;

    public Long registerUser(String phone) {
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();
        registerUserReqDTO.setPhone(phone);
        Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);
        if(response.isSuccess()){
            return response.getData();
        }
        return null;
    }


    public FindUserByPhoneRspDTO findUserByPhone(String phone) {
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();
        findUserByPhoneReqDTO.setPhone(phone);

        Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(findUserByPhoneReqDTO);

        if (!response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    public void updatePassword(String password) {
        UpdatePasswordReqDTO updatePasswordReqDTO = new UpdatePasswordReqDTO();
        updatePasswordReqDTO.setPassword(password);
        userFeignApi.updatePassword(updatePasswordReqDTO);
    }
}
