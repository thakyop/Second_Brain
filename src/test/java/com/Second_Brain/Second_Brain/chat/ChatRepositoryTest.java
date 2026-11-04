package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.workspace.workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Runs against the real PostgreSQL database from docker-compose, so the
 * JSONB column and Hibernate's schema validation are the real thing. Each
 * test is rolled back afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-04T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    private User user;
    private workspace workspace;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(new User("chat-test-" + UUID.randomUUID() + "@example.com", "not-a-real-hash"));
        workspace = entityManager.persist(new workspace("Chat test", user));
    }

    @Test
    void sourcesAreStoredAsJsonAndReadBackUnchanged() {
        ChatSession session = entityManager.persist(new ChatSession(workspace, user));
        List<ChatSource> sources = List.of(
                new ChatSource(1, UUID.randomUUID(), "notes.md", 0, "Round-robin gives each process a time slice."),
                new ChatSource(2, UUID.randomUUID(), "os.pdf", 7, "The scheduler picks the next process."));
        ChatMessage answer = entityManager.persist(
                new ChatMessage(session, ChatMessage.Role.ASSISTANT, "It uses time slices [1].", sources, T0));
        entityManager.flush();
        entityManager.clear(); // forget cached entities, so the next read hits the database

        ChatMessage readBack = messageRepository.findById(answer.getId()).orElseThrow();

        assertThat(readBack.getSources()).containsExactlyElementsOf(sources);
        assertThat(readBack.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
    }

    @Test
    void aQuestionHasAnEmptySourceList() {
        ChatSession session = entityManager.persist(new ChatSession(workspace, user));
        ChatMessage question = entityManager.persist(
                new ChatMessage(session, ChatMessage.Role.USER, "What is round-robin?", List.of(), T0));
        entityManager.flush();
        entityManager.clear();

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("select sources::text from chat_messages where id = :id")
                .setParameter("id", question.getId())
                .getSingleResult();

        assertThat(stored).isEqualTo("[]");
        assertThat(messageRepository.findById(question.getId()).orElseThrow().getSources()).isEmpty();
    }

    @Test
    void messagesComeBackInConversationOrder() {
        ChatSession session = entityManager.persist(new ChatSession(workspace, user));
        save(session, ChatMessage.Role.ASSISTANT, "first answer", 1);
        save(session, ChatMessage.Role.USER, "first question", 0);
        save(session, ChatMessage.Role.USER, "second question", 2);
        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()))
                .extracting(ChatMessage::getContent)
                .containsExactly("first question", "first answer", "second question");
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtDesc(session.getId(), Limit.of(2)))
                .extracting(ChatMessage::getContent)
                .containsExactly("second question", "first answer");
    }

    @Test
    void aSessionIsOnlyFoundForItsOwnUserAndWorkspace() {
        ChatSession session = entityManager.persist(new ChatSession(workspace, user));
        User otherUser = entityManager.persist(new User("other-" + UUID.randomUUID() + "@example.com", "hash"));
        workspace otherWorkspace = entityManager.persist(new workspace("Other", user));

        assertThat(sessionRepository.findByIdAndWorkspaceIdAndUserId(session.getId(), workspace.getId(), user.getId()))
                .isPresent();
        assertThat(sessionRepository.findByIdAndWorkspaceIdAndUserId(session.getId(), workspace.getId(), otherUser.getId()))
                .isEmpty();
        assertThat(sessionRepository.findByIdAndWorkspaceIdAndUserId(session.getId(), otherWorkspace.getId(), user.getId()))
                .isEmpty();
    }

    @Test
    void summariesListTheUsersChatsNewestFirstWithTheirFirstQuestion() throws InterruptedException {
        ChatSession older = entityManager.persist(new ChatSession(workspace, user));
        save(older, ChatMessage.Role.USER, "What is round-robin?", 0);
        save(older, ChatMessage.Role.ASSISTANT, "An answer", 1);
        save(older, ChatMessage.Role.USER, "And shortest job first?", 2);
        entityManager.flush();
        Thread.sleep(5); // sessions are stamped with Instant.now(); make sure the next one is later
        ChatSession newer = entityManager.persist(new ChatSession(workspace, user));
        save(newer, ChatMessage.Role.USER, "What is a page fault?", 3);
        // Someone else's chat in the same workspace must not be listed.
        User otherUser = entityManager.persist(new User("other-" + UUID.randomUUID() + "@example.com", "hash"));
        ChatSession someoneElses = entityManager.persist(new ChatSession(workspace, otherUser));
        save(someoneElses, ChatMessage.Role.USER, "Not yours", 4);
        entityManager.flush();
        entityManager.clear();

        List<ChatSessionSummary> summaries = sessionRepository.findSummaries(workspace.getId(), user.getId());

        assertThat(summaries).extracting(ChatSessionSummary::id).containsExactly(newer.getId(), older.getId());
        assertThat(summaries).extracting(ChatSessionSummary::firstQuestion)
                .containsExactly("What is a page fault?", "What is round-robin?");
    }

    private void save(ChatSession session, ChatMessage.Role role, String content, int minutesAfterT0) {
        entityManager.persist(new ChatMessage(session, role, content, List.of(), T0.plusSeconds(60L * minutesAfterT0)));
    }
}
