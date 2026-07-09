package ai.gamecopy.usage;

import ai.gamecopy.auth.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UsageController {
  private final UsageService usageService;

  public UsageController(UsageService usageService) {
    this.usageService = usageService;
  }

  @GetMapping("/usage")
  public UsageStatus usage(HttpSession session) {
    return usageService.status((CurrentUser) session.getAttribute("currentUser"));
  }
}
