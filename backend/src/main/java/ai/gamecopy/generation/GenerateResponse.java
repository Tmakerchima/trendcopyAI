package ai.gamecopy.generation;

import ai.gamecopy.trends.SourceReference;
import java.util.List;

public record GenerateResponse(
    List<String> titles,
    List<String> covers,
    String description,
    List<String> tags,
    List<String> script,
    List<SourceReference> sources,
    String imageUrl
) {
}
