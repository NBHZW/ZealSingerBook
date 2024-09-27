package com.zealsinger.zealsingerbookauth.server;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.zealsingerbookauth.domain.vo.UpdatePasswordReqVO;
import com.zealsinger.zealsingerbookauth.domain.vo.UserLoginReqVO;

public interface UserService {
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    Response<?> logout();

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);

}
