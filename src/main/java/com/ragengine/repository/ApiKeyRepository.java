package com.ragengine.repository;

import com.ragengine.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);

    List<ApiKey> findByTenantIdAndActiveTrue(UUID tenantId);

    List<ApiKey> findByUserIdAndActiveTrue(UUID userId);

    Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);
}
