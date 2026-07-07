package ai.gamecopy.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthPageController {

  @GetMapping({"/dashbord", "/dashboard"})
  public String dashboard() {
    return "forward:/dashboard.html";
  }
}
