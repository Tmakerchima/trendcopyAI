package ai.gamecopy.auth;

import ai.gamecopy.config.OAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class OAuthController {
  private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OAuthProperties properties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public OAuthController(OAuthProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(8));
    requestFactory.setReadTimeout(Duration.ofSeconds(12));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @GetMapping("/providers")
  public List<AuthProviderResponse> providers() {
    return List.of(
        response("google", "Google"),
        response("weixin", "WeChat"),
        response("qq", "QQ")
    );
  }

  @GetMapping("/me")
  public CurrentUser me(HttpSession session) {
    CurrentUser user = (CurrentUser) session.getAttribute("currentUser");
    return user == null ? CurrentUser.anonymous() : user;
  }

  @PostMapping("/logout")
  public CurrentUser logout(HttpSession session) {
    session.invalidate();
    return CurrentUser.anonymous();
  }

  @GetMapping("/{provider}")
  public ResponseEntity<Void> login(@PathVariable String provider, HttpSession session) {
    OAuthProperties.Provider config = providerConfig(provider);
    if (config == null || !config.enabled()) {
      throw new IllegalStateException("Login provider is not configured.");
    }

    String state = randomState();
    session.setAttribute("oauthState:" + provider, state);
    URI uri = URI.create(buildAuthorizationUrl(provider, config, state));
    return redirect(uri.toString());
  }

  @GetMapping("/{provider}/callback")
  public ResponseEntity<Void> callback(
      @PathVariable String provider,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(name = "error_description", required = false) String errorDescription,
      HttpSession session
  ) {
    if (error != null && !error.isBlank()) {
      return redirect("/dashbord?login=failed&reason=" + encode(firstNonBlank(errorDescription, error)));
    }

    if (code == null || code.isBlank()) {
      return redirect("/dashbord?login=failed&reason=" + encode("Missing authorization code."));
    }

    Object expectedState = session.getAttribute("oauthState:" + provider);
    if (expectedState != null && !expectedState.equals(state)) {
      return redirect("/dashbord?login=failed&reason=" + encode("OAuth state mismatch. Please try again."));
    }

    try {
      CurrentUser user = switch (provider) {
        case "google" -> fetchGoogleUser(code);
        case "qq" -> fetchQqUser(code);
        case "weixin" -> fetchWeixinUser(code);
        default -> CurrentUser.authenticated(provider, providerDisplayName(provider), "", "");
      };
      session.setAttribute("currentUser", user);
      session.removeAttribute("oauthState:" + provider);
      return redirect("/?login=success&provider=" + encode(provider));
    } catch (Exception loginError) {
      String reason = sanitizeError(loginError);
      log.warn("OAuth login failed for provider {}: {}", provider, reason, loginError);
      session.setAttribute("loginError", reason);
      return redirect("/dashbord?login=failed&reason=" + encode(reason));
    }
  }

  private CurrentUser fetchGoogleUser(String code) {
    OAuthProperties.Provider config = providerConfig("google");
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", config.clientId());
    form.add("client_secret", config.clientSecret());
    form.add("redirect_uri", config.redirectUri());
    form.add("grant_type", "authorization_code");

    JsonNode token = restClient.post()
        .uri(config.tokenUri())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(JsonNode.class);
    ensureTokenResponse(token, "Google");

    String accessToken = token.path("access_token").asText();
    JsonNode profile = restClient.get()
        .uri("https://openidconnect.googleapis.com/v1/userinfo")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .body(JsonNode.class);
    ensureNoProviderError(profile, "Google");

    String name = firstNonBlank(
        profile.path("name").asText(),
        profile.path("email").asText(),
        "Google User"
    );
    String email = profile.path("email").asText("");
    String avatar = profile.path("picture").asText("");
    return CurrentUser.authenticated("google", name, email, avatar);
  }

  private CurrentUser fetchQqUser(String code) {
    OAuthProperties.Provider config = providerConfig("qq");
    String tokenText = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https")
            .host("graph.qq.com")
            .path("/oauth2.0/token")
            .queryParam("grant_type", "authorization_code")
            .queryParam("client_id", config.clientId())
            .queryParam("client_secret", config.clientSecret())
            .queryParam("code", code)
            .queryParam("redirect_uri", config.redirectUri())
            .build())
        .retrieve()
        .body(String.class);

    String accessToken = parseQueryValue(tokenText, "access_token");
    if (accessToken.isBlank()) {
      throw new IllegalStateException("QQ token exchange failed: " + sanitizeProviderText(tokenText));
    }

    String openidText = restClient.get()
        .uri("https://graph.qq.com/oauth2.0/me?access_token={accessToken}", accessToken)
        .retrieve()
        .body(String.class);
    String openid = extractJson(openidText).path("openid").asText();
    if (openid.isBlank()) {
      throw new IllegalStateException("QQ openid fetch failed: " + sanitizeProviderText(openidText));
    }

    JsonNode profile = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https")
            .host("graph.qq.com")
            .path("/user/get_user_info")
            .queryParam("access_token", accessToken)
            .queryParam("oauth_consumer_key", config.clientId())
            .queryParam("openid", openid)
            .build())
        .retrieve()
        .body(JsonNode.class);
    ensureNoProviderError(profile, "QQ");

    String name = firstNonBlank(profile.path("nickname").asText(), "QQ User");
    String avatar = firstNonBlank(profile.path("figureurl_qq_2").asText(), profile.path("figureurl_qq_1").asText(), "");
    return CurrentUser.authenticated("qq", name, "", avatar);
  }

  private CurrentUser fetchWeixinUser(String code) {
    OAuthProperties.Provider config = providerConfig("weixin");
    JsonNode token = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https")
            .host("api.weixin.qq.com")
            .path("/sns/oauth2/access_token")
            .queryParam("appid", config.clientId())
            .queryParam("secret", config.clientSecret())
            .queryParam("code", code)
            .queryParam("grant_type", "authorization_code")
            .build())
        .retrieve()
        .body(JsonNode.class);
    ensureTokenResponse(token, "WeChat");

    String accessToken = token.path("access_token").asText();
    String openid = token.path("openid").asText();
    JsonNode profile = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https")
            .host("api.weixin.qq.com")
            .path("/sns/userinfo")
            .queryParam("access_token", accessToken)
            .queryParam("openid", openid)
            .queryParam("lang", "zh_CN")
            .build())
        .retrieve()
        .body(JsonNode.class);
    ensureNoProviderError(profile, "WeChat");

    String name = firstNonBlank(profile.path("nickname").asText(), "WeChat User");
    String avatar = profile.path("headimgurl").asText("");
    return CurrentUser.authenticated("weixin", name, "", avatar);
  }

  private String providerDisplayName(String provider) {
    return switch (provider) {
      case "qq" -> "QQ User";
      case "weixin" -> "WeChat User";
      default -> provider;
    };
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private String parseQueryValue(String text, String key) {
    String[] pairs = (text == null ? "" : text).split("&");
    for (String pair : pairs) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && key.equals(parts[0])) {
        return parts[1];
      }
    }
    return "";
  }

  private JsonNode extractJson(String text) {
    String raw = text == null ? "" : text;
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new IllegalArgumentException("OAuth provider did not return JSON: " + sanitizeProviderText(raw));
    }
    try {
      return objectMapper.readTree(raw.substring(start, end + 1));
    } catch (Exception error) {
      throw new IllegalArgumentException("OAuth provider JSON parse failed", error);
    }
  }

  private AuthProviderResponse response(String id, String name) {
    OAuthProperties.Provider config = providerConfig(id);
    return new AuthProviderResponse(id, name, config != null && config.enabled(), "/api/auth/" + id);
  }

  private OAuthProperties.Provider providerConfig(String provider) {
    return properties.asMap().get(provider);
  }

  private String buildAuthorizationUrl(String provider, OAuthProperties.Provider config, String state) {
    String scope = String.join(" ", config.scopes());

    if ("weixin".equals(provider)) {
      return new StringBuilder(config.authorizationUri())
          .append("?response_type=code")
          .append("&appid=").append(encode(config.clientId()))
          .append("&redirect_uri=").append(encode(config.redirectUri()))
          .append("&scope=").append(encode(scope))
          .append("&state=").append(encode(state))
          .toString();
    }

    return new StringBuilder(config.authorizationUri())
        .append("?response_type=code")
        .append("&client_id=").append(encode(config.clientId()))
        .append("&redirect_uri=").append(encode(config.redirectUri()))
        .append("&scope=").append(encode(scope))
        .append("&state=").append(encode(state))
        .toString();
  }

  private ResponseEntity<Void> redirect(String location) {
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
  }

  private String randomState() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private void ensureTokenResponse(JsonNode node, String provider) {
    ensureNoProviderError(node, provider);
    if (node == null || node.path("access_token").asText("").isBlank()) {
      throw new IllegalStateException(provider + " token exchange failed: missing access_token.");
    }
  }

  private void ensureNoProviderError(JsonNode node, String provider) {
    if (node == null) {
      throw new IllegalStateException(provider + " returned an empty response.");
    }

    String error = firstNonBlank(node.path("error").asText(), node.path("errcode").asText());
    String description = firstNonBlank(
        node.path("error_description").asText(),
        node.path("errmsg").asText()
    );
    if (!error.isBlank() && !"0".equals(error)) {
      throw new IllegalStateException(provider + " error " + error + ": " + description);
    }
  }

  private String sanitizeProviderText(String text) {
    if (text == null || text.isBlank()) {
      return "empty response";
    }
    return text
        .replaceAll("(?i)client_secret=[^&\\s]+", "client_secret=***")
        .replaceAll("(?i)access_token=[^&\\s]+", "access_token=***")
        .replaceAll("[\\r\\n]+", " ")
        .trim();
  }

  private String sanitizeError(Exception error) {
    String message;
    if (error instanceof RestClientResponseException responseException) {
      message = responseException.getResponseBodyAsString();
    } else {
      message = error.getMessage();
    }

    if (message == null || message.isBlank()) {
      message = error.getClass().getSimpleName();
    }

    try {
      JsonNode node = objectMapper.readTree(message);
      message = firstNonBlank(
          node.path("error_description").asText(),
          node.path("errmsg").asText(),
          node.path("error").asText(),
          message
      );
    } catch (Exception ignored) {
      // Some OAuth providers return query-string text instead of JSON.
    }

    return message
        .replaceAll("(?i)client_secret=[^&\\s]+", "client_secret=***")
        .replaceAll("(?i)access_token[^,}\\s]*", "access_token***")
        .replaceAll("[\\r\\n]+", " ")
        .trim();
  }
}
