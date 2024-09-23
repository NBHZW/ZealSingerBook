package com.zealsinger.zealsingerbookauth.server.Impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.collect.Lists;
import com.zealsinger.book.framework.common.enums.DeletedEnum;
import com.zealsinger.book.framework.common.enums.StatusEnum;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.util.JsonUtil;
import com.zealsinger.zealsingerbookauth.constant.RedisConstant;
import com.zealsinger.zealsingerbookauth.constant.RoleConstants;
import com.zealsinger.zealsingerbookauth.domain.entity.User;
import com.zealsinger.zealsingerbookauth.domain.entity.UserRole;
import com.zealsinger.zealsingerbookauth.domain.enums.LoginTypeEnum;
import com.zealsinger.zealsingerbookauth.domain.enums.ResponseCodeEnum;
import com.zealsinger.zealsingerbookauth.domain.vo.UserLoginReqVO;
import com.zealsinger.zealsingerbookauth.mapper.UserMapper;
import com.zealsinger.zealsingerbookauth.mapper.UserRoleMapper;
import com.zealsinger.zealsingerbookauth.server.VerificationCodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Resource
    private UserMapper userMapper;

    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;



    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        // 根据不同的类型进行判断逻辑
        // 如果为验证码登录
        SaTokenInfo tokenInfo = null;
        Long userId = null;
        if(LoginTypeEnum.VERIFICATION_CODE.getValue().equals(userLoginReqVO.getType())){
            // 验证码登录 先检测验证码
            String key = RedisConstant.getVerificationCodeKeyPrefix(userLoginReqVO.getPhoneNumber());
            Object o = redisTemplate.opsForValue().get(key);
            if(o == null){
                throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
            }else{
                // 如果验证码正确
                if(String.valueOf(o).equals(userLoginReqVO.getCode())){
                    // 检测是否存在用户 如果有 则直接登录 没有则登录+注册添加用户信息
                    User user = userMapper.selectByPhone(userLoginReqVO.getPhoneNumber());
                    if(user==null){
                        userId = registerUser(userLoginReqVO.getPhoneNumber());
                        log.info("===>用户 {} 注册成功",userId);
                    }else{
                        userId = user.getId();
                    }
                    StpUtil.login(userId);
                    log.info("===>用户 {} 登录成功",userId);
                    tokenInfo = StpUtil.getTokenInfo();
                    return Response.success(tokenInfo.tokenValue);
                }else{
                    // 验证码不正确 抛出异常
                    throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }
            }
        }else{
            // 账号密码登录

        }
        return null;
    }

    /**
     * 系统自动注册用户
     * @param phone
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerUser(String phone) {
        // 获取全局自增的小哈书 ID
        Long zealId = redisTemplate.opsForValue().increment(RedisConstant.ZEALSINGER_BOOK_ID_GENERATOR_KEY);

        User userDO = User.builder()
                .phone(phone)
                .zealsingerBookId(String.valueOf(zealId)) // 自动生成 账号ID
                .nickname("zealsingerbook" + zealId) // 自动生成昵称, 如：zealsingerbook10000
                .status(StatusEnum.ENABLE.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
                .build();

        // 添加入库
        userMapper.insert(userDO);

        // 获取刚刚添加入库的用户 ID
        Long userId = userDO.getId();

        // 给该用户分配一个默认角色
        UserRole userRoleDO = UserRole.builder()
                .userId(userId)
                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();
        userRoleMapper.insert(userRoleDO);

        // 将该用户的角色 ID 存入 Redis 中
        List<Long> roles = Lists.newArrayList();
        roles.add(RoleConstants.COMMON_USER_ROLE_ID);
        String userRolesKey = RedisConstant.buildUserRoleKey(phone);
        redisTemplate.opsForValue().set(userRolesKey, JsonUtil.ObjToJsonString(roles));

        return userId;
    }
}
