package com.zealsinger.zealsingerbookauth.mapper;

import com.zealsinger.zealsingerbookauth.domain.entity.User;

public interface UserMapper {
    int deleteByPrimaryKey(Long id);

    int insert(User record);

    int insertSelective(User record);

    User selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(User record);

    int updateByPrimaryKey(User record);

    /**
     * 根据手机号查询记录
     * @param phone
     * @return
     */
    User selectByPhone(String phone);
}