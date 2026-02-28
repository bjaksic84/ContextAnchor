package com.ragengine.exception;

/**
 * Exception thrown when a tenant exceeds their rate limit.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
