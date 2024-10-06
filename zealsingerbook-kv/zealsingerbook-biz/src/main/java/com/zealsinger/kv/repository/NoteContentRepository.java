package com.zealsinger.kv.repository;

import com.zealsinger.kv.domain.NoteContent;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NoteContentRepository extends CassandraRepository<NoteContent, UUID> {
    // 自定义更新方法
    @Query("UPDATE note_content SET content = :content WHERE id = :contentUuid")
    void updateContentByUuid(@Param("content") String content, @Param("contentUuid") UUID contentUuid);
}
