package ai.gamecopy.payment;

import ai.gamecopy.config.AlipayProperties;
import ai.gamecopy.pricing.PricingPlan;
import ai.gamecopy.pricing.PricingService;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlipayService {
  private final AlipayProperties properties;
  private final PricingService pricingService;

  public AlipayService(AlipayProperties properties, PricingService pricingService) {
    this.properties = properties;
    this.pricingService = pricingService;
  }

  public CreatePaymentResponse createPagePayment(String planId) {
    PricingPlan plan = pricingService.findPlan(planId)
        .orElseThrow(() -> new IllegalArgumentException("Pricing plan does not exist."));

    if ("free".equals(plan.id())) {
      throw new IllegalArgumentException("The free plan does not require payment.");
    }

    ensureConfigured();

    String orderNo = "GC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    AlipayTradePagePayModel model = new AlipayTradePagePayModel();
    model.setOutTradeNo(orderNo);
    model.setSubject("GameCopy AI - " + plan.name());
    model.setTotalAmount(formatYuan(plan.priceFen()));
    model.setProductCode("FAST_INSTANT_TRADE_PAY");
    model.setBody(plan.description());

    AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
    request.setBizModel(model);
    request.setReturnUrl(properties.returnUrl());
    if (properties.notifyUrl() != null && !properties.notifyUrl().isBlank()) {
      request.setNotifyUrl(properties.notifyUrl());
    }

    try {
      String form = client().pageExecute(request).getBody();
      return new CreatePaymentResponse("alipay", orderNo, form);
    } catch (AlipayApiException error) {
      throw new IllegalStateException("Alipay service is unavailable.", error);
    }
  }

  public boolean configured() {
    return missingConfigKeys().isEmpty();
  }

  public List<String> missingConfigKeys() {
    List<String> missing = new ArrayList<>();
    if (blank(properties.appId())) {
      missing.add("ALIPAY_APP_ID");
    }
    if (blank(properties.merchantPrivateKey())) {
      missing.add("ALIPAY_MERCHANT_PRIVATE_KEY");
    }
    if (blank(properties.alipayPublicKey())) {
      missing.add("ALIPAY_PUBLIC_KEY");
    }
    return missing;
  }

  public boolean verifyNotify(HttpServletRequest request) {
    if (properties.alipayPublicKey() == null || properties.alipayPublicKey().isBlank()) {
      return false;
    }

    Map<String, String> params = new HashMap<>();
    Map<String, String[]> requestParams = request.getParameterMap();
    for (Iterator<String> iterator = requestParams.keySet().iterator(); iterator.hasNext(); ) {
      String name = iterator.next();
      String[] values = requestParams.get(name);
      params.put(name, String.join(",", values));
    }

    try {
      return AlipaySignature.rsaCheckV1(
          params,
          properties.alipayPublicKey(),
          properties.charset(),
          properties.signType()
      );
    } catch (AlipayApiException error) {
      return false;
    }
  }

  private AlipayClient client() {
    return new DefaultAlipayClient(
        properties.gatewayUrl(),
        properties.appId(),
        properties.merchantPrivateKey(),
        "json",
        properties.charset(),
        properties.alipayPublicKey(),
        properties.signType()
    );
  }

  private void ensureConfigured() {
    if (!configured()) {
      throw new IllegalStateException("Alipay service is unavailable.");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String formatYuan(int priceFen) {
    return BigDecimal.valueOf(priceFen)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        .toPlainString();
  }
}
