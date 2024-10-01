package com.zealsinger.user.server.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Preconditions;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.ParamUtils;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.oss.api.FileFeignApi;
import com.zealsinger.user.domain.entity.User;
import com.zealsinger.user.domain.enums.ResponseCodeEnum;
import com.zealsinger.user.domain.enums.SexEnum;
import com.zealsinger.user.domain.vo.UpdateUserInfoReqVO;
import com.zealsinger.user.mapper.UserMapper;
import com.zealsinger.user.server.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final int MAX_INTRODUCTION_LEN=100;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FileFeignApi fileFeignApi;

    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO vo) {
        // 创建初始user对象
        Long userId = LoginUserContextHolder.getUserId();
        User user = new User();
        user.setId(userId);

        // 是否需要更新的标识
        boolean needUpdate = false;

        // 头想 和 背景图片
        MultipartFile avatar = vo.getAvatar();
        MultipartFile background = vo.getBackground();
        if(avatar != null) {
            // TODO 调用对象存储服务
            fileFeignApi.test();
        }
        if(background != null) {
            //TODO 调用对象存储服务
        }

        // 昵称
        String nickname = vo.getNickname();
        if(StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getErrorMessage());
            user.setNickname(nickname);
            needUpdate = true;
        }

        //ID
        String zealsingerBookId = vo.getZealsingerBookId();
        if(StringUtils.isNotBlank(zealsingerBookId)) {
            Preconditions.checkArgument(ParamUtils.checkId(zealsingerBookId), ResponseCodeEnum.ZEALSINGERBOOK_ID_VALID_FAIL.getErrorMessage());
            user.setZealsingerBookId(zealsingerBookId);
            needUpdate = true;
        }

        // 性别
        Integer sex = vo.getSex();
        if(sex!=null){
            Preconditions.checkArgument(SexEnum.isValid(sex),ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            user.setSex(sex);
            needUpdate = true;
        }

        // 介绍
        String introduction = vo.getIntroduction();
        if(StringUtils.isNotBlank(introduction)){
            Preconditions.checkArgument(ParamUtils.checkLength(introduction,MAX_INTRODUCTION_LEN),ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            user.setIntroduction(introduction);
            needUpdate = true;
        }

        // 生日
        LocalDateTime birthday = vo.getBirthday();
        if (Objects.nonNull(birthday)) {
            user.setBirthday(birthday);
            needUpdate = true;
        }

        if(needUpdate){
            userMapper.updateById(user);
        }

        return Response.success();
    }
}
