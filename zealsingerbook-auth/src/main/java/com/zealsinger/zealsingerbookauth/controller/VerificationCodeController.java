package com.zealsinger.zealsingerbookauth.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.zealsingerbookauth.domain.vo.SendVerificationCodeReqVO;
import com.zealsinger.zealsingerbookauth.server.VerificationCodeService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;

@RestController
@RequestMapping("/verification")
public class VerificationCodeController {

    @Resource
    private VerificationCodeService verificationCodeService;

    @PostMapping("/code/send")
    @ZealLog(description = "发送登录验证码")
    public Response<?> sendVerification(@Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO){
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }
}
