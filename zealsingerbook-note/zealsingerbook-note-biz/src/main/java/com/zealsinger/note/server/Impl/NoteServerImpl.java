package com.zealsinger.note.server.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import com.zealsinger.kv.dto.FindNoteContentRspDTO;
import com.zealsinger.note.domain.dto.FindNoteByIdReqDTO;
import com.zealsinger.note.domain.entity.Note;
import com.zealsinger.note.domain.enums.NoteStatusEnum;
import com.zealsinger.note.domain.enums.NoteTypeEnum;
import com.zealsinger.note.domain.enums.NoteVisibleEnum;
import com.zealsinger.note.domain.enums.ResponseCodeEnum;
import com.zealsinger.note.domain.vo.FindNoteByIdRspVO;
import com.zealsinger.note.domain.vo.PublishNoteReqVO;
import com.zealsinger.note.domain.vo.UpdateNoteReqVO;
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
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private KvRpcService kvRpcService;

    @Resource
    private UserRpcService userRpcService;

    @Resource
    private TopicMapper topicMapper;

    @Resource
    private NoteMapper noteMapper;

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
    public Response<?> findById(FindNoteByIdReqDTO findNoteByIdReqDTO) throws ExecutionException, InterruptedException {
        String noteId = findNoteByIdReqDTO.getNoteId();
        FindNoteByIdRspVO resultNoteVO = null;
        resultNoteVO = LOCAL_NOTEVO_CACHE.getIfPresent(noteId);
        if(resultNoteVO !=null){
            log.info("===>命中本地缓存{}",resultNoteVO);
            return Response.success(resultNoteVO);
        }
        // 查询redis缓存
        String noteStr = (String) redisTemplate.opsForValue().get(RedisConstant.getNoteCacheId(noteId));
        if(StringUtils.isNotBlank(noteStr)){
            resultNoteVO = JsonUtil.JsonStringToObj(noteStr, FindNoteByIdRspVO.class);
            // 异步存入本地缓存
            FindNoteByIdRspVO finalResultNoteVO1 = resultNoteVO;
            threadPoolTaskExecutor.submit(()-> LOCAL_NOTEVO_CACHE.put(noteId, finalResultNoteVO1));
            return Response.success(resultNoteVO);
        }

        // 两层缓存中均无数据 查库！！

        Note note = noteMapper.selectById(noteId);
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
        Note note = noteMapper.selectById(id);
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

        // 更新完毕后删除缓存
        LOCAL_NOTEVO_CACHE.invalidate(id);
        redisTemplate.delete(RedisConstant.getNoteCacheId(id));

        return Response.success();
    }

    public void checkVisible(Note note){
        if(NoteVisibleEnum.PRIVATE.getValue().equals(note.getVisible())){
            Long userId = LoginUserContextHolder.getUserId();
            if(userId==null || !userId.equals(note.getCreatorId())){
                throw new BusinessException(ResponseCodeEnum.NOTE_PRIVATE);
            }
        }
    }

}
