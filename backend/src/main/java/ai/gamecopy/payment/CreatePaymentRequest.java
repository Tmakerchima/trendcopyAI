package ai.gamecopy.payment;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(
    @NotBlank String planId
) {
}
