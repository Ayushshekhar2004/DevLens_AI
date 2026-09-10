package com.devlensai.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "name must not be blank")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Pattern(regexp = "(?s).*\\S.*\\S.*", message = "name must contain at least 2 non-space characters")
        String name,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be valid")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password
) {

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + ", password=[REDACTED]]";
    }
}
