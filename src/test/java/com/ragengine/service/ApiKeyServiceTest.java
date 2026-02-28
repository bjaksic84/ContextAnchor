package com.ragengine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiKeyService static utility methods — SHA-256 hashing.
 */
class ApiKeyServiceTest {

    @Test
    @DisplayName("SHA-256 should produce consistent hash")
    void sha256ShouldBeConsistent() {
        String input = "ctx_abcdef1234567890abcdef1234567890";
        String hash1 = ApiKeyService.sha256(input);
        String hash2 = ApiKeyService.sha256(input);
        assertEquals(hash1, hash2, "Same input should produce same hash");
    }

    @Test
    @DisplayName("SHA-256 should produce 64 character hex string")
    void sha256ShouldProduce64CharHex() {
        String hash = ApiKeyService.sha256("test-key");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"), "Hash should be lowercase hex");
    }

    @Test
    @DisplayName("Different inputs should produce different hashes")
    void differentInputsShouldProduceDifferentHashes() {
        String hash1 = ApiKeyService.sha256("key-one");
        String hash2 = ApiKeyService.sha256("key-two");
        assertNotEquals(hash1, hash2);
    }
}
