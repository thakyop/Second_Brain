package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.DocumentRepository;
import com.Second_Brain.Second_Brain.Document.RetrievedChunk;
import com.Second_Brain.Second_Brain.User.UserRepository;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberId;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberRepository;
import com.Second_Brain.Second_Brain.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Answers questions about a workspace's documents (retrieval-augmented
 * generation) and keeps the conversation.
 *
 * Like IngestionService, the slow work (embedding the question, waiting
 * for the model) runs outside any database transaction. One short
 * transaction checks access before it, and another saves the question and
 * the answer after it, so no connection is held while the model writes.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final String NO_DOCUMENTS_REPLY =
            "This workspace has no processed documents yet. Upload a file, wait until it's Ready, and ask again.";
    static final String NOT_FOUND_REPLY =
            "I couldn't find anything about that in this workspace's documents.";

    private static final int EXCERPT_LENGTH = 300;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final TransactionOperations transactions;
    private final int historyMessages;

    public ChatService(WorkspaceRepository workspaceRepository,
                       WorkspaceMemberRepository memberRepository,
                       UserRepository userRepository,
                       DocumentRepository documentRepository,
                       ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       RetrievalService retrievalService,
                       LlmService llmService,
                       TransactionOperations transactions,
                       @Value("${app.chat.history-messages}") int historyMessages) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.transactions = transactions;
        this.historyMessages = historyMessages;
    }

    /** What ask() returns: the chat it belongs to, and the saved question and answer. */
    public record Exchange(UUID sessionId, ChatMessage question, ChatMessage answer) {
    }

    private record Conversation(boolean hasReadyDocuments, List<Message> history, String previousQuestion) {
    }

    private record Reply(String text, List<ChatSource> sources) {
    }

    /**
     * Answers `message` and saves both. Without a sessionId a new chat is
     * started. Not @Transactional on purpose; see the class comment.
     */
    public Exchange ask(UUID workspaceId, UUID userId, UUID sessionId, String message) {
        Instant askedAt = now();
        String question = message.strip();

        // 1. Short transaction: access checks, and the conversation so far.
        Conversation conversation = transactions.execute(status ->
                loadConversation(workspaceId, userId, sessionId));

        // 2. No transaction: find related passages and ask the model.
        Reply reply = answer(workspaceId, question, conversation);
        Instant answeredAt = laterThan(askedAt);

        // 3. Short transaction: save the question and its answer together.
        return transactions.execute(status ->
                save(workspaceId, userId, sessionId, question, askedAt, reply, answeredAt));
    }

    /** Your chats in this workspace, newest first. */
    @Transactional(readOnly = true)
    public List<ChatSessionSummary> listSessions(UUID workspaceId, UUID userId) {
        checkMember(workspaceId, userId);
        return sessionRepository.findSummaries(workspaceId, userId);
    }

    /** Every message of one of your chats, oldest first. */
    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(UUID workspaceId, UUID userId, UUID sessionId) {
        checkMember(workspaceId, userId);
        ChatSession session = findOwnSession(workspaceId, userId, sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
    }

    private Conversation loadConversation(UUID workspaceId, UUID userId, UUID sessionId) {
        checkMember(workspaceId, userId);
        boolean hasReadyDocuments =
                documentRepository.existsByWorkspaceIdAndStatus(workspaceId, Document.Status.READY);
        if (sessionId == null) {
            return new Conversation(hasReadyDocuments, List.of(), null);
        }
        ChatSession session = findOwnSession(workspaceId, userId, sessionId);
        List<ChatMessage> recent = historyMessages <= 0 ? List.of() : messageRepository
                .findBySessionIdOrderByCreatedAtDesc(session.getId(), Limit.of(historyMessages))
                .reversed(); // oldest first, as the model reads a conversation
        String previousQuestion = recent.reversed().stream()
                .filter(m -> m.getRole() == ChatMessage.Role.USER)
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse(null);
        List<Message> history = recent.stream().map(ChatService::toModelMessage).toList();
        return new Conversation(hasReadyDocuments, history, previousQuestion);
    }

    private Reply answer(UUID workspaceId, String question, Conversation conversation) {
        if (!conversation.hasReadyDocuments()) {
            return new Reply(NO_DOCUMENTS_REPLY, List.of());
        }
        // A follow-up like "explain that more simply" says little on its own,
        // so the search also uses the previous question.
        String searchText = conversation.previousQuestion() == null
                ? question
                : conversation.previousQuestion() + "\n" + question;
        try {
            List<RetrievedChunk> passages = retrievalService.findRelevant(workspaceId, searchText);
            if (passages.isEmpty()) {
                return new Reply(NOT_FOUND_REPLY, List.of());
            }
            String text = llmService.generate(
                    PromptBuilder.SYSTEM_PROMPT,
                    conversation.history(),
                    PromptBuilder.userMessage(passages, question));
            return new Reply(text, sourcesFrom(passages));
        } catch (RuntimeException e) {
            // Ollama not running, the model not pulled, a timeout... The full
            // cause goes to the log (never the question text); the client gets 503.
            log.error("Could not answer a question in workspace {}", workspaceId, e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "The language model is not available", e);
        }
    }

    private Exchange save(UUID workspaceId, UUID userId, UUID sessionId,
                          String question, Instant askedAt, Reply reply, Instant answeredAt) {
        ChatSession session = sessionId == null
                ? sessionRepository.save(new ChatSession(
                        workspaceRepository.getReferenceById(workspaceId),
                        userRepository.getReferenceById(userId)))
                : findOwnSession(workspaceId, userId, sessionId);
        ChatMessage savedQuestion = messageRepository.save(new ChatMessage(
                session, ChatMessage.Role.USER, question, List.of(), askedAt));
        ChatMessage savedAnswer = messageRepository.save(new ChatMessage(
                session, ChatMessage.Role.ASSISTANT, reply.text(), reply.sources(), answeredAt));
        return new Exchange(session.getId(), savedQuestion, savedAnswer);
    }

    private void checkMember(UUID workspaceId, UUID userId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        if (!memberRepository.existsById(new WorkspaceMemberId(workspaceId, userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this workspace");
        }
    }

    private ChatSession findOwnSession(UUID workspaceId, UUID userId, UUID sessionId) {
        return sessionRepository.findByIdAndWorkspaceIdAndUserId(sessionId, workspaceId, userId)
                // 404 rather than 403, so nobody can find out which chats exist.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private static Message toModelMessage(ChatMessage message) {
        return message.getRole() == ChatMessage.Role.USER
                ? new UserMessage(message.getContent())
                : new AssistantMessage(message.getContent());
    }

    private static List<ChatSource> sourcesFrom(List<RetrievedChunk> passages) {
        List<ChatSource> sources = new ArrayList<>(passages.size());
        for (int i = 0; i < passages.size(); i++) {
            RetrievedChunk passage = passages.get(i);
            sources.add(new ChatSource(i + 1, passage.documentId(), passage.filename(),
                    passage.chunkIndex(), excerpt(passage.content())));
        }
        return sources;
    }

    /** The start of a passage on one line, for showing next to an answer. */
    static String excerpt(String content) {
        String text = content.strip().replaceAll("\\s+", " ");
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        int end = EXCERPT_LENGTH;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--; // don't cut an emoji or other two-char character in half
        }
        return text.substring(0, end).strip() + "…";
    }

    // PostgreSQL stores microseconds, so timestamps are kept at that precision.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Now, but always after `earlier`, so an answer sorts after its question. */
    private static Instant laterThan(Instant earlier) {
        Instant now = now();
        return now.isAfter(earlier) ? now : earlier.plus(1, ChronoUnit.MICROS);
    }
}
