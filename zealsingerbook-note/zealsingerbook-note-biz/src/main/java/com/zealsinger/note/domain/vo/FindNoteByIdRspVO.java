package com.zealsinger.note.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteByIdRspVO {

    private Long id;

    private String title;

    private Long creatorId;

    private String creatorName;

    private Long topicId;

    private String topicName;

    private Boolean isTop;

    private Integer type;

    private List<String> imgUris;

    private String videoUri;

    private Integer visible;


    private LocalDateTime updateTime;


    private String contentUuid;

    private String content;


}
