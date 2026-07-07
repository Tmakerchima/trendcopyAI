package ai.gamecopy.payment;

public record CreatePaymentResponse(
    String provider,
    String orderNo,
    String paymentForm
) {
}
