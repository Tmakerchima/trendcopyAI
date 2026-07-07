package ai.gamecopy.auth;

import ai.gamecopy.config.EmailAuthProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmailDeliveryService {
  private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

  private final EmailAuthProperties properties;
  private final RestClient restClient = RestClient.create();

  public EmailDeliveryService(EmailAuthProperties properties) {
    this.properties = properties;
  }

  public void sendCode(String email, String code) {
    if (!properties.resendEnabled()) {
      log.info("Email login code for {} is {}. Configure RESEND_API_KEY and EMAIL_FROM to send real email.", email, code);
      return;
    }

    restClient.post()
        .uri("https://api.resend.com/emails")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "from", properties.from(),
            "to", email,
            "subject", "Your TrendCopy AI login code",
            "html", """
                <div style="font-family: Inter, Arial, sans-serif; line-height: 1.6;">
                  <h2>TrendCopy AI verification code</h2>
                  <p>Use this code to continue:</p>
                  <p style="font-size: 28px; font-weight: 700; letter-spacing: 4px;">%s</p>
                  <p>This code expires in %d minutes.</p>
                </div>
                """.formatted(code, properties.ttlMinutes())
        ))
        .retrieve()
        .toBodilessEntity();
  }
}
