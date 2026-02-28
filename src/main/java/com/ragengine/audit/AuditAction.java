package com.ragengine.audit;

/**
 * Constants for audit log action types.
 * Used to standardize action names across the system.
 */
public final class AuditAction {

    private AuditAction() {} // prevent instantiation

    // Authentication
    public static final String USER_REGISTER = "USER_REGISTER";
    public static final String USER_LOGIN = "USER_LOGIN";
    public static final String USER_LOGIN_FAILED = "USER_LOGIN_FAILED";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String USER_LOGOUT = "USER_LOGOUT";

    // Documents
    public static final String DOCUMENT_UPLOAD = "DOCUMENT_UPLOAD";
    public static final String DOCUMENT_DELETE = "DOCUMENT_DELETE";
    public static final String DOCUMENT_PROCESSING_COMPLETE = "DOCUMENT_PROCESSING_COMPLETE";
    public static final String DOCUMENT_PROCESSING_FAILED = "DOCUMENT_PROCESSING_FAILED";

    // Chat
    public static final String CHAT_QUERY = "CHAT_QUERY";
    public static final String CONVERSATION_DELETE = "CONVERSATION_DELETE";

    // API Keys
    public static final String API_KEY_CREATED = "API_KEY_CREATED";
    public static final String API_KEY_REVOKED = "API_KEY_REVOKED";

    // Rate Limiting
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
}
