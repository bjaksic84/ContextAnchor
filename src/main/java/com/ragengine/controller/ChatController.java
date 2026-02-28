package com.ragengine.controller;

import com.ragengine.domain.dto.ChatRequest;
import com.ragengine.domain.dto.ChatResponse;
import com.ragengine.domain.dto.ConversationResponse;
import com.ragengine.service.RagChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the RAG chat functionality.
 * Handles chat interactions and conversation management.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "RAG chat and conversation endpoints")
public class ChatController {

    private final RagChatService ragChatService;

    @PostMapping
    @Operation(summary = "Ask a question",
            description = "Send a question to be answered using the RAG pipeline. " +
                    "Specify which documents to search and optionally provide a conversation ID " +
                    "for multi-turn conversations.")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragChatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations")
    @Operation(summary = "List all conversations",
            description = "Returns all conversations ordered by most recently updated.")
    public ResponseEntity<List<ConversationResponse>> getAllConversations() {
        return ResponseEntity.ok(ragChatService.getAllConversations());
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation by ID",
            description = "Returns a conversation with its full message history.")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable UUID id) {
        return ResponseEntity.ok(ragChatService.getConversation(id));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete a conversation",
            description = "Deletes a conversation and all its messages.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable UUID id) {
        ragChatService.deleteConversation(id);
    }
}
