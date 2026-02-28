package com.ragengine.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document not found with ID: " + id);
    }

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
