package com.zealsinger.user.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.domain.enums.ResponseCodeEnum;
import com.zealsinger.user.domain.vo.UpdateUserInfoReqVO;
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



    /*
   @PostMapping(value = "/update")
   public Response<?> updateUserInfo(
            @RequestParam("zealsingerBookId") String zealsingerBookId,
            @RequestParam("nickname") String nickname,
            @RequestParam("sex") Integer sex,
            @RequestParam("birthday") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String birthday,
            @RequestParam("introduction") String introduction,
            @RequestParam("avatar") MultipartFile avatar,
            @RequestParam("background") MultipartFile background) {

        UpdateUserInfoReqVO vo = new UpdateUserInfoReqVO();
        vo.setZealsingerBookId(zealsingerBookId);
        vo.setNickname(nickname);
        vo.setSex(sex);
        vo.setBirthday(LocalDateTime.parse(birthday, DateTimeFormatter.ISO_DATE));
        vo.setIntroduction(introduction);
        vo.setAvatar(avatar);
        vo.setBackground(background);

        return userService.updateUserInfo(vo);
    }
     */


}
