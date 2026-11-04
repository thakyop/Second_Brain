package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.RetrievedChunk;

import java.util.List;

/** The text sent to the chat model. */
final class PromptBuilder {

    static final String SYSTEM_PROMPT = """
            You are Second Brain, an assistant that answers questions about the user's own documents.
            Answer using only the numbered passages in the user's message.
            After each fact you use, cite the passage it came from, like [1] or [2].
            If the passages don't contain the answer, say that you couldn't find it in the documents. \
            Don't guess, and don't use outside knowledge.
            Keep answers short and clear.""";

    private PromptBuilder() {
    }

    /**
     * The passages, numbered from 1 so the model can cite them, followed by
     * the question. Parts are numbered from 1 for people; chunk indexes from 0.
     */
    static String userMessage(List<RetrievedChunk> passages, String question) {
        StringBuilder text = new StringBuilder("Passages from the user's documents:\n\n");
        for (int i = 0; i < passages.size(); i++) {
            RetrievedChunk passage = passages.get(i);
            text.append('[').append(i + 1).append("] ")
                    .append(passage.filename()).append(", part ").append(passage.chunkIndex() + 1).append('\n')
                    .append(passage.content().strip()).append("\n\n");
        }
        return text.append("Question: ").append(question.strip()).toString();
    }
}
