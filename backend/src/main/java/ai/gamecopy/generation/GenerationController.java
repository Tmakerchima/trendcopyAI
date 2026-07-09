package ai.gamecopy.generation;

import ai.gamecopy.auth.CurrentUser;
import ai.gamecopy.usage.UsageService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GenerationController {
  private final QwenService qwenService;
  private final UsageService usageService;

  public GenerationController(QwenService qwenService, UsageService usageService) {
    this.qwenService = qwenService;
    this.usageService = usageService;
  }

  @PostMapping("/generate")
  public GenerateResponse generate(@Valid @RequestBody GenerateRequest request, HttpSession session) {
    CurrentUser user = (CurrentUser) session.getAttribute("currentUser");
    usageService.ensureCanGenerate(user);
    GenerateResponse response = qwenService.generate(request);
    return qwenService.withUsage(response, usageService.recordGenerate(user));
  }
}
