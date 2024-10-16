package com.zealsinger.user.relation.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.user.relation.domain.dto.FollowReqDTO;
import com.zealsinger.user.relation.domain.dto.UnFollowReqDTO;
import com.zealsinger.user.relation.domain.vo.FindFansListReqVO;
import com.zealsinger.user.relation.domain.vo.FindFollowingListReqVO;

public interface UserRelationServer{
    Response<?> follow(FollowReqDTO followReqDTO);

    Response<?> unfollow(UnFollowReqDTO unFollowReqDTO);

    Response<?> list(FindFollowingListReqVO findFollowingListReqVO);

    Response<?> findFansListReqVO(FindFansListReqVO findFansListReqVO);
}
