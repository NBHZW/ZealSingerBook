package com.zealsinger.user.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.domain.entity.User;
import com.zealsinger.user.domain.enums.ResponseCodeEnum;
import com.zealsinger.user.domain.vo.UpdateUserInfoReqVO;
import com.zealsinger.user.dto.FindUserByPhoneReqDTO;
import com.zealsinger.user.dto.FindUserByPhoneRspDTO;
import com.zealsinger.user.dto.RegisterUserReqDTO;
import com.zealsinger.user.dto.UpdatePasswordReqDTO;
import com.zealsinger.user.server.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {
    @Resource
    private UserService userService;

    @PostMapping(value = "/update")
    public Response<?> updateUserInfo(@Validated @ModelAttribute UpdateUserInfoReqVO vo) {
        return userService.updateUserInfo(vo);
    }

    // ===================================== 对其他服务提供的接口 ===================================== //
    @PostMapping("/register")
    @ZealLog(description = "用户注册")
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO) {
        return userService.register(registerUserReqDTO);
    }

    @PostMapping("/findByPhone")
    @ZealLog(description = "根据手机号查询用户信息")
    public Response<FindUserByPhoneRspDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        return userService.findByPhone(findUserByPhoneReqDTO);
    }

    @PostMapping("/updatePassword")
    @ZealLog(description = "修改密码")
    public Response<?> updatePassword(@Validated @RequestBody UpdatePasswordReqDTO updatePasswordReqDTO) {
        return userService.updatePassword(updatePasswordReqDTO);
    }

}
