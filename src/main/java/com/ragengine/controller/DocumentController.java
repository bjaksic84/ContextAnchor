package com.ragengine.controller;

import com.ragengine.domain.dto.DocumentResponse;
import com.ragengine.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for document management operations.
 * Handles document upload, retrieval, and deletion.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document management endpoints")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document",
            description = "Upload a PDF, DOCX, or TXT file for processing. " +
                    "The document will be extracted, chunked, and embedded asynchronously.")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentService.uploadDocument(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all documents",
            description = "Returns all uploaded documents with their processing status.")
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID",
            description = "Returns details and processing status of a specific document.")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document",
            description = "Deletes a document, its chunks, and embeddings from the system.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
    }
}
