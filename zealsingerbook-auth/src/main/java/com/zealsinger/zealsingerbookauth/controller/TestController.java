package com.zealsinger.zealsingerbookauth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.zealsingerbookauth.domain.entity.UserDO;
import com.zealsinger.zealsingerbookauth.mapper.UserDOMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestController {

    @Resource
    private UserDOMapper userTestMapper;

    @GetMapping("/response")
    @ZealLog(description = "统一Response格式返回测试And自定义切面日志测试")
    public Response<?> testResponse(){
        // return Response.success("ZealSinger - Response success 返回测试");
        return Response.fail("ZealSinger - Response fail 返回测试");
    }

    @PostMapping("/test/jackson")
    @ZealLog(description = "jackson统一配置测试")
    public Response<?> testJackson(@RequestBody UserDO user){
        return Response.success(user);
    }

    @GetMapping("/exceptionhandler")
    @ZealLog(description = "全局异常捕获器测试")
    public Response<?> testException(){
        int i =1/0;
        return null;
    }

    @GetMapping("/user/login")
    @ZealLog(description = "Sa-Token登录接口测试")
    public Response<?> testLogin(String username,String password){
        if("zealsinger".equals(username) && "123123".equals(password)){
            StpUtil.login(1000);
            return Response.success("登录成功");
        }
        return Response.fail("登录失败");
    }

    @GetMapping("/user/islogin")
    @ZealLog(description = "测试Sa-Token登录状态查询接口测试")
    public Response<?> isLogin(){
        return Response.success("当前会话登录状态: " + StpUtil.isLogin());
    }
}
