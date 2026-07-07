package ai.gamecopy.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailStartRequest(
    @Email @NotBlank String email
) {}
