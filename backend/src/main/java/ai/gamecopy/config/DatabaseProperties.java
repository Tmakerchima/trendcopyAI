package ai.gamecopy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamecopy.database")
public record DatabaseProperties(
    String url,
    String user,
    String password
) {
  public boolean enabled() {
    return url != null && !url.isBlank();
  }
}
