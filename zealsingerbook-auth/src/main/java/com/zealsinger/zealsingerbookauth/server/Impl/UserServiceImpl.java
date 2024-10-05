package com.zealsinger.zealsingerbookauth.server.Impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.user.dto.FindUserByPhoneRspDTO;
import com.zealsinger.zealsingerbookauth.domain.enums.LoginTypeEnum;
import com.zealsinger.zealsingerbookauth.domain.enums.ResponseCodeEnum;
import com.zealsinger.zealsingerbookauth.domain.vo.UpdatePasswordReqVO;
import com.zealsinger.zealsingerbookauth.domain.vo.UserLoginReqVO;
import com.zealsinger.zealsingerbookauth.rpc.UserRpcServer;
import com.zealsinger.zealsingerbookauth.server.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author ZealSinger
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Resource
    private UserRpcServer userRpcServer;

    @Resource
    private RedisTemplate<String,String> redisTemplate;

    @Resource
    private PasswordEncoder passwordEncoder;



    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        // 根据不同的类型进行判断逻辑
        // 如果为验证码登录
        SaTokenInfo tokenInfo = null;
        Long userId = null;
        String phoneNumber = userLoginReqVO.getPhoneNumber();
        if(LoginTypeEnum.VERIFICATION_CODE.getValue().equals(userLoginReqVO.getType())){
            // 验证码登录 先检测验证码
            String key = RedisConstant.getVerificationCodeKeyPrefix(phoneNumber);
            Object o = redisTemplate.opsForValue().get(key);
            if(o == null){
                throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
            }else{
                // 如果验证码正确
                if(String.valueOf(o).equals(userLoginReqVO.getCode())){
                    // 检测是否存在用户 如果有 则直接登录 没有则登录+注册添加用户信息
                    userId = userRpcServer.registerUser(phoneNumber);
                    if(Objects.isNull(userId)){
                        throw new BusinessException(ResponseCodeEnum.LOGIN_FAIL);
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
            FindUserByPhoneRspDTO userByPhone = userRpcServer.findUserByPhone(userLoginReqVO.getPhoneNumber());
            if(Objects.isNull(userByPhone)){
                throw new BusinessException(ResponseCodeEnum.USER_NOT_FOUND);
            }else{
                boolean matches = passwordEncoder.matches(userLoginReqVO.getPassword(), userByPhone.getPassword());
                if(matches){
                    StpUtil.login(userByPhone.getId());
                    log.info("===>用户 {} 登录成功",userByPhone.getId());
                    tokenInfo = StpUtil.getTokenInfo();
                    return Response.success(tokenInfo.tokenValue);
                }else{
                    throw new BusinessException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }
            }
        }
    }

    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();
        log.info("===>用户 {} 退出登录",userId);
        StpUtil.logout(userId);
        return Response.success();
    }

    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        String encodePassword = passwordEncoder.encode(updatePasswordReqVO.getPassword());
        userRpcServer.updatePassword(encodePassword);
        return Response.success();
    }
}
