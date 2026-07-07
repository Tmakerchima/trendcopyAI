package ai.gamecopy.auth;

import ai.gamecopy.config.EmailAuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class EmailAuthService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final EmailDeliveryService emailDeliveryService;
  private final EmailAuthProperties properties;
  private final PasswordService passwordService;

  public EmailAuthService(
      UserRepository userRepository,
      EmailDeliveryService emailDeliveryService,
      EmailAuthProperties properties,
      PasswordService passwordService
  ) {
    this.userRepository = userRepository;
    this.emailDeliveryService = emailDeliveryService;
    this.properties = properties;
    this.passwordService = passwordService;
  }

  public EmailStartResponse start(String email) {
    String normalizedEmail = normalize(email);
    if (userRepository.userExists(normalizedEmail)) {
      return new EmailStartResponse(
          true,
          "password",
          "请输入密码继续。",
          0
      );
    }

    EmailCodeResponse codeResponse = requestCode(normalizedEmail, "register");
    return new EmailStartResponse(
        false,
        "verify",
        codeResponse.message(),
        codeResponse.expiresInMinutes()
    );
  }

  public EmailCodeResponse requestCode(String email, String purpose) {
    String normalizedEmail = normalize(email);
    String normalizedPurpose = normalizePurpose(purpose);
    String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
    userRepository.saveEmailCode(
        normalizedEmail,
        hash(normalizedEmail, normalizedPurpose, code),
        normalizedPurpose,
        Instant.now().plusSeconds(properties.ttlMinutes() * 60L)
    );
    emailDeliveryService.sendCode(normalizedEmail, code);
    return new EmailCodeResponse(
        true,
        properties.resendEnabled() ? "验证码已发送到邮箱。" : "开发模式：验证码已打印在后端日志里。",
        properties.ttlMinutes()
    );
  }

  public CurrentUser verifyCode(String email, String code, String purpose) {
    String normalizedEmail = normalize(email);
    String normalizedPurpose = normalizePurpose(purpose);
    validateCode(normalizedEmail, code, normalizedPurpose);
    AppUser appUser = userRepository.upsertEmailUser(normalizedEmail, displayName(normalizedEmail));
    return CurrentUser.fromAppUser(appUser);
  }

  public void verifyRegistrationCode(String email, String code) {
    validateCode(normalize(email), code, "register");
  }

  public CurrentUser setPassword(String email, String password) {
    String normalizedEmail = normalize(email);
    AppUser user = userRepository.createVerifiedEmailUser(
        normalizedEmail,
        displayName(normalizedEmail),
        passwordService.hash(password)
    );
    return CurrentUser.fromAppUser(user);
  }

  public CurrentUser loginWithPassword(String email, String password) {
    String normalizedEmail = normalize(email);
    String passwordHash = userRepository.passwordHashByEmail(normalizedEmail)
        .orElseThrow(() -> new IllegalArgumentException("账号不存在或尚未设置密码。"));
    if (!passwordService.verify(password, passwordHash)) {
      throw new IllegalArgumentException("邮箱或密码不正确。");
    }
    return CurrentUser.fromAppUser(userRepository.touchLogin(normalizedEmail));
  }

  private void validateCode(String email, String code, String purpose) {
    EmailCode emailCode = userRepository.latestEmailCode(email, purpose)
        .orElseThrow(() -> new IllegalArgumentException("验证码不存在，请重新获取。"));

    if (emailCode.used()) {
      throw new IllegalArgumentException("验证码已使用，请重新获取。");
    }
    if (emailCode.expiresAt().isBefore(Instant.now())) {
      throw new IllegalArgumentException("验证码已过期，请重新获取。");
    }
    String expectedHash = hash(email, purpose, code);
    if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), emailCode.codeHash().getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("验证码不正确。");
    }

    userRepository.markEmailCodeUsed(email, purpose);
  }

  private String displayName(String email) {
    int at = email.indexOf('@');
    return at > 0 ? email.substring(0, at) : email;
  }

  private String hash(String email, String purpose, String code) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest((email + ":" + purpose + ":" + code).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception error) {
      throw new IllegalStateException("Unable to hash email code.", error);
    }
  }

  private String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private String normalizePurpose(String purpose) {
    String value = purpose == null ? "" : purpose.trim().toLowerCase();
    return value.equals("register") ? "register" : "login";
  }
}
