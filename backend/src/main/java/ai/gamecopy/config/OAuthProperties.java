package ai.gamecopy.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamecopy.oauth")
public record OAuthProperties(
    Provider google,
    Provider weixin,
    Provider qq
) {
  public Map<String, Provider> asMap() {
    return Map.of(
        "google", google,
        "weixin", weixin,
        "qq", qq
    );
  }

  public record Provider(
      String clientId,
      String clientSecret,
      String authorizationUri,
      String tokenUri,
      List<String> scopes,
      String redirectUri
  ) {
    public boolean enabled() {
      return clientId != null && !clientId.isBlank()
          && clientSecret != null && !clientSecret.isBlank();
    }
  }
}
