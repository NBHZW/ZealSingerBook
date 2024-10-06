package com.zealsinger.user.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.constanst.ApiConstants;
import com.zealsinger.user.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


/**
 * 文件服务feign接口
 * @author zealsinger
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {

    String PREFIX = "/user";
    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);

    @PostMapping(value = PREFIX + "/findByPhone")
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    @PostMapping(value = PREFIX + "/updatePassword")
    Response<?> updatePassword(@RequestBody UpdatePasswordReqDTO updatePasswordReqDTO);

    @PostMapping(value = PREFIX + "/findById")
    Response<FindUserByIdRspDTO> findById(@RequestBody FindUserByIdReqDTO findUserByIdReqDTO);
}
