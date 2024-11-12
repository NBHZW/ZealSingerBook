# 收藏接口

收藏笔记接口的逻辑和点赞笔记的逻辑基本一致，对比点赞笔记逻辑就很好理解

```
URL POST  /note/collect
入参
{
    "id": 1824370663356366868 // 笔记ID
}

出参
{
	"success": true, // true 表示收藏成功
	"message": null,
	"errorCode": null,
	"data": null
}
```

同样的 我们在收藏逻辑如下

**先验证noteId是否合理 ； 然后检测该noteId是否已经收藏 只能操作未收藏的note ； 通过noteId合理性校验和note未收藏的校验之后就能准备进行落库操作和计数操作** 

## 基础对象准备

```JAVA 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CollectNoteReqVO {
    @NotNull(message = "笔记ID不能为空")
    private Long noteId;
}

@PostMapping("/collect")
public Response<?> collectNote(@RequestBody @Validated CollectNoteReqVO collectNoteReqVO){
    return noteServer.collectNote(collectNoteReqVO);
}
```

## 整体逻辑框架

```Java
public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        // 先检测noteId的合理性 先查本地缓存 再查redis缓存 最后查库
        Long noteId = collectNoteReqVO.getNoteId();
        checkNoteIdIsExits(noteId);
        //检测完noteId的合理性之后 就需要检测是否已经收藏了  采用布隆过滤器判断  过程类似笔记点赞

        // 确定是未收藏的笔记 更新收藏笔记缓存 异步落库

    }
```

## 判断是否还未收藏

还是老规矩 采用布隆过滤器实现  布隆过滤器的redisKey为 bloom:note:collects:#{userId}