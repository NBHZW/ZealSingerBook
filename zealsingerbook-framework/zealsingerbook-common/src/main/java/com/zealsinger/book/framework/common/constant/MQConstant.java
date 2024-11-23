package com.zealsinger.book.framework.common.constant;

public interface MQConstant {
    /**
     * 关注接口异步操作Topic
     */
    String FOLLOW_AND_UNFOLLOW = "followAndUnfollow";

    /**
     * 删除本地笔记缓存Topic
     */
    String DELETE_LOCAL_NOTE_CACHE = "deleteLocalNoteCache";
    /**
     * Topic: 关注数计数服务Topic
     */
    String TOPIC_COUNT_FOLLOWING = "CountFollowingTopic";

    String TOPIC_COUNT_FOLLOWING_2_DB = "CountFollowing2DBTopic";

    /**
     * Topic: 粉丝数计数服务Topic
     */
    String TOPIC_COUNT_FANS = "CountFansTopic";

    /**
     * Topic: 粉丝数计数入库消息Topic
     */
    String TOPIC_COUNT_FANS_2_DB = "CountFans2DBTopic";

    /**
     * TOPIC: 计数-笔记点赞数目Topic
     */
    String TOPIC_COUNT_NOTE_LIKE = "CountNoteLikeTopic";

    /**
     * Topic: 计数 - 笔记点赞数落库
     */
    String TOPIC_COUNT_NOTE_LIKE_2_DB = "CountNoteLike2DBTTopic";

    /**
     * Topic: 计数 - 笔记收藏数
     */
    String TOPIC_COUNT_NOTE_COLLECT = "CountNoteCollectTopic";
    /**
     * Topic: 计数 - 笔记收藏数落库
     */
    String TOPIC_COUNT_NOTE_COLLECT_2_DB = "CountNoteCollect2DBTTopic";


}
