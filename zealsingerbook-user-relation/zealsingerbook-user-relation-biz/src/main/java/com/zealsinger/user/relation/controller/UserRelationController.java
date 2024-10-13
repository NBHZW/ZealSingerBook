package com.zealsinger.user.relation.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.relation.domain.dto.FollowReqDTO;
import com.zealsinger.user.relation.domain.dto.UnFollowReqDTO;
import com.zealsinger.user.relation.server.UserRelationServer;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/relation")
@RestController
public class UserRelationController {

    @Resource
    private UserRelationServer userRelationServer;

    @PostMapping("/follow")
    @ZealLog(description = "关注")
    public Response<?> follow(@Validated @RequestBody FollowReqDTO followReqDTO) {
        return userRelationServer.follow(followReqDTO);
    }

    @PostMapping("/unfollow")
    @ZealLog(description = "取关")
    public Response<?> unfollow(@Validated @RequestBody UnFollowReqDTO unFollowReqDTO){
        return userRelationServer.unfollow(unFollowReqDTO);
    }
}
