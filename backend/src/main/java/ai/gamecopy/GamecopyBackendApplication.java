package ai.gamecopy;

import ai.gamecopy.config.AlipayProperties;
import ai.gamecopy.config.OAuthProperties;
import ai.gamecopy.config.QwenProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    QwenProperties.class,
    AlipayProperties.class,
    OAuthProperties.class
})
public class GamecopyBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(GamecopyBackendApplication.class, args);
  }
}
