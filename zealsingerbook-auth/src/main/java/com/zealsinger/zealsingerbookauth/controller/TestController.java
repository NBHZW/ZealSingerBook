package com.zealsinger.zealsingerbookauth.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.framework.common.response.Response;
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
}
