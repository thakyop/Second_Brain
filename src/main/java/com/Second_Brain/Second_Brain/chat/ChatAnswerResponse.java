package com.Second_Brain.Second_Brain.chat;

import java.util.UUID;

/** The reply to a question: the chat's id, the saved question and the answer with its sources. */
public record ChatAnswerResponse(
        UUID sessionId,
        ChatMessageResponse question,
        ChatMessageResponse answer
) {

    public static ChatAnswerResponse from(ChatService.Exchange exchange) {
        return new ChatAnswerResponse(
                exchange.sessionId(),
                ChatMessageResponse.from(exchange.question()),
                ChatMessageResponse.from(exchange.answer()));
    }
}
