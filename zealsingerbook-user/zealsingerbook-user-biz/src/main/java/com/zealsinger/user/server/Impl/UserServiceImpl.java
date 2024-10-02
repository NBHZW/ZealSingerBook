package com.zealsinger.user.server.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.enums.DeletedEnum;
import com.zealsinger.book.framework.common.enums.StatusEnum;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.book.framework.common.utils.ParamUtils;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.oss.api.FileFeignApi;
import com.zealsinger.user.constant.RoleConstants;
import com.zealsinger.user.domain.entity.Role;
import com.zealsinger.user.domain.entity.User;
import com.zealsinger.user.domain.entity.UserRole;
import com.zealsinger.user.domain.enums.ResponseCodeEnum;
import com.zealsinger.user.domain.enums.SexEnum;
import com.zealsinger.user.domain.vo.UpdateUserInfoReqVO;
import com.zealsinger.user.dto.FindUserByPhoneReqDTO;
import com.zealsinger.user.dto.FindUserByPhoneRspDTO;
import com.zealsinger.user.dto.RegisterUserReqDTO;
import com.zealsinger.user.dto.UpdatePasswordReqDTO;
import com.zealsinger.user.mapper.RoleMapper;
import com.zealsinger.user.mapper.UserMapper;
import com.zealsinger.user.mapper.UserRoleMapper;
import com.zealsinger.user.rpc.OssRpcService;
import com.zealsinger.user.server.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final int MAX_INTRODUCTION_LEN=100;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OssRpcService ossRpcService;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;


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
            String rpcResponse = ossRpcService.uploadFile(avatar);
            if(StringUtils.isBlank(rpcResponse)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", rpcResponse);
            user.setAvatar(rpcResponse);
            needUpdate = true;
        }
        if(background != null) {
            //TODO 调用对象存储服务
            String rpcResponse = ossRpcService.uploadFile(background);
            if(StringUtils.isBlank(rpcResponse)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", rpcResponse);
            user.setBackgroundImg(rpcResponse);
            needUpdate = true;
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

    @Override
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {
            String phone = registerUserReqDTO.getPhone();
            User user = userMapper.selectByPhone(phone);
            if(user!=null){
                return Response.success(user.getId());
            }
            //  新用户 需要注册
            try{
                // 获取全局自增的小哈书 ID
                Long zealId = redisTemplate.opsForValue().increment(RedisConstant.ZEALSINGER_BOOK_ID_GENERATOR_KEY);
                User userDO = User.builder()
                        .phone(phone)
                        // 自动生成 账号ID
                        .zealsingerBookId(String.valueOf(zealId))
                        // 自动生成昵称, 如：zealsingerbook10000
                        .nickname("zealsingerbook" + zealId)
                        // 状态为启用
                        .status(StatusEnum.ENABLE.getValue())
                        // 逻辑删除
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build();

                // 添加入库
                userMapper.insert(userDO);

                // 获取刚刚添加入库的用户 ID
                Long userId = userDO.getId();

                // 给该用户分配一个默认角色
                UserRole userRoleDO = UserRole.builder()
                        .userId(userId)
                        .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build();
                userRoleMapper.insert(userRoleDO);

                // 将该用户的对应角色 存入 Redis 中
                List<String> roles = Lists.newArrayList();
                roles.add(roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getId, RoleConstants.COMMON_USER_ROLE_ID)).getRoleKey());
                String userRolesKey = RedisConstant.buildUserRoleKey(userId);
                redisTemplate.opsForValue().set(userRolesKey, JsonUtil.ObjToJsonString(roles));
                return Response.success(userId);
            }catch (Exception e) {
                // 标记为事件回滚
                log.error("系统注册服务出现故障!!!");
                return Response.fail();
            }
    }

    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        String phone = findUserByPhoneReqDTO.getPhone();
        LambdaUpdateWrapper<User> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = userMapper.selectOne(queryWrapper);
        if(user == null){
            throw new BusinessException(ResponseCodeEnum.USER_NOT_EXIST);
        }
        FindUserByPhoneRspDTO responseUser = FindUserByPhoneRspDTO.builder().id(user.getId()).password(user.getPassword()).build();
        return Response.success(responseUser);
    }

    @Override
    public Response<?> updatePassword(UpdatePasswordReqDTO updatePasswordReqDTO) {
        try{
            Long userId = LoginUserContextHolder.getUserId();
            String password = updatePasswordReqDTO.getPassword();
            LambdaUpdateWrapper<User> userUpdateWrapper = new LambdaUpdateWrapper<>();
            userUpdateWrapper.eq(User::getId, userId).set(User::getPassword,password);
            userMapper.update(userUpdateWrapper);
            return Response.success();
        }catch (Exception e){
            throw  new BusinessException(ResponseCodeEnum.PASSWORD_UPDATE_FAIL);
        }
    }
}
