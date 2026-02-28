package com.ragengine.service;

import com.ragengine.domain.dto.ChatRequest;
import com.ragengine.domain.dto.ChatResponse;
import com.ragengine.domain.dto.ConversationResponse;
import com.ragengine.domain.entity.ChatMessage;
import com.ragengine.domain.entity.Conversation;
import com.ragengine.domain.entity.Document;
import com.ragengine.domain.entity.DocumentStatus;
import com.ragengine.exception.DocumentNotFoundException;
import com.ragengine.repository.ChatMessageRepository;
import com.ragengine.repository.ConversationRepository;
import com.ragengine.repository.DocumentRepository;
import com.ragengine.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core RAG (Retrieval-Augmented Generation) service.
 * Handles the retrieval of relevant document chunks and generation of AI responses.
 * 
 * Pipeline:
 * 1. User asks a question with document scope
 * 2. Question is embedded and similar chunks are retrieved from pgvector
 * 3. Retrieved chunks are injected as context into the prompt
 * 4. LLM generates an answer grounded in the retrieved context
 * 5. Response includes source citations for transparency
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagChatService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final SecurityContext securityContext;

    @Value("${rag.chat.top-k-results:5}")
    private int topKResults;

    @Value("${rag.chat.max-history-size:10}")
    private int maxHistorySize;

    @Value("${rag.chat.system-prompt}")
    private String systemPrompt;

    /**
     * Processes a chat request using the RAG pipeline.
     *
     * @param request the chat request containing the question and document scope
     * @return the AI-generated response with source citations
     */
    @Transactional
    public ChatResponse chat(ChatRequest request) {
        log.info("Processing chat request: '{}'", request.question());

        // Validate that all requested documents are ready
        validateDocuments(request.documentIds());

        // Step 1: Retrieve relevant chunks from vector store
        List<org.springframework.ai.document.Document> relevantDocs = retrieveRelevantChunks(
                request.question(), request.documentIds());

        log.info("Retrieved {} relevant chunks for question", relevantDocs.size());

        // Step 2: Get or create conversation
        Conversation conversation = getOrCreateConversation(request);

        // Step 3: Build context from retrieved chunks
        String context = buildContext(relevantDocs);

        // Step 4: Build conversation history
        List<Message> messageHistory = buildMessageHistory(conversation);

        // Step 5: Generate response using LLM
        String augmentedQuestion = buildAugmentedPrompt(request.question(), context);

        messageHistory.add(new UserMessage(augmentedQuestion));

        ChatClient chatClient = chatClientBuilder.build();

        String aiResponse = chatClient.prompt()
                .system(systemPrompt)
                .messages(messageHistory)
                .call()
                .content();

        // Step 6: Build source citations
        List<ChatResponse.Source> sources = buildSources(relevantDocs);

        // Step 7: Save messages to conversation
        saveMessage(conversation, "user", request.question(), null);
        saveMessage(conversation, "assistant", aiResponse, sources);

        // Update conversation title if it's new
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(truncate(request.question(), 100));
            conversationRepository.save(conversation);
        }

        log.info("Chat response generated for conversation: {}", conversation.getId());

        return ChatResponse.builder()
                .conversationId(conversation.getId())
                .answer(aiResponse)
                .sources(sources)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Gets all conversations for the current tenant.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getAllConversations() {
        UUID tenantId = securityContext.getCurrentTenantId();
        return conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .map(this::mapConversation)
                .toList();
    }

    /**
     * Gets a conversation by ID, scoped to the current tenant.
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID id) {
        UUID tenantId = securityContext.getCurrentTenantId();
        Conversation conversation = conversationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
        return mapConversation(conversation);
    }

    /**
     * Deletes a conversation, scoped to the current tenant.
     */
    @Transactional
    public void deleteConversation(UUID id) {
        UUID tenantId = securityContext.getCurrentTenantId();
        Conversation conversation = conversationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
        conversationRepository.delete(conversation);
        log.info("Conversation deleted: {}", id);
    }

    // ============================
    // RAG Pipeline Methods
    // ============================

    /**
     * Retrieves relevant document chunks from the vector store using similarity search,
     * filtered to only include chunks from the specified documents and the current tenant.
     */
    private List<org.springframework.ai.document.Document> retrieveRelevantChunks(
            String query, List<UUID> documentIds) {

        UUID tenantId = securityContext.getCurrentTenantId();

        // Build filter to restrict search to specified documents within the tenant
        String docFilter = documentIds.stream()
                .map(id -> "documentId == '" + id.toString() + "'")
                .collect(Collectors.joining(" || "));
        String filterExpression = "tenantId == '" + tenantId.toString() + "' && (" + docFilter + ")";

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topKResults)
                .filterExpression(filterExpression)
                .build();

        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * Builds the context string from retrieved document chunks.
     * Each chunk is labeled with its source document and index for citation.
     */
    private String buildContext(List<org.springframework.ai.document.Document> relevantDocs) {
        if (relevantDocs.isEmpty()) {
            return "No relevant context found in the uploaded documents.";
        }

        StringBuilder context = new StringBuilder();
        context.append("=== RELEVANT DOCUMENT CONTEXT ===\n\n");

        for (int i = 0; i < relevantDocs.size(); i++) {
            org.springframework.ai.document.Document doc = relevantDocs.get(i);
            Map<String, Object> metadata = doc.getMetadata();

            String docName = (String) metadata.getOrDefault("documentName", "Unknown");
            Object chunkIdx = metadata.getOrDefault("chunkIndex", "?");

            context.append(String.format("[Source %d - %s, Chunk %s]\n", i + 1, docName, chunkIdx));
            context.append(doc.getText());
            context.append("\n\n---\n\n");
        }

        return context.toString();
    }

    /**
     * Builds the augmented prompt that includes the retrieved context.
     */
    private String buildAugmentedPrompt(String question, String context) {
        return """
                Based on the following context from the uploaded documents, please answer my question.
                If the context doesn't contain enough information, clearly state that.
                Always reference which source(s) you're using in your answer.
                
                %s
                
                === QUESTION ===
                %s
                """.formatted(context, question);
    }

    /**
     * Builds conversation history from stored messages.
     * Limits to the most recent messages to stay within context window.
     */
    private List<Message> buildMessageHistory(Conversation conversation) {
        List<Message> messages = new ArrayList<>();

        List<ChatMessage> storedMessages = conversation.getMessages();
        int startIdx = Math.max(0, storedMessages.size() - maxHistorySize);

        for (int i = startIdx; i < storedMessages.size(); i++) {
            ChatMessage msg = storedMessages.get(i);
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        return messages;
    }

    /**
     * Builds source citations from retrieved documents.
     */
    private List<ChatResponse.Source> buildSources(List<org.springframework.ai.document.Document> relevantDocs) {
        return relevantDocs.stream()
                .map(doc -> {
                    Map<String, Object> metadata = doc.getMetadata();
                    return ChatResponse.Source.builder()
                            .documentId(UUID.fromString((String) metadata.get("documentId")))
                            .documentName((String) metadata.getOrDefault("documentName", "Unknown"))
                            .chunkContent(truncate(doc.getText(), 200))
                            .chunkIndex((Integer) metadata.getOrDefault("chunkIndex", 0))
                            .pageNumber(parsePageNumber(metadata.get("pageNumber")))
                            .similarityScore(doc.getScore() != null ? doc.getScore() : null)
                            .build();
                })
                .toList();
    }

    // ============================
    // Helper Methods
    // ============================

    private void validateDocuments(List<UUID> documentIds) {
        UUID tenantId = securityContext.getCurrentTenantId();
        List<Document> documents = documentRepository.findByIdInAndTenantId(documentIds, tenantId);

        if (documents.size() != documentIds.size()) {
            throw new DocumentNotFoundException("One or more documents not found in your organization");
        }

        List<Document> notReady = documents.stream()
                .filter(doc -> doc.getStatus() != DocumentStatus.READY)
                .toList();

        if (!notReady.isEmpty()) {
            String names = notReady.stream()
                    .map(d -> d.getOriginalName() + " (" + d.getStatus() + ")")
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Documents not ready for querying: " + names);
        }
    }

    private Conversation getOrCreateConversation(ChatRequest request) {
        UUID tenantId = securityContext.getCurrentTenantId();
        if (request.conversationId() != null) {
            return conversationRepository.findByIdAndTenantId(request.conversationId(), tenantId)
                    .orElseGet(this::createNewConversation);
        }
        return createNewConversation();
    }

    private Conversation createNewConversation() {
        return conversationRepository.save(
                Conversation.builder()
                        .tenant(securityContext.getCurrentUser().getTenant())
                        .createdBy(securityContext.getCurrentUser())
                        .build());
    }

    private void saveMessage(Conversation conversation, String role, String content,
                             List<ChatResponse.Source> sources) {
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .build();
        chatMessageRepository.save(message);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private Integer parsePageNumber(Object pageNumber) {
        if (pageNumber == null) return null;
        if (pageNumber instanceof Integer num) return num == -1 ? null : num;
        try {
            int num = Integer.parseInt(pageNumber.toString());
            return num == -1 ? null : num;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ConversationResponse mapConversation(Conversation conversation) {
        List<ConversationResponse.MessageResponse> messages = conversation.getMessages().stream()
                .map(msg -> ConversationResponse.MessageResponse.builder()
                        .id(msg.getId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .toList();

        List<UUID> docIds = conversation.getDocuments().stream()
                .map(Document::getId)
                .toList();

        return ConversationResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .messages(messages)
                .documentIds(docIds)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
