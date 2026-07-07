package ai.gamecopy.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerifyRequest(
    @Email @NotBlank String email,
    @Pattern(regexp = "\\d{6}") String code,
    String purpose
) {}
