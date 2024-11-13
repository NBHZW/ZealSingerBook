package com.zealsinger.note.server.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.DateUtils;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.kv.dto.FindNoteContentRspDTO;
import com.zealsinger.note.constant.RocketMQConstant;
import com.zealsinger.note.domain.dto.*;
import com.zealsinger.note.domain.entity.Note;
import com.zealsinger.note.domain.entity.NoteCollection;
import com.zealsinger.note.domain.entity.NoteLike;
import com.zealsinger.note.domain.enums.*;
import com.zealsinger.note.domain.vo.*;
import com.zealsinger.note.mapper.NoteCollectionMapper;
import com.zealsinger.note.mapper.NoteLikeMapper;
import com.zealsinger.note.mapper.NoteMapper;
import com.zealsinger.note.mapper.TopicMapper;
import com.zealsinger.note.rpc.IdGeneratorRpcService;
import com.zealsinger.note.rpc.KvRpcService;
import com.zealsinger.note.rpc.UserRpcService;
import com.zealsinger.note.server.NoteServer;
import com.zealsinger.user.dto.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.DefaultScriptExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NoteServerImpl extends ServiceImpl<NoteMapper, Note> implements NoteServer {
    @Resource
    private IdGeneratorRpcService idGeneratorRpcService;

    @Resource
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private KvRpcService kvRpcService;

    @Resource
    private UserRpcService userRpcService;

    @Resource
    private TopicMapper topicMapper;

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private NoteCollectionMapper noteCollectionMapper;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private NoteLikeMapper noteLikeMapper;

    private static final Cache<String,FindNoteByIdRspVO> LOCAL_NOTEVO_CACHE = Caffeine.newBuilder()
            .initialCapacity(1000)
            .expireAfterWrite(30,TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    @Override
    public Response<?> publicNote(PublishNoteReqVO publishNoteReqVO) {
        Integer code = publishNoteReqVO.getType();
        if(NoteTypeEnum.isValid(code)){
            NoteTypeEnum typeEnum = NoteTypeEnum.getType(code);
            if(Objects.isNull(typeEnum)){
                throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
            }
            String imgUris = null;
            Boolean isContentEmpty = true;
            String videoUri = null;
            switch (typeEnum){
                case TEXT:
                    //图文
                    List<String> imgUriList = publishNoteReqVO.getImgUris();
                    // 校验图片是否为空
                    Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                    // 校验图片数量
                    Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                    // 将图片链接拼接，以逗号分隔
                    imgUris = StringUtils.join(imgUriList, ",");
                    break;
                case VIDEO:
                    //视频
                    videoUri = publishNoteReqVO.getVideoUri();
                    // 校验视频链接是否为空
                    Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                    break;
                default:
                    throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
            }
            // 生成笔记ID  即数据库中的笔记ID
            String contentId = idGeneratorRpcService.getNoteId();
            // 笔记内容ID  即Cassandra中的对应的UUID
            String contentUuid = null;
            // 笔记内容文本
            String contentText = publishNoteReqVO.getContent();

            // 内容不为空 需要KV服务
            if(StringUtils.isNotBlank(contentText)){
                isContentEmpty = false;
                contentUuid = UUID.randomUUID().toString();
                // 调用kv服务进行存储
                Boolean isSaveSuccess = kvRpcService.addNoteContent(contentUuid, contentText);
                if(!isSaveSuccess){
                    throw new BusinessException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
                }
            }

            // 话题ID
            Long topicId = publishNoteReqVO.getTopicId();
            String topicName = null;
            // 查询话题名
            if(topicId!=null){
                 topicName= topicMapper.selectById(topicId).getName();
            }
            // 调用笔记服务 保存note笔记对象
            Note note = Note.builder().id(Long.valueOf(contentId))
                    .title(publishNoteReqVO.getTitle())
                    .isContentEmpty(isContentEmpty)
                    .creatorId(LoginUserContextHolder.getUserId())
                    .topicId(topicId)
                    .topicName(topicName)
                    .isTop(Boolean.FALSE)
                    .type(code)
                    .imgUris(imgUris)
                    .videoUri(videoUri)
                    .visible(NoteVisibleEnum.PUBLIC.getValue())
                    .contentUuid(contentUuid)
                    .status(NoteStatusEnum.NORMAL.getCode())
                    .build();

            try{
                noteMapper.insert(note);
            }catch (Exception e){
                log.error("===》保存笔记失败", e);
                if(StringUtils.isNotBlank(contentUuid)){
                    kvRpcService.deleteNoteContent(contentUuid);
                }
                throw new BusinessException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }

            return Response.success();
        }else{
            throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
        }
    }

    @Override
    public Response<FindNoteByIdRspVO> findById(FindNoteByIdReqDTO findNoteByIdReqDTO) throws ExecutionException, InterruptedException {
        String noteId = findNoteByIdReqDTO.getNoteId();
        FindNoteByIdRspVO resultNoteVO = null;
        resultNoteVO = LOCAL_NOTEVO_CACHE.getIfPresent(noteId);
        if(resultNoteVO !=null){
            log.info("===>命中本地缓存{}",resultNoteVO);
            return Response.success(resultNoteVO);
        }
        // 查询redis缓存
        String noteStr = redisTemplate.opsForValue().get(RedisConstant.getNoteCacheId(noteId));
        if(StringUtils.isNotBlank(noteStr)){
            resultNoteVO = JsonUtil.JsonStringToObj(noteStr, FindNoteByIdRspVO.class);
            // 异步存入本地缓存
            FindNoteByIdRspVO finalResultNoteVO1 = resultNoteVO;
            threadPoolTaskExecutor.submit(()-> LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO1));
            return Response.success(resultNoteVO);
        }

        // 两层缓存中均无数据 查库！！
        LambdaUpdateWrapper<Note> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(Note::getId, noteId);
        queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
        Note note = noteMapper.selectOne(queryWrapper);
        if(note==null){
            // 缓存空值 防止穿透
            long expireSeconds = 60+RandomUtil.randomInt(60);
            redisTemplate.opsForValue().set(RedisConstant.getNoteCacheId(noteId), "null", expireSeconds, TimeUnit.SECONDS);
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkVisible(note);
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if(!note.getIsContentEmpty()) {
            contentResultFuture = contentResultFuture.supplyAsync(()->{
                FindNoteContentRspDTO noteContentById = kvRpcService.findNoteContentById(note.getContentUuid());
                return noteContentById.getContent();
            },threadPoolTaskExecutor);
        }

        // 异步调用user模块
        CompletableFuture<FindUserByIdRspDTO> userResultFuture =  CompletableFuture.supplyAsync(()->userRpcService.getUserInfoById(note.getCreatorId()),threadPoolTaskExecutor);
        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        // 整体异步任务连接封装返回对象
        CompletableFuture<FindNoteByIdRspVO> resultFuture = CompletableFuture
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                            FindUserByIdRspDTO join = userResultFuture.join();
                            String creatorName = join.getNickname();
                            String content = finalContentResultFuture.join();
                            List<String> imgUris = null;
                            String noteImgUris = note.getImgUris();
                            if(StringUtils.isNotBlank(noteImgUris)){
                                imgUris  = Arrays.asList(noteImgUris.split(","));
                            }
                            return FindNoteByIdRspVO.builder().id(note.getId())
                                    .title(note.getTitle())
                                    .creatorId(note.getCreatorId())
                                    .creatorName(creatorName)
                                    .topicId(note.getTopicId())
                                    .topicName(note.getTopicName())
                                    .isTop(note.getIsTop())
                                    .type(note.getType())
                                    .imgUris(imgUris)
                                    .videoUri(note.getVideoUri())
                                    .visible(note.getVisible())
                                    .content(content)
                                    .contentUuid(note.getContentUuid())
                                    .updateTime(note.getUpdateTime())
                                    .build();
                        });

        // 获取拼装后的 FindNoteDetailRspVO
        resultNoteVO = resultFuture.get();
        // 异步写入redis缓存 和 本地缓存
        FindNoteByIdRspVO finalResultNoteVO = resultNoteVO;
        threadPoolTaskExecutor.submit(()->{
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO);
            redisTemplate.opsForValue().set(RedisConstant.getNoteCacheId(noteId), JsonUtil.ObjToJsonString(finalResultNoteVO), expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(resultNoteVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReqVO reqVO) {
        Integer type = reqVO.getType();
        NoteTypeEnum typeEnum = NoteTypeEnum.getType(type);
        if(typeEnum==null){
            throw new BusinessException(ResponseCodeEnum.TYPE_ERROR);
        }
        List<String> imgUriList = reqVO.getImgUris();
        String videoUri = reqVO.getVideoUri();
        String imgUris = null;
        // 根据类型进行特判
        if(NoteTypeEnum.TEXT.getValue().equals(type)){
            Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList),"图片不能为空");
            Preconditions.checkArgument(imgUriList.size()<=8,"图片数量不能多于8张");
        }else{
            Preconditions.checkArgument(StringUtils.isNotBlank(videoUri),"视频不能为空");
        }
        imgUris = String.join(",",imgUriList);

        String content = reqVO.getContent();
        String id = reqVO.getId();
        LambdaUpdateWrapper<Note> findQueryWrapper = new LambdaUpdateWrapper<>();
        findQueryWrapper.eq(Note::getId, id);
        findQueryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
        Note note = noteMapper.selectOne(findQueryWrapper);
        if(note==null){
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        if(!Objects.equals(LoginUserContextHolder.getUserId(), note.getCreatorId())){
            throw new BusinessException(ResponseCodeEnum.NOT_HAVE_PERMISSIONS);
        }
        String contentUuid = note.getContentUuid();
        LambdaUpdateWrapper<Note> queryWrapper = new LambdaUpdateWrapper<>();
        if(StringUtils.isNotBlank(content)){
            Boolean isUpdateSuccess = kvRpcService.updateNoteContent(contentUuid, content);
            if(!isUpdateSuccess){
                throw new BusinessException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
            }
        }else{
            // 如果内容为空 则删除kv存储中的内容
            kvRpcService.deleteNoteContent(contentUuid);
            contentUuid = null;
        }

        String topicName = null;
        Long topicId = reqVO.getTopicId();
        if(topicId!=null){
            topicName = topicMapper.selectById(topicId).getName();
        }

        queryWrapper.eq(Note::getId, id)
                .set(Note::getImgUris,imgUris)
                .set(Note::getTitle,reqVO.getTitle())
                .set(Note::getTopicId, topicId)
                .set(Note::getTopicName,topicName)
                .set(Note::getType, type)
                .set(Note::getContentUuid,contentUuid)
                .set(Note::getIsContentEmpty,StringUtils.isBlank(content))
                .set(Note::getVideoUri, videoUri);

        noteMapper.update(queryWrapper);

        // 更新完毕后删除本地缓存  利用MQ进行通知删除
        rocketMQTemplate.syncSend(RocketMQConstant.TOPIC_DELETE_NOTE_LOCAL_CACHE,id);

        // 更新后删除redis远程缓存
        redisTemplate.delete(RedisConstant.getNoteCacheId(id));

        return Response.success();
    }

    @Override
    public void deleteNoteLocalCache(String noteId) {
        LOCAL_NOTEVO_CACHE.invalidate(noteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> deleteNote(DeleteNoteByIdReqDTO deleteNoteByIdReqDTO) {
        String id = deleteNoteByIdReqDTO.getId();
        // 我们的删除是逻辑删除 所以实际上的行为为更新操作
        LambdaUpdateWrapper<Note> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(Note::getId, id);
        queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
        Note note = noteMapper.selectOne(queryWrapper);
        if(note==null){
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        if(!Objects.equals(LoginUserContextHolder.getUserId(), note.getCreatorId())){
            throw new BusinessException(ResponseCodeEnum.NOT_HAVE_PERMISSIONS);
        }
        note.setStatus(NoteStatusEnum.DELETED.getCode());
        noteMapper.updateById(note);
        // 删除缓存 和 kv中的数据
        rocketMQTemplate.syncSend(RocketMQConstant.TOPIC_DELETE_NOTE_LOCAL_CACHE,id);
        log.info("====> MQ：删除笔记本地缓存发送成功...");
        redisTemplate.delete(RedisConstant.getNoteCacheId(id));
        threadPoolTaskExecutor.submit(()->{
            try{
                if(!note.getIsContentEmpty()) {
                    kvRpcService.deleteNoteContent(note.getContentUuid());
                }
            }catch (Exception e){
                log.warn("删除kv笔记内容失败,笔记UUID:{}",note.getContentUuid());
            }
        });
        return Response.success();
    }

    @Override
    public Response<?> visibleOnlyMe(UpdateVisibleOnlyMeReqVO updateVisibleOnlyMeReqVO) {
        try{
            String id = updateVisibleOnlyMeReqVO.getId();
            LambdaUpdateWrapper<Note> queryWrapper = new LambdaUpdateWrapper<>();
            queryWrapper.eq(Note::getId, id);
            queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
            Note note = noteMapper.selectOne(queryWrapper);
            if(note==null){
                throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            if(!Objects.equals(LoginUserContextHolder.getUserId(), note.getCreatorId())){
                throw new BusinessException(ResponseCodeEnum.NOT_HAVE_PERMISSIONS);
            }
            LambdaUpdateWrapper<Note> updateWrapper = new LambdaUpdateWrapper<>();
            Boolean visibleOnlyMe = updateVisibleOnlyMeReqVO.getVisibleOnlyMe();
            if(visibleOnlyMe){
                updateWrapper.eq(Note::getId,id).eq(Note::getStatus,NoteStatusEnum.NORMAL.getCode()).set(Note::getVisible,NoteVisibleEnum.PRIVATE.getValue());
            }else{
                updateWrapper.eq(Note::getId,id).eq(Note::getStatus,NoteStatusEnum.NORMAL.getCode()).set(Note::getVisible,NoteVisibleEnum.PUBLIC.getValue());
            }

            noteMapper.update(updateWrapper);
            // 删除缓redis缓存
            redisTemplate.delete(RedisConstant.getNoteCacheId(id));
            // 消息通知删除本地缓存
            rocketMQTemplate.syncSend(RocketMQConstant.TOPIC_DELETE_NOTE_LOCAL_CACHE,id);
            log.info("====> MQ：删除笔记本地缓存发送成功...");
            return Response.success();
        }catch (Exception e){
            throw new BusinessException(ResponseCodeEnum.SET_ONLY_ME_FAIL);
        }

    }

    @Override
    public Response<?> updateTopStatus(UpdateTopStatusDTO updateTopStatusDTO) {
        try{
            String id = updateTopStatusDTO.getId();
            LambdaUpdateWrapper<Note> queryWrapper = new LambdaUpdateWrapper<>();
            queryWrapper.eq(Note::getId, id);
            queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
            Note note = noteMapper.selectOne(queryWrapper);
            if(note==null){
                throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            if(!Objects.equals(LoginUserContextHolder.getUserId(), note.getCreatorId())){
                throw new BusinessException(ResponseCodeEnum.NOT_HAVE_PERMISSIONS);
            }
            Boolean isTop = updateTopStatusDTO.getIsTop();
            LambdaUpdateWrapper<Note> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Note::getId,id).eq(Note::getStatus,NoteStatusEnum.NORMAL.getCode()).set(Note::getIsTop,isTop);
            noteMapper.update(updateWrapper);
            // 删除 Redis 缓存
            String noteDetailRedisKey = RedisConstant.getNoteCacheId(id);
            redisTemplate.delete(noteDetailRedisKey);

            // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
            rocketMQTemplate.syncSend(RocketMQConstant.TOPIC_DELETE_NOTE_LOCAL_CACHE,id);
            log.info("====> MQ：删除笔记本地缓存发送成功...");
        }catch(Exception e){
            throw new BusinessException(ResponseCodeEnum.SET_NOTE_TOP_FAIL);
        }


        return Response.success();
    }

    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        // 校验note合理性 + 是否已经点赞 + 更新点赞列表 + 发送MQ落库
        Long noteId = likeNoteReqVO.getNoteId();
        // 因为会存在本地缓存笔记详情，所以可以先查本地缓存
        checkNoteIdIsExits(noteId);
        // 判断是否已经点赞
        Long userId = LoginUserContextHolder.getUserId();
        String bloomKey = RedisConstant.getBloomUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_like_check.lua")));
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(bloomKey), noteId);
        NoteBloomLikeLuaResultEnmu resultEnmu = NoteBloomLikeLuaResultEnmu.valueOf(result);
        long expiredSecond = 60*60*24 + RandomUtil.randomInt(60*60*24);
        String noteLikeZSetKey = RedisConstant.buildUserNoteLikeZSetKey(userId);
        switch (resultEnmu) {
            // 布隆中已经存在 进行二次判断
            case NOTE_LIKED -> {
                // 多重检测之ZSET检测  检测zset中是否存在noteId的记录
                Double score = redisTemplate.opsForZSet().score(noteLikeZSetKey, noteId);
                if(Objects.nonNull(score)){
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // zset中不存在数据 查库
                LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteLike::getUserId, userId);
                queryWrapper.eq(NoteLike::getNoteId, noteId);
                queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
                NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
                if(noteLike!=null){
                    // 到这里说明zset中没有数据,zset过期 所有需要被初始化  异步初始化zset
                    threadPoolTaskExecutor.execute(()->{
                        Boolean hasLikeZset = redisTemplate.hasKey(noteLikeZSetKey);
                        if(Boolean.FALSE.equals(hasLikeZset)){
                            LambdaQueryWrapper<NoteLike> lastQueryWrapper = new LambdaQueryWrapper<>();
                            lastQueryWrapper.eq(NoteLike::getUserId, userId);
                            lastQueryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
                            lastQueryWrapper.orderByDesc(NoteLike::getUpdateTime);
                            lastQueryWrapper.last("limit 100");
                            List<NoteLike> lastNoteLikeList = noteLikeMapper.selectList(lastQueryWrapper);
                            if(CollUtil.isNotEmpty(lastNoteLikeList)){
                                // 非空 初始化ZSET 初始化之前删除一下该用户的点赞zset记录 否则会导致zest中数据超过100条的限制
                                redisTemplate.delete(noteLikeZSetKey);
                                Object[] luaArgs = buildNoteLikeZsetArg(lastNoteLikeList);
                                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                                script3.setResultType(Long.class);
                                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire")));
                                redisTemplate.execute(script3,Collections.singletonList(noteLikeZSetKey),luaArgs);
                            }
                        }
                    });
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 走到这里说明布隆过滤器确实误判了 初始化布隆过滤器然后将当前记录加入到布隆过滤器中
                batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey);
                // 将新记录加入
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setResultType(Long.class);
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_like_note_and_expire.lua")));
                redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId,expiredSecond);
            }
            // 布隆过滤器不存在 通过调库判断是否已经点赞  初始化布隆
            case BLOOM_NOT_EXIST -> {
                // 查库判断是否点赞过
                LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteLike::getUserId, userId);
                queryWrapper.eq(NoteLike::getNoteId, noteId);
                queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
                NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
                if(!Objects.isNull(noteLike)){
                    // 说明已经点赞 进行返回 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(()-> batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey));
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // noteLike为null 说明 说明没被点赞过  老规矩 初始化布隆过滤器后加入当前记录
                // 到这里的就是未点赞 主动初始化布隆过滤器后添加新记录 异步放入到库中
                batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey);
                // 将新记录加入
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setResultType(Long.class);
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_like_note_and_expire.lua")));
                redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId,expiredSecond);
            }
            default -> {}
        }
        /*
        // 到这里的就是未点赞 主动初始化布隆过滤器后添加新记录 异步放入到库中
        batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey);
        // 将新记录加入
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_like_note_and_expire.lua")));
        redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId,expiredSecond);
        */

        // 到这里说明之前没点赞过且一定更新完布隆过滤器了
        // 更新点赞zset数据 执行lua脚本 先判断是否zset存在 如果存在 判断是否有100个数据 如果有 则删除最早那个  如果没有则直接新增
        DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
        script2.setResultType(Long.class);
        script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        Long zsetResult = redisTemplate.execute(script2, Collections.singletonList(noteLikeZSetKey), noteId, expiredSecond);
        if(Objects.equals(zsetResult, NoteBloomLikeLuaResultEnmu.ZSET_NOT_EXIST.getCode())){
            // ZSET不存在 进行初始化 找到当前用户最近的100个点赞用于初始化ZSET
            LambdaQueryWrapper<NoteLike> lastQueryWrapper = new LambdaQueryWrapper<>();
            lastQueryWrapper.eq(NoteLike::getUserId, userId);
            lastQueryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
            lastQueryWrapper.orderByDesc(NoteLike::getUpdateTime);
            lastQueryWrapper.last("limit 100");
            List<NoteLike> lastNoteLikeList = noteLikeMapper.selectList(lastQueryWrapper);
            if(CollUtil.isNotEmpty(lastNoteLikeList)){
                // 非空 初始化ZSET
                Object[] luaArgs = buildNoteLikeZsetArg(lastNoteLikeList);
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setResultType(Long.class);
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
                redisTemplate.execute(script3,Collections.singletonList(noteLikeZSetKey),luaArgs);
                // 再次调用新增ZSET的Lua脚本,将本次数据添加进去
                redisTemplate.execute(script2, Collections.singletonList(noteLikeZSetKey), noteId, expiredSecond);
            }
        }
        // 直接到这里标识zset的更新操作没问题 zset存在并且添加更新成功  准备异步MQ入库
        log.info("===>点赞消息准备异步落库,用户{},笔记{}",userId,noteId);
        LikeUnlikeMqDTO likeUnlikeMqDTO = LikeUnlikeMqDTO.builder().userId(userId)
                .noteId(noteId)
                .likeStatus(LikeStatusEnum.LIKE.getCode())
                .optionTime(LocalDateTime.now())
                .build();
        Message<String> message = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(likeUnlikeMqDTO)).build();
        String messageHead = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE + ":" + RocketMQConstant.TAG_LIKE;
        rocketMQTemplate.asyncSendOrderly(messageHead, message,String.valueOf(userId), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("用户{}点赞笔记{}落库操作成功",userId,noteId);
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("用户{}点赞笔记{}落库操作失败",userId,noteId);
            }
        });

        return Response.success();

    }

    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        // 判断noteId的合理性  先查本地缓存 再查redis  最后查库
        Long noteId = unlikeNoteReqVO.getNoteId();
        checkNoteIdIsExits(noteId);
        // noteId合理 检测是否已经点赞过 使用Lua去布隆过滤器中检测
        Long userId = LoginUserContextHolder.getUserId();
        String bloomKey = RedisConstant.getBloomUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_unlike_check.lua")));
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(bloomKey), noteId);
        NoteUnlikeLuaResultEnum resultEnmu = NoteUnlikeLuaResultEnum.valueOf(result);
        switch(resultEnmu){
            case NOT_EXIST ->{
                //布隆过滤器不存在  查库判断是否点赞  初始化布隆过滤器
                //先初始化
                // 说明没有点赞过  抛出异常 初始化bloom
                long expiredSecond =60*60*24 + RandomUtil.randomInt(60*60*24);
                threadPoolTaskExecutor.submit(()-> batchAddNoteLike2BloomAndExpire(userId,expiredSecond,bloomKey));
                // 查库判断是否点赞
                LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteLike::getUserId,userId).eq(NoteLike::getNoteId,noteId);
                NoteLike noteLike = noteLikeMapper.selectOne(queryWrapper);
                if(Objects.isNull(noteLike) || noteLike.getStatus().equals(Byte.valueOf(String.valueOf(LikeStatusEnum.UNLIKE.getCode())))){
                    throw new BusinessException(ResponseCodeEnum.NOT_LIKED_NOTE);
                }
                // 到这里属于数据库确认了 说明确实点赞了 通过了已点赞校验
            }
            // 到这里说明布隆中没有数据 布隆中没有数据的不会存在误判 所以确实没有点赞  抛出异常
            case  NOTE_NOT_LIKED->{
                throw new BusinessException(ResponseCodeEnum.NOT_LIKED_NOTE);
            }
            // 到这里说明布隆过滤器中存在数据 通过了已点赞校验
            case  NOTE_LIKED-> {}
        }
        // 到这里说明布隆过滤波器中存在数据 通过了已点赞校验
        // 接下来执行的操作：删除zset中的点赞记录，异步落库数据库
        String unlikeNoteRedisKey = RedisConstant.buildUserNoteLikeZSetKey(userId);
        redisTemplate.opsForZSet().remove(unlikeNoteRedisKey,noteId);
        // 异步MQ消息落库
        String topicHeader = RocketMQConstant.TOPIC_LIKE_OR_UNLIKE+":"+RocketMQConstant.TAG_UNLIKE;
        LikeUnlikeMqDTO build = LikeUnlikeMqDTO.builder()
                                .noteId(noteId)
                                .optionTime(LocalDateTime.now())
                                .userId(userId)
                                .likeStatus(LikeStatusEnum.UNLIKE.getCode()).build();
        Message<String> mqMessage = MessageBuilder.withPayload(JsonUtil.ObjToJsonString(build)).build();
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(topicHeader,mqMessage,hashKey,new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 收藏笔记
     * @param collectNoteReqVO
     * @return
     */
    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        // 先检测noteId的合理性 先查本地缓存 再查redis缓存 最后查库
        Long noteId = collectNoteReqVO.getNoteId();
        checkNoteIdIsExits(noteId);
        //检测完noteId的合理性之后 就需要检测是否已经收藏了  采用布隆过滤器判断  过程类似笔记点赞
        Long userId = LoginUserContextHolder.getUserId();
        String bloomNoteCollectsRedisKey = RedisConstant.buildBloomUserNoteCollectsKey(userId);
        String userCollectionZsetKey = RedisConstant.buildUserCollectionZSetKey(userId);
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_collect_check.lua")));
        Long execute = redisTemplate.execute(redisScript, Collections.singletonList(bloomNoteCollectsRedisKey), noteId);
        // 确定是未收藏的笔记 更新收藏笔记缓存 异步落库
        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(execute);
        switch(noteCollectLuaResultEnum){
            // 布隆过滤器不存在 初始化布隆
            case NOT_EXIST ->{
                //查库判断是否已经收藏
                long expiredSecond = 60*60*24 + RandomUtil.randomInt(60*60*24);
                LambdaQueryWrapper<NoteCollection> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(NoteCollection::getUserId,userId)
                        .eq(NoteCollection::getNoteId,noteId)
                        .eq(NoteCollection::getStatus,NoteCollectionStatusEnum.COLLECTION.getCode());
                NoteCollection noteCollection = noteCollectionMapper.selectOne(queryWrapper);
                // 如果查到了 则说明已经收藏 抛出异常  异步初始化collection的bloom布隆
                if(!Objects.isNull(noteCollection)){
                    threadPoolTaskExecutor.submit(()->{
                        batchAddNoteCollection2BloomAndExpire(userId,expiredSecond,bloomNoteCollectsRedisKey);
                    });
                }
                // 到这里说明没查到 那么该笔记确实还没收藏  但还是需要初始化布隆
                batchAddNoteCollection2BloomAndExpire(userId,expiredSecond,bloomNoteCollectsRedisKey);
                // 添加新记录到布隆中
                DefaultRedisScript<Long> redisScript2 = new DefaultRedisScript<>();
                redisScript2.setResultType(Long.class);
                redisScript2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_collection_note_and_expire.lua")));
                redisTemplate.execute(redisScript2, Collections.singletonList(bloomNoteCollectsRedisKey), noteId,expiredSecond);
            }
            // 已经收藏 存在误判 需要多层校验
            case NOTE_COLLECTED ->{
                // 存在误判 先查zset
                Double score = redisTemplate.opsForZSet().score(userCollectionZsetKey, noteId);
                if(Objects.isNull(score)){
                    // zset中也不存在  需要查库
                    LambdaQueryWrapper<NoteCollection> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(NoteCollection::getUserId,userId);
                    queryWrapper.eq(NoteCollection::getNoteId,noteId);
                    queryWrapper.eq(NoteCollection::getStatus,NoteCollectionStatusEnum.COLLECTION.getCode());
                    NoteCollection noteCollection = noteCollectionMapper.selectOne(queryWrapper);
                    if(!Objects.isNull(noteCollection)){
                        // 不为null 则数据库中有  没有误判  但是zset中没有 异步初始化zset 抛出异常已经收藏
                        threadPoolTaskExecutor.execute(()->{
                            // 初始化收藏的zset  先检测zset是否存在 不存在则需要初始化
                            Boolean hasZset = redisTemplate.hasKey(userCollectionZsetKey);
                            if(Boolean.FALSE.equals(hasZset)){
                                // zset不存在需要进行初始化
                                LambdaQueryWrapper<NoteCollection> queryWrappy = new LambdaQueryWrapper<>();
                                queryWrappy.eq(NoteCollection::getUserId,userId);
                                queryWrappy.eq(NoteCollection::getStatus,NoteCollectionStatusEnum.COLLECTION.getCode());
                                queryWrappy.orderByDesc(NoteCollection::getUpdateTime);
                                // 收藏能被别人看到 相比较于喜欢 可悲操作的人多一些 所以缓存也设置大一点
                                queryWrappy.last("limit 300");
                                List<NoteCollection> noteCollectionList = noteCollectionMapper.selectList(queryWrappy);
                                if(CollUtil.isNotEmpty(noteCollectionList)){
                                    DefaultRedisScript<Long> redisScript2 = new DefaultRedisScript<>();
                                    redisScript2.setResultType(Long.class);
                                    redisScript2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collection_zset_and_expire.lua")));
                                    long expireSecond = 60*60*24+RandomUtil.randomInt(60*60);
                                    Object[] userCollectionZsetLuaArg = buildNoteCollectionZSetLuaArgs(noteCollectionList,expireSecond);
                                    redisTemplate.execute(redisScript2, Collections.singletonList(userCollectionZsetKey), userCollectionZsetLuaArg);
                                }
                            }
                        });
                        throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                    }
                    // 为null则说明数据库中没有数据 没有收藏 误判了 不要管
                }else{
                    // zset中存在 说明没有误判了 确实已经收藏了 抛出异常
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }
            }
            //  布隆过滤器存在且没有被收藏且成功添加到布隆中
            case NOTE_COLLECTED_SUCCESS ->{}
        }
        // 到这里说明noteId合理且没有被收藏 将数据更新到zset中并且异步落库
        DefaultRedisScript<Long> redisScript3 = new DefaultRedisScript<>();
        redisScript3.setResultType(Long.class);
        redisScript3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_collection_check_and_update_zset.lua")));
        long timestamp = DateUtils.localDateTime2Timestamp(LocalDateTime.now());
        Long executeResult = redisTemplate.execute(redisScript3, Collections.singletonList(userCollectionZsetKey), noteId, timestamp);
        if(Objects.equals(executeResult,NoteCollectLuaResultEnum.NOT_EXIST.getCode())){
            // 不存在 先初始化 然后再次调用
            LambdaQueryWrapper<NoteCollection> queryWrappy = new LambdaQueryWrapper<>();
            queryWrappy.eq(NoteCollection::getUserId,userId);
            queryWrappy.eq(NoteCollection::getStatus,NoteCollectionStatusEnum.COLLECTION.getCode());
            queryWrappy.orderByDesc(NoteCollection::getUpdateTime);
            queryWrappy.last("limit 300");
            List<NoteCollection> noteCollectionList = noteCollectionMapper.selectList(queryWrappy);
            if(CollUtil.isNotEmpty(noteCollectionList)){
                DefaultRedisScript<Long> redisScript2 = new DefaultRedisScript<>();
                redisScript2.setResultType(Long.class);
                redisScript2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collection_zset_and_expire.lua")));
                long expireSecond = 60*60*24+RandomUtil.randomInt(60*60);
                Object[] userCollectionZsetLuaArg = buildNoteCollectionZSetLuaArgs(noteCollectionList,expireSecond);
                redisTemplate.execute(redisScript2, Collections.singletonList(userCollectionZsetKey), userCollectionZsetLuaArg);
                // 再次调用  加入本次记录
                redisTemplate.execute(redisScript3, Collections.singletonList(userCollectionZsetKey), noteId, timestamp);
            }
        }
        // 到这里 确保新元素已经加入到笔记收藏的ZSET中了 布隆过滤器中也有数据了
        // 准备异步落库操作

    }

    private Object[] buildNoteCollectionZSetLuaArgs(List<NoteCollection> noteCollectionList, long expireSecond) {
        int leng = noteCollectionList.size() * 2 + 1;
        Object[] result =new Object[leng];
        int i=0;
        for(NoteCollection noteCollection : noteCollectionList){
            result[i] = DateUtils.localDateTime2Timestamp(noteCollection.getUpdateTime());
            result[i+1] = noteCollection.getNoteId();
            i += 2;
        }
        result[leng-1] = expireSecond;
        return result;
    }

    /**
     * 初始化笔记收藏布隆过滤器
     * @param userId
     * @param expiredSecond
     * @param bloomNoteCollectsRedisKey
     */
    private void batchAddNoteCollection2BloomAndExpire(Long userId, long expiredSecond, String bloomNoteCollectsRedisKey) {
        try{
            LambdaQueryWrapper<NoteCollection> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(NoteCollection::getUserId,userId).eq(NoteCollection::getStatus,NoteCollectionStatusEnum.COLLECTION.getCode());
            List<NoteCollection> noteCollections = noteCollectionMapper.selectList(queryWrapper);
            if(CollUtil.isNotEmpty(noteCollections)){
                DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                redisScript.setResultType(Long.class);
                redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_collection_and_expire.lua")));
                // 构建笔记收藏布隆过滤器初始化lua脚本所需要的参数
                List<Object> collectionLuaArg = Lists.newArrayList();
                noteCollections.forEach(noteCollection -> collectionLuaArg.add(noteCollection.getNoteId()));
                collectionLuaArg.add(expiredSecond); // 最后一个参数为过期时间
                redisTemplate.execute(redisScript, Collections.singletonList(bloomNoteCollectsRedisKey),collectionLuaArg.toArray())
            }
        }catch (Exception e){
            log.error("## 异步初始化笔记收藏布隆过滤器失败 ",e);
        }
    }

    public Object[] buildNoteLikeZsetArg(List<NoteLike> noteLikeList) {
        long expiredSecond = 60*60*24 + RandomUtil.randomInt(60*60*24);
        Object[] resultArr = new Object[noteLikeList.size()*2+1];
        int i=0;
        for (NoteLike noteLike : noteLikeList) {
            resultArr[i] = DateUtils.localDateTime2Timestamp(noteLike.getUpdateTime());
            resultArr[i+1] = noteLike.getNoteId();
            i+=2;
        }
        resultArr[resultArr.length-1] = expiredSecond;
        return resultArr;
    }

    public void checkVisible(Note note){
        if(NoteVisibleEnum.PRIVATE.getValue().equals(note.getVisible())){
            Long userId = LoginUserContextHolder.getUserId();
            if(userId==null || !userId.equals(note.getCreatorId())){
                throw new BusinessException(ResponseCodeEnum.NOTE_PRIVATE);
            }
        }
    }

    /**
     * 初始化笔记点赞布隆过滤器
     * @param userId
     * @param expireSeconds
     * @param bloomUserNoteLikeListKey
     */
    private void batchAddNoteLike2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteLikeListKey) {
        try{
            // 查库判断是否点赞过
            LambdaQueryWrapper<NoteLike> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(NoteLike::getUserId, userId);
            queryWrapper.eq(NoteLike::getStatus, LikeStatusEnum.LIKE.getCode());
            List<NoteLike> noteLikeList = noteLikeMapper.selectList(queryWrapper);
            if(CollUtil.isNotEmpty(noteLikeList)){
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                // 构建 Lua 参数
                List<Object> luaArgs = new ArrayList<>();
                noteLikeList.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId())); // 将每个点赞的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), luaArgs.toArray());
            }
        }catch (Exception e){
            log.error("## 异步初始化笔记点赞布隆过滤器异常: ", e);
        }
    }


    public void checkNoteIdIsExits(Long noteId) {
        FindNoteByIdRspVO findNoteByIdRspVO = LOCAL_NOTEVO_CACHE.getIfPresent(noteId.toString());
        if(Objects.isNull(findNoteByIdRspVO)){
            String noteCacheId = RedisConstant.getNoteCacheId(String.valueOf(noteId));
            String noteStr = redisTemplate.opsForValue().get(noteCacheId);
            findNoteByIdRspVO = JsonUtil.JsonStringToObj(noteStr, FindNoteByIdRspVO.class);
            if(Objects.isNull(findNoteByIdRspVO)){
                // redis也为空 查库 异步同步数据到缓存
                LambdaQueryWrapper<Note> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(Note::getId, noteId);
                queryWrapper.eq(Note::getStatus, NoteStatusEnum.NORMAL.getCode());
                Note note = noteMapper.selectOne(queryWrapper);
                if(Objects.isNull(note)){
                    throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }
                // 存在的话需要异步同步到缓存
                threadPoolTaskExecutor.submit(()->{
                    FindNoteByIdReqDTO build = FindNoteByIdReqDTO.builder().noteId(String.valueOf(noteId)).build();
                    try {
                        findById(build);
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            // redis中就存在缓存 异步保存到本地缓存即可
            FindNoteByIdRspVO finalFindNoteByIdRspVO = findNoteByIdRspVO;
            threadPoolTaskExecutor.submit(()-> LOCAL_NOTEVO_CACHE.put(String.valueOf(noteId), finalFindNoteByIdRspVO));
        }
    }
}
