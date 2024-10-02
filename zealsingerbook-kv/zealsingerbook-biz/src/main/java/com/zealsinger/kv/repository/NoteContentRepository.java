package com.zealsinger.kv.repository;

import com.zealsinger.kv.domain.NoteContent;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

public interface NoteContentRepository extends CassandraRepository<NoteContent, UUID> {
}
