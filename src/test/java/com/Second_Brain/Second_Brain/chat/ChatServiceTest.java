package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.DocumentRepository;
import com.Second_Brain.Second_Brain.Document.RetrievedChunk;
import com.Second_Brain.Second_Brain.User.UserRepository;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberId;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberRepository;
import com.Second_Brain.Second_Brain.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/*
 * Unit tests: repositories, retrieval and the model are mocks, and
 * TransactionOperations.withoutTransaction() runs each "transaction"
 * directly, so no database or Ollama is needed.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-11-04T10:00:00Z");

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private LlmService llmService;

    @Captor
    private ArgumentCaptor<List<Message>> history;

    @Captor
    private ArgumentCaptor<String> userMessage;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(workspaceRepository, memberRepository, userRepository,
                documentRepository, sessionRepository, messageRepository, retrievalService, llmService,
                TransactionOperations.withoutTransaction(), 6);
    }

    @Test
    void answersFromTheRelatedPassagesAndSavesTheQuestionAndTheAnswer() {
        isMember();
        hasReadyDocuments(true);
        savesReturnWhatTheyAreGiven();
        RetrievedChunk notes = passage("notes.md", 0, "Round-robin gives each process\n\na time slice.");
        RetrievedChunk pdf = passage("os.pdf", 6, "Schedulers decide which process runs next.");
        when(retrievalService.findRelevant(WORKSPACE, "What is round-robin?")).thenReturn(List.of(notes, pdf));
        when(llmService.generate(eq(PromptBuilder.SYSTEM_PROMPT), eq(List.of()), anyString()))
                .thenReturn("Each process gets a time slice [1].");

        ChatService.Exchange exchange = chatService.ask(WORKSPACE, USER, null, "  What is round-robin?  ");

        verify(sessionRepository).save(any(ChatSession.class)); // a new chat
        verify(llmService).generate(any(), any(), userMessage.capture());
        assertThat(userMessage.getValue())
                .contains("[1] notes.md, part 1", "[2] os.pdf, part 7")
                .endsWith("Question: What is round-robin?");

        assertThat(exchange.question().getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(exchange.question().getContent()).isEqualTo("What is round-robin?");
        assertThat(exchange.question().getSources()).isEmpty();
        assertThat(exchange.answer().getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(exchange.answer().getContent()).isEqualTo("Each process gets a time slice [1].");
        assertThat(exchange.answer().getSources()).containsExactly(
                new ChatSource(1, notes.documentId(), "notes.md", 0, "Round-robin gives each process a time slice."),
                new ChatSource(2, pdf.documentId(), "os.pdf", 6, "Schedulers decide which process runs next."));
        assertThat(exchange.answer().getCreatedAt()).isAfter(exchange.question().getCreatedAt());

        InOrder inOrder = inOrder(messageRepository);
        inOrder.verify(messageRepository).save(exchange.question());
        inOrder.verify(messageRepository).save(exchange.answer());
    }

    @Test
    void aFollowUpSendsTheEarlierMessagesAndSearchesWithThePreviousQuestion() {
        isMember();
        hasReadyDocuments(true);
        savesReturnWhatTheyAreGiven();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = existingSession(sessionId);
        ChatMessage firstQuestion = new ChatMessage(session, ChatMessage.Role.USER, "What is round-robin?", List.of(), T0);
        ChatMessage firstAnswer = new ChatMessage(session, ChatMessage.Role.ASSISTANT, "Time slices [1].", List.of(), T0.plusSeconds(20));
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, Limit.of(6)))
                .thenReturn(List.of(firstAnswer, firstQuestion)); // newest first
        when(retrievalService.findRelevant(WORKSPACE, "What is round-robin?\nCan you explain that more simply?"))
                .thenReturn(List.of(passage("notes.md", 0, "Round-robin...")));
        when(llmService.generate(any(), anyList(), anyString())).thenReturn("Everyone takes turns [1].");

        ChatService.Exchange exchange = chatService.ask(WORKSPACE, USER, sessionId, "Can you explain that more simply?");

        verify(llmService).generate(any(), history.capture(), anyString());
        assertThat(history.getValue())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, "What is round-robin?"),
                        tuple(MessageType.ASSISTANT, "Time slices [1]."));
        assertThat(exchange.sessionId()).isEqualTo(sessionId);
        assertThat(exchange.question().getSession()).isSameAs(session);
        verify(sessionRepository, never()).save(any()); // no new chat
    }

    @Test
    void aNonMemberIsRefusedBeforeAnythingElseHappens() {
        when(workspaceRepository.existsById(WORKSPACE)).thenReturn(true);
        when(memberRepository.existsById(new WorkspaceMemberId(WORKSPACE, USER))).thenReturn(false);

        assertThatThrownBy(() -> chatService.ask(WORKSPACE, USER, null, "What is round-robin?"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(retrievalService, llmService, sessionRepository, messageRepository);
    }

    @Test
    void anUnknownWorkspaceIsNotFound() {
        when(workspaceRepository.existsById(WORKSPACE)).thenReturn(false);

        assertThatThrownBy(() -> chatService.ask(WORKSPACE, USER, null, "What is round-robin?"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(retrievalService, llmService);
    }

    @Test
    void someoneElsesChatIsNotFound() {
        isMember();
        hasReadyDocuments(true);
        UUID theirSession = UUID.randomUUID();
        when(sessionRepository.findByIdAndWorkspaceIdAndUserId(theirSession, WORKSPACE, USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.ask(WORKSPACE, USER, theirSession, "What did they ask?"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(retrievalService, llmService);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void withoutReadyDocumentsItSaysSoWithoutAskingTheModel() {
        isMember();
        hasReadyDocuments(false);
        savesReturnWhatTheyAreGiven();

        ChatService.Exchange exchange = chatService.ask(WORKSPACE, USER, null, "What is round-robin?");

        assertThat(exchange.answer().getContent()).isEqualTo(ChatService.NO_DOCUMENTS_REPLY);
        assertThat(exchange.answer().getSources()).isEmpty();
        verifyNoInteractions(retrievalService, llmService);
    }

    @Test
    void whenNothingRelatedIsFoundItSaysSoWithoutAskingTheModel() {
        isMember();
        hasReadyDocuments(true);
        savesReturnWhatTheyAreGiven();
        when(retrievalService.findRelevant(WORKSPACE, "What is the capital of France?")).thenReturn(List.of());

        ChatService.Exchange exchange = chatService.ask(WORKSPACE, USER, null, "What is the capital of France?");

        assertThat(exchange.answer().getContent()).isEqualTo(ChatService.NOT_FOUND_REPLY);
        verifyNoInteractions(llmService);
    }

    @Test
    void aModelFailureIsServiceUnavailableAndNothingIsSaved() {
        isMember();
        hasReadyDocuments(true);
        when(retrievalService.findRelevant(any(), any())).thenReturn(List.of(passage("notes.md", 0, "text")));
        when(llmService.generate(any(), any(), any())).thenThrow(new IllegalStateException("Connection refused"));

        assertThatThrownBy(() -> chatService.ask(WORKSPACE, USER, null, "What is round-robin?"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(sessionRepository, never()).save(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void anEmbeddingFailureIsAlsoServiceUnavailable() {
        isMember();
        hasReadyDocuments(true);
        when(retrievalService.findRelevant(any(), any())).thenThrow(new IllegalStateException("Ollama is not running"));

        assertThatThrownBy(() -> chatService.ask(WORKSPACE, USER, null, "What is round-robin?"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void listingMessagesOfSomeoneElsesChatIsNotFound() {
        isMember();
        UUID theirSession = UUID.randomUUID();
        when(sessionRepository.findByIdAndWorkspaceIdAndUserId(theirSession, WORKSPACE, USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.listMessages(WORKSPACE, USER, theirSession))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listingChatsNeedsMembership() {
        when(workspaceRepository.existsById(WORKSPACE)).thenReturn(true);
        when(memberRepository.existsById(new WorkspaceMemberId(WORKSPACE, USER))).thenReturn(false);

        assertThatThrownBy(() -> chatService.listSessions(WORKSPACE, USER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void excerptsAreOneLineAndShortened() {
        assertThat(ChatService.excerpt("  First line.\n\n  Second\tline.  ")).isEqualTo("First line. Second line.");

        String excerpt = ChatService.excerpt("word ".repeat(100));
        assertThat(excerpt).endsWith("…").hasSizeLessThanOrEqualTo(301);

        // 299 letters and then an emoji (two chars): the cut must not split it.
        String withEmoji = ChatService.excerpt("a".repeat(299) + "😀" + "b".repeat(50));
        assertThat(withEmoji).isEqualTo("a".repeat(299) + "…");
    }

    private void isMember() {
        when(workspaceRepository.existsById(WORKSPACE)).thenReturn(true);
        when(memberRepository.existsById(new WorkspaceMemberId(WORKSPACE, USER))).thenReturn(true);
    }

    private void hasReadyDocuments(boolean ready) {
        when(documentRepository.existsByWorkspaceIdAndStatus(WORKSPACE, Document.Status.READY)).thenReturn(ready);
    }

    private void savesReturnWhatTheyAreGiven() {
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // lenient: only a new chat saves a session
        lenient().when(sessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ChatSession existingSession(UUID sessionId) {
        ChatSession session = mock(ChatSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(sessionRepository.findByIdAndWorkspaceIdAndUserId(sessionId, WORKSPACE, USER))
                .thenReturn(Optional.of(session));
        return session;
    }

    private static RetrievedChunk passage(String filename, int chunkIndex, String content) {
        return new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), filename, chunkIndex, content, 0.3);
    }
}
