package com.Second_Brain.Second_Brain.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class ChatRequests {

    private ChatRequests() {}

    /** A question. Leave out sessionId to start a new chat. */
    public record AskRequest(
            UUID sessionId,

            @NotBlank @Size(max = 4000)
            String message
    ) {}
}
