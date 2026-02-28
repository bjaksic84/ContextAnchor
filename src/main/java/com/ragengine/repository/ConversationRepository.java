package com.ragengine.repository;

import com.ragengine.domain.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    // Tenant-scoped queries
    List<Conversation> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<Conversation> findByIdAndTenantId(UUID id, UUID tenantId);
}
