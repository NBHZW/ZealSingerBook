package com.zealsinger.kv.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

/**
 * 笔记内容kv存储对象
 */
@Table("note_content")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteContent {
    @PrimaryKey("id")
    private UUID id;
    private String content;
}
