package com.zealsinger.note.constant;

/**
 * MQ相关常量 例如主题
 */
public interface RocketMQConstant {
    /**
     * Topic 主题：删除笔记本地缓存
     */
    String TOPIC_DELETE_NOTE_LOCAL_CACHE = "DeleteNoteLocalCacheTopic";

    /**
     * Topic: 点赞、取消点赞共用一个大Topic
     */
    String TOPIC_LIKE_OR_UNLIKE = "LikeUnlikeTopic";

    /**
     * Topic: 计数 - 笔记点赞数
     */
    String TOPIC_COUNT_NOTE_LIKE = "CountNoteLikeTopic";

    /**
     * 点赞标签Tag
     */
    String TAG_LIKE = "Like";

    /**
     * Tag 标签：取消点赞
     */
    String TAG_UNLIKE = "Unlike";

    /**
     * Topic:收藏和取消收藏统一一个大Topic
     */
    String TOPIC_COLLECTION_UNCOLLECTION = "CollectionUnCollectTopic";

    /**
     * Tag标签：收藏
     */
    String TAG_COLLECTION = "Collection";

    /**
     * Tag标签：取消收藏
     */
    String TAG_UNCOLLECTION = "UnCollect";

    /**
     * Topic: 计数 - 笔记收藏数
     */
    String TOPIC_COUNT_NOTE_COLLECT = "CountNoteCollectTopic";
}
