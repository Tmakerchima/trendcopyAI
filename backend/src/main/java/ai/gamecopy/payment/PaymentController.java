package ai.gamecopy.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final AlipayService alipayService;

  public PaymentController(AlipayService alipayService) {
    this.alipayService = alipayService;
  }

  @PostMapping("/alipay")
  public CreatePaymentResponse createAlipayPayment(@Valid @RequestBody CreatePaymentRequest request) {
    return alipayService.createPagePayment(request.planId());
  }

  @GetMapping("/alipay/status")
  public Map<String, Object> alipayStatus() {
    return Map.of(
        "configured", alipayService.configured(),
        "missing", alipayService.missingConfigKeys()
    );
  }

  @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
  public String alipayNotify(HttpServletRequest request) {
    return alipayService.verifyNotify(request) ? "success" : "failure";
  }
}
