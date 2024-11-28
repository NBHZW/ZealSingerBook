package com.zealsinger.data.align.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CollectionUnCollectionMqDTO {
    private Long userId;
    private Long noteId;
    /**
     * 1收藏 0取消收藏
     */
    private Integer status;
    private Long creatorId;
    private LocalDateTime optionTime;
}
