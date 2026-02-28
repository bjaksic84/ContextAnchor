package com.ragengine.repository;

import com.ragengine.domain.entity.Document;
import com.ragengine.domain.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findAllByOrderByCreatedAtDesc();

    List<Document> findByIdIn(List<UUID> ids);

    // Tenant-scoped queries
    List<Document> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Document> findByIdInAndTenantId(List<UUID> ids, UUID tenantId);

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    int countByTenantId(UUID tenantId);
}
