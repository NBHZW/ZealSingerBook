package com.zealsinger.user.relation.server.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.PageUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.PageResponse;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.DateUtils;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.user.dto.FindUserByIdRspDTO;
import com.zealsinger.user.relation.constant.RedisConstant;
import com.zealsinger.user.relation.constant.RocketMQConstant;
import com.zealsinger.user.relation.domain.dto.FollowReqDTO;
import com.zealsinger.user.relation.domain.dto.MqFollowUserDTO;
import com.zealsinger.user.relation.domain.dto.MqUnFollowUserDTO;
import com.zealsinger.user.relation.domain.dto.UnFollowReqDTO;
import com.zealsinger.user.relation.domain.entity.Fans;
import com.zealsinger.user.relation.domain.entity.Following;
import com.zealsinger.user.relation.domain.enums.LuaResultEnum;
import com.zealsinger.user.relation.domain.enums.ResponseCodeEnum;
import com.zealsinger.user.relation.domain.vo.FindFansListReqVO;
import com.zealsinger.user.relation.domain.vo.FindFansListRspVO;
import com.zealsinger.user.relation.domain.vo.FindFollowingListReqVO;
import com.zealsinger.user.relation.domain.vo.FindFollowingListRspVO;
import com.zealsinger.user.relation.mapper.FansMapper;
import com.zealsinger.user.relation.mapper.FollowingMapper;
import com.zealsinger.user.relation.rpc.UserRpcServer;
import com.zealsinger.user.relation.server.UserRelationServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.DefaultScriptExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

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
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Autowired
    private FansMapper fansMapper;


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
        String hashKey = String.valueOf(userId);
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", mqFollowUserDTO);
        // 异步发送MQ消息  提高性能
        rocketTemplate.asyncSendOrderly(header, message, hashKey,new SendCallback() {
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
        // 返回-4 没有被关注 不能取关没有关注的人
        if(LuaResultEnum.UNFOLLOWED.getCode().equals(executeResult)){
            throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
        }
        // 返回-1 关注列表缓存暂且不存在  将关注列表全量加载到redis的zset中
        if(LuaResultEnum.ZSET_NOT_EXIST.getCode().equals(executeResult)){
            // 获取当前用户的关注信息 全量加载到zset中
            List<Following> followingList = followingMapper.selectList(new LambdaQueryWrapper<Following>().eq(Following::getUserId, userId));
            if(CollUtil.isEmpty(followingList)){
                // 关注列表为空说明还未关注任何人 即返回还没关注
                throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
            }
            // 设置过期时间
            long expiresSecondes = 60 * 60 * 24 + RandomUtil.randomInt(60*60*24);
            // 构建lua脚本参数
            Object[] luaArgs = buildLuaArgs(followingList, expiresSecondes);
            // 执行脚本
            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script2.setResultType(Long.class);
            redisTemplate.execute(script2, Collections.singletonList(followingKey), luaArgs);
            // 再次调用上面的 Lua 脚本：unfollow_check_and_remove.lua , 将取关的用户删除
            executeResult = redisTemplate.execute(script, Collections.singletonList(followingKey), unfollowUserId);
            // 再次校验结果
            if (Objects.equals(executeResult, LuaResultEnum.UNFOLLOWED.getCode())) {
                throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
            }
        }


        // MQ异步消费数据库
        MqUnFollowUserDTO mqUnfollowUser = MqUnFollowUserDTO.builder().unfollowUserId(String.valueOf(unfollowUserId)).userId(String.valueOf(userId)).unfollowTime(LocalDateTime.now()).build();
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(mqUnfollowUser)).build();
        String header = RocketMQConstant.TOPIC_FOLLOW_OR_UNFOLLOW+":"+RocketMQConstant.TAG_UNFOLLOW;
        String hashKey = String.valueOf(userId);
        rocketTemplate.asyncSendOrderly(header,message,hashKey,new SendCallback() {

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
    public Response<?> list(FindFollowingListReqVO findFollowingListReqVO) {
        Long pageNo = findFollowingListReqVO.getPageNo();
        Long userId = findFollowingListReqVO.getUserId();
        // 先从redis中查询
        String redisUserInfoKey = RedisConstant.getFollowingKey(String.valueOf(userId));
        Long total = redisTemplate.opsForZSet().zCard(redisUserInfoKey);
        // 每页展示 10 条数据
        long limit = 10;
        List<FindFollowingListRspVO> resultList = null;
        if(total>0) {
            // 说明有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage) {
                return PageResponse.success(null, pageNo, total);
            }
            // 拿取对应的数据进行返回
            long offset = (pageNo - 1) * limit;
            // Set<Object> range = redisTemplate.opsForZSet().range(redisUserInfoKey, offset, offset + limit - 1);
            // 使用 ZREVRANGEBYSCORE 命令（该命令返回的set集合是key的每一个value数值 而不是value的分数  所以直接返回了followingUserId）按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 注意：这里使用了 Double.POSITIVE_INFINITY 和 Double.NEGATIVE_INFINITY 作为分数范围
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(redisUserInfoKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 如果不为空 拿去数据封装返回即可
                // 提取所有用户 ID 到集合中
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcServer.findUserByIds(userIds);
                if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
                    resultList = findUserByIdRspDTOS.stream().map(findUserByIdRspDTO -> FindFollowingListRspVO.builder().userId(findFollowingListReqVO.getUserId())
                            .nickname(findUserByIdRspDTO.getNickname())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .introduction(findUserByIdRspDTO.getDescription())
                            .build()).toList();
                }
            }
        }else{
                // todo 缓存中没有数据  查库 并且 异步保存到redis中
                if(pageNo<1){
                    pageNo = 1L;
                }
                LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
                followingLambdaQueryWrapper.eq(Following::getUserId, userId);
                followingLambdaQueryWrapper.orderByDesc(Following::getCreateTime);
                followingLambdaQueryWrapper.last("limit"+" "+(pageNo-1)*limit+","+limit);
                List<Following> followingList = followingMapper.selectList(followingLambdaQueryWrapper);
                if(CollUtil.isNotEmpty(followingList)){
                    // 收集ID的List
                    List<Long> followingIdList = followingList.stream().map(Following::getFollowingUserId).distinct().toList();
                    // 调用User模块的RPC 查询用户信息
                    List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(followingIdList);
                    if (CollUtil.isNotEmpty(userByIds)) {
                        resultList= userByIds.stream().map(findUserByIdRspDTO -> FindFollowingListRspVO.builder().userId(findFollowingListReqVO.getUserId())
                                .nickname(findUserByIdRspDTO.getNickname())
                                .avatar(findUserByIdRspDTO.getAvatar())
                                .introduction(findUserByIdRspDTO.getDescription())
                                .build()).toList();
                        // TODO 异步同步到redis
                        taskExecutor.submit(()-> syncFollowingList2Redis(userId));
                    }
                }
            }
        return PageResponse.success(resultList, pageNo, total);
    }

    /**
     * 返回粉丝列表
     * @param findFansListReqVO
     * @return
     */
    @Override
    public Response<?> findFansListReqVO(FindFansListReqVO findFansListReqVO) {
        Long userId = findFansListReqVO.getUserId();
        Long pageNo = findFansListReqVO.getPageNo();
        if(pageNo < 1){
            pageNo =1L ;
        }
        String fansKey = RedisConstant.getFansKey(String.valueOf(userId));
        Long total = redisTemplate.opsForZSet().zCard(fansKey);
        long limit = 10;
        List<FindFansListRspVO> resultList = null;
        if(total > 0){
            // 说明缓存中有数据
            if(pageNo > total) {
                return PageResponse.success(null, pageNo, total);
            }
            long offset = (pageNo - 1) * limit;
            Set<Object> redisObjectList = redisTemplate.opsForZSet().reverseRangeByScore(fansKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if(CollUtil.isNotEmpty(redisObjectList)) {
                List<Long> fansUserIdList = redisObjectList.stream().map(object -> Long.valueOf(object.toString())).toList();
                List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(fansUserIdList);
                if (CollUtil.isNotEmpty(userByIds)) {
                    resultList = userByIds.stream().map(findUserByIdRspDTO -> FindFansListRspVO.builder().userId(findUserByIdRspDTO.getId())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            // TODO 计数模块再补充这两个数据
                            .fansCount(null)
                            .noteCount(null)
                            .build()).toList();
                }
            }
        }else{
            LambdaQueryWrapper<Fans> fansLambdaQueryWrapper = new LambdaQueryWrapper<>();
            // 查询数据库返回 异步同步到redis中
            Long fansTotal = fansMapper.selectCount(fansLambdaQueryWrapper.eq(Fans::getUserId, userId));
            // 粉丝数量是无上限的  防止返回过多和恶意攻击  之前有做限定 不能大于500页
            if(pageNo > fansTotal || pageNo > 500) {
                return PageResponse.success(null, pageNo, total);
            }
            // 返回前500的数据 到这里能确定pageNo小于500
            fansLambdaQueryWrapper.orderByDesc(Fans::getCreateTime);
            fansLambdaQueryWrapper.last("limit"+" "+(pageNo-1)*limit+","+limit);
            List<Fans> fansList = fansMapper.selectList(fansLambdaQueryWrapper);
            if(CollUtil.isNotEmpty(fansList)){
                List<Long> fansIdList = fansList.stream().map(Fans::getFansUserId).distinct().toList();
                List<FindUserByIdRspDTO> userByIds = userRpcServer.findUserByIds(fansIdList);
                if (CollUtil.isNotEmpty(userByIds)) {
                    resultList = userByIds.stream().map(findUserByIdRspDTO -> FindFansListRspVO.builder().userId(findUserByIdRspDTO.getId())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            // TODO 计数模块再补充这两个数据
                            .fansCount(null)
                            .noteCount(null)
                            .build()).toList();
                    // 异步将粉丝数据同步到redis中
                    taskExecutor.submit(()-> syncFansList2Redis(userId));
                }
            }
        }
        return PageResponse.success(resultList, pageNo, total);
    }

    private void syncFansList2Redis(Long userId) {
        LambdaQueryWrapper<Fans> fansLambdaQueryWrapper = new LambdaQueryWrapper<>();
        fansLambdaQueryWrapper.eq(Fans::getUserId, userId);
        fansLambdaQueryWrapper.orderByDesc(Fans::getCreateTime);
        fansLambdaQueryWrapper.last("limit"+" "+0+" , "+ 5000);
        List<Fans> fansList = fansMapper.selectList(fansLambdaQueryWrapper);
        if(CollUtil.isNotEmpty(fansList)){
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            Object[] luaArgs = buildFansLuaArgs(fansList, expireSeconds);
            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(RedisConstant.getFansKey(String.valueOf(userId))), luaArgs);
        }
    }

    private Object[] buildFansLuaArgs(List<Fans> list,long expireSeconds){
        int len = list.size()*2+1;
        Object[] args = new Object[len];
        int i =0 ;
        for (Fans fans : list) {
            args[i] = DateUtils.localDateTime2Timestamp(fans.getCreateTime());
            args[i+1] = fans.getFansUserId();
            i+=2;
        }
        args[len-1] = expireSeconds;
        return args;
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

    /**
     * 全量同步关注列表至 Redis 中  直接套用之前的Lua脚本即可
     */
    private void syncFollowingList2Redis(Long userId) {
        LambdaQueryWrapper<Following> followingLambdaQueryWrapper = new LambdaQueryWrapper<>();
        followingLambdaQueryWrapper.eq(Following::getUserId, userId);
        List<Following> followingList = followingMapper.selectList(followingLambdaQueryWrapper);
        if(CollUtil.isNotEmpty(followingList)){
            String redisKey = RedisConstant.getFollowingKey(String.valueOf(userId));
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            Object[] luaArgs = buildLuaArgs(followingList, expireSeconds);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(redisKey), luaArgs);
        }
    }
}
