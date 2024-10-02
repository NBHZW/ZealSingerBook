package com.zealsinger.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.user.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    /**
     * 根据手机号查询记录
     * @param phone
     * @return
     */
    User selectByPhone(String phone);
}