package com.Second_Brain.Second_Brain.chat;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Ask a question about the workspace's documents. Slow: a local model takes seconds. */
    @PostMapping
    public ChatAnswerResponse ask(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody ChatRequests.AskRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ChatAnswerResponse.from(
                chatService.ask(workspaceId, userId, request.sessionId(), request.message()));
    }

    @GetMapping("/sessions")
    public List<ChatSessionResponse> sessions(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return chatService.listSessions(workspaceId, userId).stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageResponse> messages(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return chatService.listMessages(workspaceId, userId, sessionId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
