package com.Second_Brain.Second_Brain.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the chat model (Ollama, through Spring AI). Wrapped like
 * EmbeddingService, so the rest of the code and its tests don't depend on
 * ChatClient.
 */
@Service
public class LlmService {

    private final ChatClient chatClient;

    public LlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * The model's reply to `userMessage`, given the instructions and the
     * earlier messages of the conversation. Slow: a local model takes
     * seconds, so never call this inside a database transaction.
     */
    public String generate(String systemPrompt, List<Message> history, String userMessage) {
        // Passed as Message objects, not as ChatClient's .user(String) text, so
        // braces in the documents are never treated as template placeholders.
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);
        messages.add(new UserMessage(userMessage));

        String content = chatClient.prompt(new Prompt(messages)).call().content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("The chat model returned an empty answer");
        }
        return content.strip();
    }
}
