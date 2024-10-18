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

}
