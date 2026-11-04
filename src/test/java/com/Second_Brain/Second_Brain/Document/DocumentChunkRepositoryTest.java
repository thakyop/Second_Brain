package com.Second_Brain.Second_Brain.Document;

import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.workspace.workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Runs against the real PostgreSQL + pgvector database from docker-compose
 * (start it with `docker compose up -d`). Each test runs in a transaction
 * that is rolled back afterwards, so no data is left behind.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentChunkRepositoryTest {

    private static final int DIMENSIONS = 768;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    private User user;
    private workspace workspace;
    private Document document;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(new User(
                "chunk-test-" + UUID.randomUUID() + "@example.com", "not-a-real-hash"));
        workspace = entityManager.persist(new workspace("Chunk test", user));
        document = newDocument(workspace, Document.Status.READY);
    }

    @Test
    void embeddingIsStoredAndReadBackUnchanged() {
        float[] embedding = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            embedding[i] = (float) Math.sin(i);
        }
        saveChunk(0, "Round-robin scheduling", embedding);
        entityManager.flush();
        entityManager.clear(); // forget cached entities, so the next read hits the database

        List<DocumentChunk> chunks =
                chunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getEmbedding()).containsExactly(embedding);
    }

    @Test
    void chunksCanBeOrderedByCosineDistance() {
        saveChunk(0, "about scheduling", vector(1, 0, 0));
        saveChunk(1, "about memory", vector(0, 1, 0));
        saveChunk(2, "about files", vector(0, 0, 1));
        entityManager.flush();
        entityManager.clear();

        // Mostly "memory", a little "files", nothing of "scheduling".
        float[] question = vector(0, 1, 0.5f);
        List<Integer> closestFirst = entityManager.getEntityManager()
                .createQuery("""
                    select c.chunkIndex
                    from DocumentChunk c
                    where c.document.id = :documentId
                    order by cosine_distance(c.embedding, :question)
                    """, Integer.class)
                .setParameter("documentId", document.getId())
                .setParameter("question", question)
                .getResultList();

        assertThat(closestFirst).containsExactly(1, 2, 0);
    }

    @Test
    void similaritySearchReturnsTheClosestChunksFirstUpToTheLimit() {
        saveChunk(0, "about scheduling", vector(1, 0, 0));
        saveChunk(1, "about memory", vector(0, 1, 0));
        saveChunk(2, "about files", vector(0, 0, 1));
        entityManager.flush();
        entityManager.clear();

        List<RetrievedChunk> found = chunkRepository.findClosestInWorkspace(
                workspace.getId(), vector(0, 1, 0.5f), Limit.of(2));

        assertThat(found).extracting(RetrievedChunk::chunkIndex).containsExactly(1, 2);
        assertThat(found.get(0).distance()).isLessThan(found.get(1).distance());
        assertThat(found.get(0).documentId()).isEqualTo(document.getId());
        assertThat(found.get(0).filename()).isEqualTo("notes.txt");
        assertThat(found.get(0).content()).isEqualTo("about memory");
    }

    @Test
    void similaritySearchOnlySeesReadyDocumentsOfTheGivenWorkspace() {
        float[] question = vector(1, 0, 0);
        saveChunk(document, 0, "ready, in this workspace", vector(1, 0.1f, 0));
        saveChunk(newDocument(workspace, Document.Status.PROCESSING), 0, "still processing", question);
        saveChunk(newDocument(workspace, Document.Status.FAILED), 0, "failed", question);
        workspace otherWorkspace = entityManager.persist(new workspace("Someone else's", user));
        saveChunk(newDocument(otherWorkspace, Document.Status.READY), 0, "another workspace", question);
        entityManager.flush();
        entityManager.clear();

        List<RetrievedChunk> found = chunkRepository.findClosestInWorkspace(
                workspace.getId(), question, Limit.of(10));

        // The others match the question exactly, but must never be returned.
        assertThat(found).extracting(RetrievedChunk::content).containsExactly("ready, in this workspace");
    }

    private Document newDocument(workspace owner, Document.Status status) {
        Document newDocument = new Document();
        newDocument.setId(UUID.randomUUID());
        newDocument.setWorkspace(owner);
        newDocument.setUploadedBy(user);
        newDocument.setFilename("notes.txt");
        newDocument.setContentType("text/plain");
        newDocument.setSizeBytes(42);
        newDocument.setStoragePath("test/notes.txt");
        newDocument.setStatus(status);
        return entityManager.persist(newDocument);
    }

    private void saveChunk(int index, String content, float[] embedding) {
        saveChunk(document, index, content, embedding);
    }

    private void saveChunk(Document owner, int index, String content, float[] embedding) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(owner);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunkRepository.save(chunk);
    }

    // A 768-dimension vector whose first three values are given; the rest are 0.
    private static float[] vector(float a, float b, float c) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = a;
        vector[1] = b;
        vector[2] = c;
        return vector;
    }
}
