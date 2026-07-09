package ai.gamecopy.auth;

import ai.gamecopy.config.EmailAuthProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

    try {
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
    } catch (RestClientResponseException error) {
      log.warn("Resend rejected email to {} with status {}: {}", email, error.getStatusCode(), error.getResponseBodyAsString());
      if (error.getResponseBodyAsString().contains("You can only send testing emails")) {
        throw new IllegalStateException("当前邮件服务只允许发送到已验证邮箱。请先在 Resend 绑定发信域名，或使用已验证邮箱测试。");
      }
      throw new IllegalStateException("邮件验证码发送失败，请稍后再试。");
    } catch (RestClientException error) {
      log.warn("Failed to send email code to {} via Resend: {}", email, error.getMessage());
      throw new IllegalStateException("邮件验证码发送失败，请稍后再试。");
    }
  }
}
