
package com.Second_Brain.Second_Brain.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320)
            String email,

            @NotBlank @Size(min = 8, max = 72)
            String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email
            String email,

            @NotBlank
            String password
    ) {}
}