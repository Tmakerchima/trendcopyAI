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
  private final EmailAuthService emailAuthService;

  public EmailAuthController(EmailAuthService emailAuthService) {
    this.emailAuthService = emailAuthService;
  }

  @PostMapping("/request-code")
  public EmailCodeResponse requestCode(@Valid @RequestBody EmailCodeRequest request) {
    return emailAuthService.requestCode(request.email(), request.purpose());
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
}
