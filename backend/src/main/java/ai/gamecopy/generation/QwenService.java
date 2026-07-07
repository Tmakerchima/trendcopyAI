package ai.gamecopy.generation;

import ai.gamecopy.config.QwenProperties;
import ai.gamecopy.trends.TrendResearch;
import ai.gamecopy.trends.TrendResearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class QwenService {
  private final QwenProperties properties;
  private final TrendResearchService trendResearchService;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public QwenService(
      QwenProperties properties,
      TrendResearchService trendResearchService,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.trendResearchService = trendResearchService;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder()
        .baseUrl(properties.baseUrl())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  public GenerateResponse generate(GenerateRequest request) {
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw new IllegalStateException("生成服务暂时不可用");
    }
    TrendResearch research = trendResearchService.research(request);

    Map<String, Object> body = Map.of(
        "model", properties.model(),
        "messages", List.of(
            Map.of("role", "system", "content", "你是严谨的中文 AI 趋势内容策划助手。你必须输出合法 JSON。"),
            Map.of("role", "user", "content", buildPrompt(request, research))
        ),
        "temperature", 0.75,
        "max_tokens", 1500,
        "response_format", Map.of("type", "json_object")
    );

    JsonNode response = restClient.post()
        .uri("/chat/completions")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
        .body(body)
        .retrieve()
        .body(JsonNode.class);

    String content = response.path("choices").path(0).path("message").path("content").asText();
    return parseModelJson(content, research);
  }

  private String buildPrompt(GenerateRequest request, TrendResearch research) {
    return """
        你是一个 AI 趋势内容策划助手，负责把 GitHub Trending、Hacker News、Product Hunt、Reddit、X、官方博客或用户输入的热点，改写成可发布内容。

        必须遵守：
        - 只基于用户提供的信息生成，不要编造 star 数、融资额、用户量、官方结论或未经提供的数据。
        - 如果“已检索到的真实来源材料”不为空，必须优先基于这些材料生成，不能写成泛泛而谈。
        - 如果来源材料没有支持某个结论，就不要写这个结论。
        - 如果信息不足，用“可能”“适合关注”“值得观察”“可以从这个角度理解”等稳健表达。
        - 内容要讲清楚：这个趋势是什么、为什么值得关注、适合谁、普通用户/创作者/开发者能怎么用。
        - 不要写成广告软文，不要无脑吹捧。
        - 根据目标平台调整语气和结构：
          小红书：第一屏要像笔记，场景化、可收藏、标签自然；
          X / Twitter：短句、强观点、可做 thread，不写长段落；
          B站：标题要有明确看点，正文适合视频口播和分段；
          YouTube Shorts：前三秒直接给冲突、结论或反差，句子短；
          Newsletter：理性摘要，先结论，再价值、风险、链接脉络。
        - 只返回 JSON，不要返回 Markdown，不要解释。

        用户输入：
        趋势来源：%s
        发布平台：%s
        项目、新闻或趋势：%s
        受众/关键词：%s
        内容风格：%s
        补充信息：%s

        已检索到的真实来源材料：
        %s

        返回 JSON 格式：
        {
          "titles": ["内容选题1", "内容选题2", "内容选题3", "内容选题4", "内容选题5", "内容选题6", "内容选题7", "内容选题8", "内容选题9", "内容选题10"],
          "covers": ["封面/开场短句1", "封面/开场短句2", "封面/开场短句3", "封面/开场短句4", "封面/开场短句5"],
          "description": "120字以内发布简介",
          "tags": ["标签1", "标签2", "标签3", "标签4", "标签5", "标签6", "标签7", "标签8", "标签9", "标签10"],
          "script": ["开头", "为什么值得关注", "怎么使用或怎么理解", "适合谁", "结尾引导"]
        }
        """.formatted(
        safe(request.game()),
        safe(request.platform()),
        safe(request.topic()),
        safe(request.keywords()),
        safe(request.style()),
        safe(request.notes()),
        safe(research.context()).isBlank() ? "无。只能基于用户输入生成。" : research.context()
    );
  }

  private GenerateResponse parseModelJson(String content, TrendResearch research) {
    try {
      JsonNode root = objectMapper.readTree(extractJson(content));
      return new GenerateResponse(
          toList(root.path("titles"), 10),
          toList(root.path("covers"), 5),
          trim(root.path("description").asText(), 180),
          toList(root.path("tags"), 12),
          toList(root.path("script"), 6),
          research.sources(),
          research.imageUrl()
      );
    } catch (Exception error) {
      throw new IllegalStateException("生成结果解析失败");
    }
  }

  private List<String> toList(JsonNode node, int limit) {
    if (!node.isArray()) {
      return List.of();
    }
    List<String> values = new java.util.ArrayList<>();
    node.forEach(item -> values.add(item.asText("")));
    return values.stream()
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .limit(limit)
        .toList();
  }

  private String extractJson(String value) {
    String raw = safe(value);
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new IllegalArgumentException("No JSON object found");
    }
    return raw.substring(start, end + 1);
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private String trim(String value, int maxLength) {
    String safeValue = safe(value);
    return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
  }
}
