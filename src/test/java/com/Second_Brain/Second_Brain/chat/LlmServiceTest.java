package com.Second_Brain.Second_Brain.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* The real Spring AI ChatClient in front of a mocked chat model: no Ollama needed. */
@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    private ChatModel chatModel;

    @Captor
    private ArgumentCaptor<Prompt> prompt;

    @BeforeEach
    void setUp() {
        // ChatClient starts from the model's options; a plain mock would return null.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void sendsTheInstructionsTheHistoryAndTheQuestionInOrder() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("  It uses time slices [1].  "));
        LlmService llm = new LlmService(ChatClient.builder(chatModel));

        String answer = llm.generate(
                "Be brief.",
                List.of(new UserMessage("Earlier question"), new AssistantMessage("Earlier answer")),
                "Passages... Question: what is round-robin?");

        assertThat(answer).isEqualTo("It uses time slices [1].");
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.SYSTEM, "Be brief."),
                        tuple(MessageType.USER, "Earlier question"),
                        tuple(MessageType.ASSISTANT, "Earlier answer"),
                        tuple(MessageType.USER, "Passages... Question: what is round-robin?"));
    }

    @Test
    void bracesInTheDocumentsReachTheModelUnchanged() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("An answer"));
        LlmService llm = new LlmService(ChatClient.builder(chatModel));
        String withBraces = "[1] config.md, part 1\nUse {name} and {{value}}.\n\nQuestion: what is {name}?";

        llm.generate("Be brief.", List.of(), withBraces);

        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().getLast().getText()).isEqualTo(withBraces);
    }

    @Test
    void anEmptyReplyIsAnError() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("   "));
        LlmService llm = new LlmService(ChatClient.builder(chatModel));

        assertThatThrownBy(() -> llm.generate("Be brief.", List.of(), "Question?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty answer");
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
