package ai.gamecopy.generation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GenerationController {
  private final QwenService qwenService;

  public GenerationController(QwenService qwenService) {
    this.qwenService = qwenService;
  }

  @PostMapping("/generate")
  public GenerateResponse generate(@Valid @RequestBody GenerateRequest request) {
    return qwenService.generate(request);
  }
}
