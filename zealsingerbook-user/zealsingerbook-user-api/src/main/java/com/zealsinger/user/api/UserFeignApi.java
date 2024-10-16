package com.zealsinger.user.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.constanst.ApiConstants;
import com.zealsinger.user.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;


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
    Response<Long> registerUser(@RequestBody  @Validated RegisterUserReqDTO registerUserReqDTO);

    @PostMapping(value = PREFIX + "/findByPhone")
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody  @Validated FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    @PostMapping(value = PREFIX + "/updatePassword")
    Response<?> updatePassword(@RequestBody  @Validated UpdatePasswordReqDTO updatePasswordReqDTO);

    @PostMapping(value = PREFIX + "/findById")
    Response<FindUserByIdRspDTO> findById(@RequestBody @Validated FindUserByIdReqDTO findUserByIdReqDTO);

    @PostMapping(value = PREFIX + "/checkUserExist")
    Response<Boolean> checkUserExist(@RequestBody CheckUserExistReqDTO checkUserExistReqDTO);

    @PostMapping("/findByIds")
    Response<List<FindUserByIdRspDTO>> findByIds(@Validated @RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO);

}
