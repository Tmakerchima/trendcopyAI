package ai.gamecopy.trends;

import java.util.List;

public record TrendResearch(
    List<SourceReference> sources,
    String imageUrl,
    String context
) {
  public static TrendResearch empty() {
    return new TrendResearch(List.of(), "", "");
  }
}
