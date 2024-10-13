package com.zealsinger.user.relation.server.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.DateUtils;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.user.relation.constant.RedisConstant;
import com.zealsinger.user.relation.constant.RocketMQConstant;
import com.zealsinger.user.relation.domain.dto.FollowReqDTO;
import com.zealsinger.user.relation.domain.dto.MqFollowUserDTO;
import com.zealsinger.user.relation.domain.dto.MqUnFollowUserDTO;
import com.zealsinger.user.relation.domain.dto.UnFollowReqDTO;
import com.zealsinger.user.relation.domain.entity.Following;
import com.zealsinger.user.relation.domain.enums.LuaResultEnum;
import com.zealsinger.user.relation.domain.enums.ResponseCodeEnum;
import com.zealsinger.user.relation.mapper.FollowingMapper;
import com.zealsinger.user.relation.rpc.UserRpcServer;
import com.zealsinger.user.relation.server.UserRelationServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.DefaultScriptExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserRelationServerImpl implements UserRelationServer {
    @Resource
    private UserRpcServer userRpcServer;


    @Resource
    private FollowingMapper followingMapper;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketTemplate;

    @Override
    public Response<?> follow(FollowReqDTO followReqDTO) {
        Long followUserId = followReqDTO.getFollowUserId();
        Long userId = LoginUserContextHolder.getUserId();
        // 校验：无法关注自己
        if (Objects.equals(userId, followUserId)) {
            throw new BusinessException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }
        // 检测用户是否存在
        Boolean exist = userRpcServer.checkUserExist(followUserId);
        if(!exist){
            throw new BusinessException(ResponseCodeEnum.USER_NOT_EXITS);
        }

        // 构建当前用户的关注列表key
        String followingKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        // 创建脚本执行对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // 设置脚本资源路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        // 设置脚本返回值类型
        script.setResultType(Long.class);
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 当前时间转时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(now);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(followingKey), followUserId, timestamp);
        // 对结果进行判断  进行对应的处理
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        if (Objects.isNull(luaResultEnum)) {
            throw new RuntimeException("Lua 返回结果错误");
        }
        checkLuaScriptResult(result);
        // 判断是否为关注列表不存在的情况
        if(Objects.equals(result,LuaResultEnum.ZSET_NOT_EXIST.getCode())){
            // TODO 关注列表不存在 需要查库找寻载入缓存信息
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            LambdaQueryWrapper<Following> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Following::getUserId,userId);
            List<Following> followingList = followingMapper.selectList(queryWrapper);
            if(CollUtil.isEmpty(followingList)){
                // 如果为空 那么说明是第一次关注用户  采用ZADD添加信息设置信息即可
                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                script2.setResultType(Long.class);
                // TODO 计数服务没有搭建 到时候搭建完毕后 在这里根据计数结果进行不同过期时间的设置
                redisTemplate.execute(script2, Collections.singletonList(followingKey), followUserId, timestamp,expireSeconds);
            }else{
                // 则说明原本有数据  全量同步到redis中即可
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                Object[] luaArgs = buildLuaArgs(followingList, expireSeconds);
                redisTemplate.execute(script3, Collections.singletonList(followingKey), luaArgs);
                // 全量同步之后 将这次的数据也要加进去
                Long execute = redisTemplate.execute(script, Collections.singletonList(followingKey), followUserId, timestamp);
                checkLuaScriptResult(execute);
            }
        }
        // 异步MQ消费通知
        MqFollowUserDTO mqFollowUserDTO = MqFollowUserDTO.builder().userId(userId).followUserId(followUserId).createTime(now).build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(mqFollowUserDTO))
                .build();
        // 采用  Topic:str 的形式作为topic 后面的str就是tag
        String header = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW+":"+RocketMQConstant.TAG_FOLLOW;
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", mqFollowUserDTO);
        // 异步发送MQ消息  提高性能
        rocketTemplate.asyncSend(header, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    @Override
    public Response<?> unfollow(UnFollowReqDTO unFollowReqDTO) {
        Long unfollowUserId = unFollowReqDTO.getUnfollowUserId();
        Long userId = LoginUserContextHolder.getUserId();
        // 不能自己取关自己
        if (Objects.equals(userId, unfollowUserId)) {
            throw new BusinessException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }
        // 用户不存在 非法用户
        Boolean exist = userRpcServer.checkUserExist(unfollowUserId);
        if(!exist){
            throw new BusinessException(ResponseCodeEnum.USER_NOT_EXITS);
        }
        // 执行lua脚本

        // 构建关注列表的key
        String followingKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        // 构造脚本执行对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // 设置返回值
        script.setResultType(Long.class);
        // 设置资源路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_remove.lua")));
        // 执行
        Long executeResult = redisTemplate.execute(script, Collections.singletonList(followingKey), unfollowUserId);
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(executeResult);
        if (Objects.isNull(luaResultEnum)) {
            throw new RuntimeException("Lua 执行失败");
        }
        // 返回-1 关注列表缓存暂且不存在  不用管 直接MQ操作库; 如果返回0则说明Lua操作成功缓存中已经删除所以也是直接可以去删库了  所以这里只有-4的时候需要额外异常处理
        if(LuaResultEnum.UNFOLLOWED.getCode().equals(luaResultEnum.getCode())){
            throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
        }
        // MQ异步消费数据库
        MqUnFollowUserDTO mqUnfollowUser = MqUnFollowUserDTO.builder().unfollowUserId(String.valueOf(unfollowUserId)).userId(String.valueOf(userId)).unfollowTime(LocalDateTime.now()).build();
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(mqUnfollowUser)).build();
        String header = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW+":"+RocketMQConstant.TAG_UNFOLLOW;
        rocketTemplate.asyncSend(header,message,new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) {
            throw new RuntimeException("Lua 返回结果错误");
        }
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BusinessException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BusinessException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }

    /**
     * 构建 Lua 脚本参数
     * @param followings
     * @param expireSeconds
     * @return
     */
    private static Object[] buildLuaArgs(List<Following> followings, long expireSeconds) {
        // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间
        int argsLength = followings.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (Following following : followings) {
            // 关注时间作为 score
            luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime());
            // 关注的用户 ID 作为 ZSet value
            luaArgs[i + 1] = following.getFollowingUserId();
            i += 2;
        }
        // 最后一个参数是 ZSet 的过期时间
        luaArgs[argsLength - 1] = expireSeconds;
        return luaArgs;
    }
}
