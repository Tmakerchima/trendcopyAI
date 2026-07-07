package ai.gamecopy.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateRequest(
    @NotBlank @Size(max = 30) String game,
    @NotBlank @Size(max = 20) String platform,
    @NotBlank @Size(max = 120) String topic,
    @Size(max = 100) String keywords,
    @Size(max = 30) String style,
    @Size(max = 260) String notes
) {
}
