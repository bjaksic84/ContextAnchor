package com.ragengine.service;

import com.ragengine.domain.entity.Document;
import com.ragengine.domain.entity.DocumentChunk;
import com.ragengine.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for generating vector embeddings from document chunks
 * and storing them in the PGVector store for similarity search.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;
    private final DocumentChunkRepository chunkRepository;

    /**
     * Generates embeddings for all chunks of a document and stores them in the vector store.
     *
     * @param document   the document entity
     * @param chunks     the document chunk entities
     */
    public void embedAndStore(Document document, List<DocumentChunk> chunks) {
        log.info("Generating embeddings for {} chunks of document '{}'",
                chunks.size(), document.getOriginalName());

        List<org.springframework.ai.document.Document> aiDocuments = chunks.stream()
                .map(chunk -> new org.springframework.ai.document.Document(
                        chunk.getId().toString(),
                        chunk.getContent(),
                        Map.of(
                                "documentId", document.getId().toString(),
                                "documentName", document.getOriginalName(),
                                "chunkIndex", chunk.getChunkIndex(),
                                "pageNumber", chunk.getPageNumber() != null ? chunk.getPageNumber() : -1
                        )
                ))
                .toList();

        // Store in vector store (this automatically generates embeddings)
        vectorStore.add(aiDocuments);

        log.info("Successfully stored {} embeddings for document '{}'",
                aiDocuments.size(), document.getOriginalName());
    }

    /**
     * Removes all embeddings associated with a document from the vector store.
     *
     * @param documentId the document ID
     */
    public void removeEmbeddings(UUID documentId) {
        log.info("Removing embeddings for document: {}", documentId);

        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        List<String> ids = chunks.stream()
                .map(chunk -> chunk.getId().toString())
                .toList();

        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
            log.info("Removed {} embeddings for document: {}", ids.size(), documentId);
        }
    }
}
