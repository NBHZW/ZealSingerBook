package com.zealsinger.zealsingerbookauth.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.zealsingerbookauth.domain.vo.UserLoginReqVO;
import com.zealsinger.zealsingerbookauth.server.Impl.UserService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    @ZealLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return userService.loginAndRegister(userLoginReqVO);
    }

    @PostMapping("/logout")
    public Response<?> logout() {
        return Response.success();
    }
}
