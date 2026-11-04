package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.DocumentChunk;
import com.Second_Brain.Second_Brain.Document.EmbeddingService;
import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMember;
import com.Second_Brain.Second_Brain.workspace.workspace;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * The chat API through Spring Security, the controller, the service and the
 * real database (Docker). Only the two Ollama calls are mocked. Each test runs
 * in a transaction that is rolled back; the service's short transactions join it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String encoded = Base64.getEncoder().encodeToString(secret);
        registry.add("app.jwt.secret", () -> encoded);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmbeddingService embeddingService;

    @MockitoBean
    private LlmService llmService;

    private User owner;
    private User otherUser;
    private workspace workspace;

    @BeforeEach
    void setUp() {
        owner = persist(new User("chat-owner-" + UUID.randomUUID() + "@example.com", "not-a-real-hash"));
        otherUser = persist(new User("chat-other-" + UUID.randomUUID() + "@example.com", "not-a-real-hash"));
        workspace = persist(new workspace("Chat API test", owner));
        persist(new WorkspaceMember(workspace, owner, WorkspaceMember.Role.OWNER));

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setWorkspace(workspace);
        document.setUploadedBy(owner);
        document.setFilename("notes.md");
        document.setContentType("text/markdown");
        document.setSizeBytes(42);
        document.setStoragePath("test/notes.md");
        document.setStatus(Document.Status.READY);
        persist(document);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(0);
        chunk.setContent("Round-robin gives each process a time slice.");
        chunk.setEmbedding(vector());
        persist(chunk);
        entityManager.flush();

        // Every question "means" the same as the chunk, so it is always found.
        when(embeddingService.embed(anyString())).thenReturn(vector());
        when(llmService.generate(anyString(), anyList(), anyString())).thenReturn("Each process gets a time slice [1].");
    }

    @Test
    void askingWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(ask("What is round-robin?", null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aNonMemberCannotAsk() throws Exception {
        mockMvc.perform(ask("What is round-robin?", null).with(as(otherUser)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(embeddingService, llmService);
    }

    @Test
    void aBlankQuestionIsRejected() throws Exception {
        mockMvc.perform(ask("   ", null).with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMemberGetsAnAnswerWithSourcesAndTheChatIsSaved() throws Exception {
        String body = mockMvc.perform(ask("What is round-robin?", null).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.role").value("USER"))
                .andExpect(jsonPath("$.question.content").value("What is round-robin?"))
                .andExpect(jsonPath("$.answer.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.answer.content").value("Each process gets a time slice [1]."))
                .andExpect(jsonPath("$.answer.sources[0].number").value(1))
                .andExpect(jsonPath("$.answer.sources[0].filename").value("notes.md"))
                .andExpect(jsonPath("$.answer.sources[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.answer.sources[0].excerpt").value("Round-robin gives each process a time slice."))
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(body, "$.sessionId");

        mockMvc.perform(get(sessionsPath()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sessionId))
                .andExpect(jsonPath("$[0].title").value("What is round-robin?"));

        mockMvc.perform(get(sessionsPath() + "/" + sessionId + "/messages").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].sources[0].filename").value("notes.md"));
    }

    @Test
    void chatsArePrivateEvenInASharedWorkspace() throws Exception {
        persist(new WorkspaceMember(workspace, otherUser, WorkspaceMember.Role.MEMBER));
        entityManager.flush();
        String body = mockMvc.perform(ask("What is round-robin?", null).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ownersChat = JsonPath.read(body, "$.sessionId");

        // The other member sees no chats, can't read the owner's, and can't add to it.
        mockMvc.perform(get(sessionsPath()).with(as(otherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(sessionsPath() + "/" + ownersChat + "/messages").with(as(otherUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(ask("What did they ask?", ownersChat).with(as(otherUser)))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder ask(String message, String sessionId) {
        String sessionField = sessionId == null ? "" : "\"sessionId\": \"" + sessionId + "\", ";
        return post("/api/workspaces/" + workspace.getId() + "/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + sessionField + "\"message\": \"" + message + "\"}");
    }

    private String sessionsPath() {
        return "/api/workspaces/" + workspace.getId() + "/chat/sessions";
    }

    // A request made as this user, like one carrying a valid token from login.
    private static RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    // The same 768-dimension vector for the chunk and every question.
    private static float[] vector() {
        float[] vector = new float[768];
        vector[0] = 1;
        return vector;
    }
}
