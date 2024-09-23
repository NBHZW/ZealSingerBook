package com.zealsinger.zealsingerbookauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.zealsingerbookauth.domain.entity.User;
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