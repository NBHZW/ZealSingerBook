package com.zealsinger.user.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.domain.entity.User;
import com.zealsinger.user.domain.vo.UpdateUserInfoReqVO;
import com.zealsinger.user.dto.*;

public interface UserService extends IService<User> {
    Response<?> updateUserInfo(UpdateUserInfoReqVO vo);

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    Response<?> updatePassword(UpdatePasswordReqDTO updatePasswordReqDTO);

    Response<?> findById(FindUserByIdReqDTO findUserByIdReqDTO);
}
