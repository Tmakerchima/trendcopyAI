package ai.gamecopy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamecopy.email-auth")
public record EmailAuthProperties(
    String resendApiKey,
    String from,
    int codeMinutes,
    String appUrl
) {
  public int ttlMinutes() {
    return codeMinutes <= 0 ? 10 : codeMinutes;
  }

  public boolean resendEnabled() {
    return resendApiKey != null && !resendApiKey.isBlank()
        && from != null && !from.isBlank();
  }
}
