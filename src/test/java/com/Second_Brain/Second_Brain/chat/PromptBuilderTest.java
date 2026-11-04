package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @Test
    void numbersEachPassageWithItsFileAndPartThenAsksTheQuestion() {
        List<RetrievedChunk> passages = List.of(
                passage("notes.md", 0, "  Round-robin gives each process a time slice.\n"),
                passage("os.pdf", 6, "Shortest job first picks the shortest burst."));

        String message = PromptBuilder.userMessage(passages, "  What is round-robin?  ");

        assertThat(message).isEqualTo("""
                Passages from the user's documents:

                [1] notes.md, part 1
                Round-robin gives each process a time slice.

                [2] os.pdf, part 7
                Shortest job first picks the shortest burst.

                Question: What is round-robin?""");
    }

    @Test
    void keepsBracesAndOtherSpecialCharactersAsTheyAre() {
        String content = "Use {name} and {{value}} in templates; $HOME and <tags> too.";

        String message = PromptBuilder.userMessage(List.of(passage("config.md", 0, content)), "What is {name}?");

        assertThat(message).contains(content).endsWith("Question: What is {name}?");
    }

    @Test
    void theInstructionsAskForCitationsAndNoGuessing() {
        assertThat(PromptBuilder.SYSTEM_PROMPT)
                .contains("only the numbered passages")
                .contains("like [1] or [2]")
                .contains("couldn't find it in the documents");
    }

    private static RetrievedChunk passage(String filename, int chunkIndex, String content) {
        return new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), filename, chunkIndex, content, 0.3);
    }
}
