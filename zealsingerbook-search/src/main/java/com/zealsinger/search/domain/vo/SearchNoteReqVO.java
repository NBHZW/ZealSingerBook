package com.zealsinger.search.domain.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchNoteReqVO {
    @NotBlank(message = "搜索关键字不能为空")
    private String keyword;

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1; // 默认值为第一页

    /**
     * 查询类型  默认null标识不限  0标识图文  1标识视频
     */
    private Integer type = null;

    /**
     * 排序规矩 默认null标识综合（默认排序） 0标识最新  1标识点赞最多  2标识评论最多  3标识收藏最多
     */
    private Integer sort = null;

    /**
     * 发布时间范围：null：不限 / 0：一天内 / 1：一周内 / 2：半年内
     */
    private Integer publishTimeRange;
}
