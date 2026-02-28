package com.ragengine.service;

import com.ragengine.domain.dto.DocumentResponse;
import com.ragengine.domain.entity.Document;
import com.ragengine.domain.entity.DocumentChunk;
import com.ragengine.domain.entity.DocumentStatus;
import com.ragengine.domain.entity.Tenant;
import com.ragengine.domain.entity.User;
import com.ragengine.exception.DocumentNotFoundException;
import com.ragengine.exception.DocumentProcessingException;
import com.ragengine.repository.DocumentChunkRepository;
import com.ragengine.repository.DocumentRepository;
import com.ragengine.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Main orchestrator service for document upload and processing pipeline.
 * Handles the full lifecycle: upload → extract → chunk → embed → ready.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentExtractionService extractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final SecurityContext securityContext;

    @Value("${rag.upload.storage-path:./uploads}")
    private String storagePath;

    @Value("${rag.upload.allowed-types}")
    private List<String> allowedTypes;

    /**
     * Uploads a document and triggers async processing.
     *
     * @param file the uploaded file
     * @return document response with initial status
     */
    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {
        validateFile(file);

        User currentUser = securityContext.getCurrentUser();
        Tenant tenant = currentUser.getTenant();

        // Generate unique filename
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();

        // Save file to disk
        Path filePath = saveFile(file, filename);

        // Create document entity
        Document document = Document.builder()
                .filename(filename)
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(DocumentStatus.UPLOADED)
                .tenant(tenant)
                .uploadedBy(currentUser)
                .build();

        document = documentRepository.save(document);
        log.info("Document uploaded: {} (ID: {})", document.getOriginalName(), document.getId());

        // Trigger async processing
        processDocumentAsync(document.getId(), file);

        return mapToResponse(document);
    }

    /**
     * Async processing pipeline: extract → chunk → embed
     */
    @Async
    public void processDocumentAsync(UUID documentId, MultipartFile file) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        try {
            // Step 1: Extract text
            updateStatus(document, DocumentStatus.PROCESSING);
            String extractedText = extractionService.extractText(file);
            Integer pageCount = extractionService.extractPageCount(file);
            document.setPageCount(pageCount);

            if (extractedText.isBlank()) {
                throw new DocumentProcessingException("No text could be extracted from the document");
            }

            // Step 2: Chunk the text
            updateStatus(document, DocumentStatus.CHUNKING);
            List<String> textChunks = chunkingService.chunkText(extractedText);
            log.info("Document '{}' split into {} chunks", document.getOriginalName(), textChunks.size());

            // Save chunks to database
            List<DocumentChunk> chunks = saveChunks(document, textChunks);

            // Step 3: Generate embeddings and store in vector DB
            updateStatus(document, DocumentStatus.EMBEDDING);
            embeddingService.embedAndStore(document, chunks);

            // Mark as ready
            updateStatus(document, DocumentStatus.READY);
            log.info("Document '{}' processing complete. {} chunks embedded.",
                    document.getOriginalName(), chunks.size());

        } catch (Exception e) {
            log.error("Failed to process document '{}': {}", document.getOriginalName(), e.getMessage(), e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }

    /**
     * Gets all documents for the current tenant, ordered by creation date.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        UUID tenantId = securityContext.getCurrentTenantId();
        return documentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Gets a document by ID, scoped to the current tenant.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID id) {
        UUID tenantId = securityContext.getCurrentTenantId();
        Document document = documentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return mapToResponse(document);
    }

    /**
     * Deletes a document and its associated chunks and embeddings.
     * Scoped to the current tenant.
     */
    @Transactional
    public void deleteDocument(UUID id) {
        UUID tenantId = securityContext.getCurrentTenantId();
        Document document = documentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        // Remove embeddings from vector store
        embeddingService.removeEmbeddings(id);

        // Delete from database (cascades to chunks)
        documentRepository.delete(document);

        // Delete file from disk
        try {
            Path filePath = Paths.get(storagePath, document.getFilename());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Could not delete file from disk: {}", document.getFilename(), e);
        }

        log.info("Document deleted: {} (ID: {})", document.getOriginalName(), id);
    }

    // ============================
    // Private helper methods
    // ============================

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new DocumentProcessingException("File is empty");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new DocumentProcessingException("File name is missing");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new DocumentProcessingException(
                    "Unsupported file type: " + contentType + ". Allowed types: " + allowedTypes);
        }
    }

    private Path saveFile(MultipartFile file, String filename) {
        try {
            Path uploadDir = Paths.get(storagePath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath);
            return filePath;
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to save file: " + e.getMessage());
        }
    }

    private List<DocumentChunk> saveChunks(Document document, List<String> textChunks) {
        List<DocumentChunk> chunks = new java.util.ArrayList<>();

        for (int i = 0; i < textChunks.size(); i++) {
            String text = textChunks.get(i);
            DocumentChunk chunk = DocumentChunk.builder()
                    .document(document)
                    .content(text)
                    .chunkIndex(i)
                    .tokenCount(chunkingService.estimateTokenCount(text))
                    .build();
            chunks.add(chunk);
        }

        return chunkRepository.saveAll(chunks);
    }

    private void updateStatus(Document document, DocumentStatus status) {
        document.setStatus(status);
        documentRepository.save(document);
        log.debug("Document '{}' status updated to: {}", document.getOriginalName(), status);
    }

    private DocumentResponse mapToResponse(Document document) {
        int chunkCount = chunkRepository.countByDocumentId(document.getId());
        return DocumentResponse.builder()
                .id(document.getId())
                .originalName(document.getOriginalName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .pageCount(document.getPageCount())
                .status(document.getStatus())
                .errorMessage(document.getErrorMessage())
                .chunkCount(chunkCount)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
