package com.zealsinger.search.controller;


import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.search.domain.vo.SearchNoteReqVO;
import com.zealsinger.search.domain.vo.SearchUserReqVO;
import com.zealsinger.search.server.SearchUserService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class UserSearchController {
    @Resource
    private SearchUserService searchService;

    @PostMapping("/user")
    private Response<?> searchUser(@RequestBody @Validated SearchUserReqVO searchUserReqVO){
        return searchService.searchUser(searchUserReqVO);
    }

    @PostMapping("/note")
    private Response<?> searchNote(@RequestBody @Validated SearchNoteReqVO searchNoteReqVO){
        return searchService.searchNote(searchNoteReqVO);
    }
}
