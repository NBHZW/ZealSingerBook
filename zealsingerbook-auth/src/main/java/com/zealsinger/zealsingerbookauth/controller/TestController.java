package com.zealsinger.zealsingerbookauth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
@RequestMapping("/test")
public class TestController {


    @GetMapping("/response")
    @ZealLog(description = "统一Response格式返回测试And自定义切面日志测试")
    public Response<?> testResponse(){
        // return Response.success("ZealSinger - Response success 返回测试");
        return Response.fail("ZealSinger - Response fail 返回测试");
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


    @Value("${rate-limit.api.limit}")
    private Integer limit;
    @GetMapping("/nacos/limit")
    @ZealLog(description = "nacos热部署限流配置属性配置")
    public String testNacosLimit(){
        return "当前接口限流配置为: " + limit;
    }
}
