package ai.gamecopy.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
public class EmailAuthController {
  private static final String PENDING_REGISTRATION_EMAIL = "pendingRegistrationEmail";

  private final EmailAuthService emailAuthService;

  public EmailAuthController(EmailAuthService emailAuthService) {
    this.emailAuthService = emailAuthService;
  }

  @PostMapping("/request-code")
  public EmailCodeResponse requestCode(@Valid @RequestBody EmailCodeRequest request) {
    return emailAuthService.requestCode(request.email(), request.purpose());
  }

  @PostMapping("/start")
  public EmailStartResponse start(@Valid @RequestBody EmailStartRequest request) {
    return emailAuthService.start(request.email());
  }

  @PostMapping("/verify-registration")
  public EmailCodeResponse verifyRegistration(
      @Valid @RequestBody EmailVerifyRequest request,
      HttpSession session
  ) {
    emailAuthService.verifyRegistrationCode(request.email(), request.code());
    session.setAttribute(PENDING_REGISTRATION_EMAIL, normalize(request.email()));
    return new EmailCodeResponse(true, "邮箱已验证，请设置密码。", 0);
  }

  @PostMapping("/set-password")
  public CurrentUser setPassword(
      @Valid @RequestBody SetPasswordRequest request,
      HttpSession session
  ) {
    String email = normalize(request.email());
    Object pendingEmail = session.getAttribute(PENDING_REGISTRATION_EMAIL);
    if (!email.equals(pendingEmail)) {
      throw new IllegalArgumentException("请先完成邮箱验证码验证。");
    }
    CurrentUser user = emailAuthService.setPassword(email, request.password());
    session.setAttribute("currentUser", user);
    session.removeAttribute(PENDING_REGISTRATION_EMAIL);
    return user;
  }

  @PostMapping("/password-login")
  public CurrentUser passwordLogin(
      @Valid @RequestBody PasswordLoginRequest request,
      HttpSession session
  ) {
    CurrentUser user = emailAuthService.loginWithPassword(request.email(), request.password());
    session.setAttribute("currentUser", user);
    return user;
  }

  @PostMapping("/verify")
  public CurrentUser verify(
      @Valid @RequestBody EmailVerifyRequest request,
      HttpSession session
  ) {
    CurrentUser user = emailAuthService.verifyCode(request.email(), request.code(), request.purpose());
    session.setAttribute("currentUser", user);
    return user;
  }

  private String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}
