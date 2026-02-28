package com.ragengine.repository;

import com.ragengine.domain.entity.Document;
import com.ragengine.domain.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findAllByOrderByCreatedAtDesc();

    List<Document> findByIdIn(List<UUID> ids);
}
