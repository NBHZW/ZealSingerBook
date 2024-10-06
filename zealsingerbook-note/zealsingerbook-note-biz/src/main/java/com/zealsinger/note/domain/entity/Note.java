package com.zealsinger.note.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_note")
public class Note {
    @TableId("id")
    private Long id;

    @TableField("title")
    private String title;

    /**
     * 内容是否为空 0为空 1不为空
     */
    @TableField("is_content_empty")
    private Boolean isContentEmpty;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("topic_id")
    private Long topicId;

    @TableField("topic_name")
    private String topicName;

    /**
     * 是否置顶 0未置顶 1置顶
     */
    @TableField("is_top")
    private Boolean isTop;

    /**
     * 类型 0图文 1视频
     */
    @TableField("type")
    private Integer type;

    @TableField("img_uris")
    private String imgUris;

    @TableField("video_uri")
    private String videoUri;

    /**、
     * 是否公开  0公开可见 1私有
     */
    @TableField("visible")
    private Integer visible;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


    @TableField("content_uuid")
    private String contentUuid;

    /**
     * 状态(0：待审核 1：正常展示 2：被删除(逻辑删除) 3：被下架)
     */
    @TableField("status")
    private Integer status;

}