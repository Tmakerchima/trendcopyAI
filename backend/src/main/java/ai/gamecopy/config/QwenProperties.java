package ai.gamecopy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamecopy.qwen")
public record QwenProperties(
    String apiKey,
    String baseUrl,
    String model
) {
}
