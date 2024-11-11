package com.zealsinger.count.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CountNoteLikeUnlikeNoteMqDTO {
    private Long userId;
    private Long noteId;
    private Integer likeStatus;
    private LocalDateTime optionTime;
}
