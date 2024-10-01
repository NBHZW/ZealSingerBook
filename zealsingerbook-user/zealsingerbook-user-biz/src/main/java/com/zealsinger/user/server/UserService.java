package com.zealsinger.user.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.domain.entity.User;
import com.zealsinger.user.domain.vo.UpdateUserInfoReqVO;

public interface UserService extends IService<User> {
    Response<?> updateUserInfo(UpdateUserInfoReqVO vo);
}
