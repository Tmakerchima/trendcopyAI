package ai.gamecopy.trends;

import ai.gamecopy.generation.GenerateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TrendResearchService {
  private static final String JINA_PREFIX = "https://r.jina.ai/http://r.jina.ai/http://";
  private static final Pattern HLTV_RESULT_PATTERN =
      Pattern.compile("\\[([^\\]]+)]\\((https://www\\.hltv\\.org/(?:news|forums)/[^)]+)\\)(\\d{4}-\\d{2}-\\d{2})?");
  private static final Pattern HLTV_URL_PATTERN =
      Pattern.compile("(https://www\\.hltv\\.org/(?:news|forums)/[^)\\s]+)");
  private static final Pattern DUCK_RESULT_PATTERN =
      Pattern.compile("^## \\[([^\\]]+)]\\((https?://[^)]+)\\)", Pattern.MULTILINE);
  private static final Pattern TITLE_PATTERN =
      Pattern.compile("(?m)^Title:\\s*(.+)$");
  private static final Pattern URL_SOURCE_PATTERN =
      Pattern.compile("(?m)^URL Source:\\s*(https?://\\S+)");
  private static final Pattern PUBLISHED_PATTERN =
      Pattern.compile("(?m)^Published Time:\\s*(.+)$");
  private static final Pattern IMAGE_PATTERN =
      Pattern.compile("!\\[[^\\]]*]\\((https?://[^)]+)\\)");
  private static final Pattern META_IMAGE_PATTERN =
      Pattern.compile(
          "<meta\\s+[^>]*(?:property|name)=[\"'](?:og:image|twitter:image|twitter:image:src)[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern META_IMAGE_REVERSED_PATTERN =
      Pattern.compile(
          "<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image|twitter:image:src)[\"'][^>]*>",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern URL_PATTERN =
      Pattern.compile("https?://[^\\s\"'<>，。)）]+", Pattern.CASE_INSENSITIVE);

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(8))
          .build();
  private final ObjectMapper objectMapper;
  private final String firecrawlApiKey;
  private final String firecrawlBaseUrl;

  public TrendResearchService(
      ObjectMapper objectMapper,
      @Value("${firecrawl.api-key:}") String firecrawlApiKey,
      @Value("${firecrawl.base-url:https://api.firecrawl.dev/v2}") String firecrawlBaseUrl) {
    this.objectMapper = objectMapper;
    this.firecrawlApiKey = firecrawlApiKey == null ? "" : firecrawlApiKey.trim();
    this.firecrawlBaseUrl =
        firecrawlBaseUrl == null ? "https://api.firecrawl.dev/v2" : firecrawlBaseUrl.trim();
  }

  public TrendResearch research(GenerateRequest request) {
    if (looksLikeHltv(request)) {
      return researchHltv(request);
    }
    return researchWeb(request);
  }

  public Optional<String> resolveImage(String source, String topic, String keywords, String notes) {
    GenerateRequest request =
        new GenerateRequest(
            blankTo(source, "手动输入"),
            "小红书",
            blankTo(topic, "趋势"),
            keywords,
            "趋势观察",
            notes);
    return Optional.ofNullable(research(request).imageUrl()).filter(value -> !value.isBlank());
  }

  private TrendResearch directSource(GenerateRequest request) {
    List<SourceReference> sources = urlsFrom(request).stream()
        .map(url -> new SourceReference(hostLabel(url), hostTitle(url), url, "", ""))
        .toList();
    return sources.isEmpty() ? TrendResearch.empty() : new TrendResearch(sources, "", "");
  }

  private TrendResearch researchWeb(GenerateRequest request) {
    List<String> directUrls = urlsFrom(request);
    if (!directUrls.isEmpty()) {
      return researchUrls(directUrls);
    }

    try {
      List<Candidate> candidates = searchWeb(request);
      if (candidates.isEmpty()) {
        return TrendResearch.empty();
      }
      return researchCandidates(candidates.stream().limit(5).toList(), "Web");
    } catch (Exception ex) {
      return TrendResearch.empty();
    }
  }

  private TrendResearch researchUrls(List<String> urls) {
    List<Candidate> candidates = urls.stream()
        .limit(5)
        .map(url -> new Candidate(hostTitle(url), url, "", 10, ""))
        .toList();
    return researchCandidates(candidates, "Web");
  }

  private TrendResearch researchHltv(GenerateRequest request) {
    try {
      List<Candidate> candidates = searchHltv(request);
      if (candidates.isEmpty()) {
        return TrendResearch.empty();
      }

      return researchCandidates(candidates.stream().limit(5).toList(), "HLTV");
    } catch (Exception ex) {
      return TrendResearch.empty();
    }
  }

  private TrendResearch researchCandidates(List<Candidate> candidates, String fallbackSourceName) {
    List<SourceReference> sources = new ArrayList<>();
    String imageUrl = "";
    StringBuilder context = new StringBuilder();
    for (Candidate candidate : candidates) {
      Article article = fetchArticle(candidate);
      String label = sourceName(article.url(), fallbackSourceName) + " / " + article.title();
      SourceReference source =
          new SourceReference(label, article.title(), article.url(), article.excerpt(), article.imageUrl());
      sources.add(source);
      if (imageUrl.isBlank() && !article.imageUrl().isBlank()) {
        imageUrl = article.imageUrl();
      }
      context
          .append("来源：")
          .append(source.label())
          .append("\n链接：")
          .append(source.url())
          .append("\n发布时间：")
          .append(article.published())
          .append("\n摘要：")
          .append(source.excerpt())
          .append("\n\n");
    }
    return new TrendResearch(sources, imageUrl, context.toString().trim());
  }

  private List<Candidate> searchWeb(GenerateRequest request) throws Exception {
    if (!firecrawlApiKey.isBlank()) {
      List<Candidate> firecrawlResults = searchWithFirecrawl(request);
      if (!firecrawlResults.isEmpty()) {
        return firecrawlResults;
      }
    }

    String query = webQuery(request);
    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    String markdown = fetchText(JINA_PREFIX + "https://html.duckduckgo.com/html/?q=" + encoded);

    Map<String, Candidate> deduped = new LinkedHashMap<>();
    Matcher matcher = DUCK_RESULT_PATTERN.matcher(markdown);
    while (matcher.find()) {
      String title = cleanTitle(matcher.group(1));
      String url = unwrapDuckUrl(matcher.group(2));
      if (title.isBlank() || !isUsefulWebUrl(url)) {
        continue;
      }
      deduped.putIfAbsent(url, new Candidate(title, url, "", scoreWeb(title, url, request), ""));
      if (deduped.size() >= 10) {
        break;
      }
    }
    return deduped.values().stream()
        .sorted(Comparator.comparingInt(Candidate::score).reversed())
        .limit(5)
        .toList();
  }

  private List<Candidate> searchWithFirecrawl(GenerateRequest request) {
    try {
      String endpoint = firecrawlBaseUrl.replaceAll("/+$", "") + "/search";
      Map<String, Object> body = Map.of(
          "query", webQuery(request),
          "limit", 5,
          "tbs", "qdr:w",
          "sources", List.of("web", "news", "images"),
          "scrapeOptions", Map.of("formats", List.of(Map.of("type", "markdown")))
      );
      HttpRequest firecrawlRequest =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(Duration.ofSeconds(25))
              .header("Authorization", "Bearer " + firecrawlApiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(
                  objectMapper.writeValueAsString(body),
                  StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(firecrawlRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return List.of();
      }

      JsonNode root = objectMapper.readTree(response.body()).path("data");
      List<Candidate> candidates = new ArrayList<>();
      addFirecrawlCandidates(candidates, root.path("web"), request);
      addFirecrawlCandidates(candidates, root.path("news"), request);
      Map<String, String> imagesByHost = firecrawlImagesByHost(root.path("images"));

      return candidates.stream()
          .filter(candidate -> isUsefulWebUrl(candidate.url()))
          .map(candidate -> {
            if (!candidate.imageUrl().isBlank()) return candidate;
            String image = imagesByHost.getOrDefault(sourceName(candidate.url(), ""), "");
            return new Candidate(candidate.title(), candidate.url(), candidate.date(), candidate.score(), image);
          })
          .collect(java.util.stream.Collectors.toMap(
              Candidate::url,
              candidate -> candidate,
              (first, second) -> first,
              LinkedHashMap::new))
          .values()
          .stream()
          .sorted(Comparator.comparingInt(Candidate::score).reversed())
          .limit(5)
          .toList();
    } catch (Exception ex) {
      return List.of();
    }
  }

  private void addFirecrawlCandidates(
      List<Candidate> candidates,
      JsonNode nodes,
      GenerateRequest request) {
    if (!nodes.isArray()) return;
    nodes.forEach(node -> {
      String url = node.path("url").asText("");
      String title = node.path("title").asText("");
      String date = node.path("publishedDate").asText(node.path("date").asText(""));
      String image = node.path("image").asText(node.path("ogImage").asText(""));
      if (title.isBlank() || url.isBlank()) return;
      candidates.add(new Candidate(title, url, date, scoreWeb(title, url, request), image));
    });
  }

  private Map<String, String> firecrawlImagesByHost(JsonNode nodes) {
    Map<String, String> images = new LinkedHashMap<>();
    if (!nodes.isArray()) return images;
    nodes.forEach(node -> {
      String image = node.path("imageUrl").asText(node.path("url").asText(""));
      String source = sourceName(node.path("sourceUrl").asText(node.path("url").asText("")), "");
      if (!source.isBlank() && isUsefulImage(image)) {
        images.putIfAbsent(source, image);
      }
    });
    return images;
  }

  private List<Candidate> searchHltv(GenerateRequest request) throws Exception {
    String query = hltvQuery(request);
    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    String markdown = fetchText(JINA_PREFIX + "https://www.hltv.org/search?query=" + encoded);

    Map<String, Candidate> deduped = parseHltvCandidates(markdown, request);

    return deduped.values().stream()
        .sorted(Comparator.comparingInt(Candidate::score).reversed())
        .limit(5)
        .toList();
  }

  private Map<String, Candidate> parseHltvCandidates(String markdown, GenerateRequest request) {
    Map<String, Candidate> deduped = new LinkedHashMap<>();
    boolean requiresFaze =
        (safe(request.topic()) + " " + safe(request.keywords()) + " " + safe(request.notes()))
            .toLowerCase(Locale.ROOT)
            .contains("faze");
    for (String rawLine : markdown.split("\\R")) {
      if (!rawLine.contains("https://www.hltv.org/news/")
          && !rawLine.contains("https://www.hltv.org/forums/")) {
        continue;
      }
      String line = rawLine.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "");
      Matcher matcher = HLTV_RESULT_PATTERN.matcher(line);
      if (matcher.find()) {
        String title = cleanTitle(matcher.group(1));
        String url = matcher.group(2);
        if (requiresFaze && !(title + " " + url).toLowerCase(Locale.ROOT).contains("faze")) {
          continue;
        }
        String date = matcher.group(3) == null ? "" : matcher.group(3);
        if (!title.isBlank() && !title.equalsIgnoreCase("HLTV.org")) {
          deduped.putIfAbsent(url, new Candidate(title, url, date, score(title, url, date, request), ""));
        }
        continue;
      }
      Matcher urlMatcher = HLTV_URL_PATTERN.matcher(line);
      if (urlMatcher.find()) {
        String url = urlMatcher.group(1);
        String title = titleFromUrl(url);
        if (requiresFaze && !(title + " " + url).toLowerCase(Locale.ROOT).contains("faze")) {
          continue;
        }
        deduped.putIfAbsent(url, new Candidate(title, url, "", score(title, url, "", request), ""));
      }
    }
    return deduped;
  }

  private Article fetchArticle(Candidate candidate) {
    try {
      String markdown = fetchText(JINA_PREFIX + candidate.url());
      String title = find(TITLE_PATTERN, markdown).orElse(candidate.title());
      String url = find(URL_SOURCE_PATTERN, markdown).orElse(candidate.url());
      String published = find(PUBLISHED_PATTERN, markdown).orElse(candidate.date());
      String image = bestImage(markdown);
      if (image.isBlank() && !candidate.imageUrl().isBlank()) {
        image = candidate.imageUrl();
      }
      if (image.isBlank()) {
        image = fetchMetaImage(url).orElse("");
      }
      String excerpt = articleExcerpt(markdown, title);
      return new Article(title, url, published, excerpt, image);
    } catch (Exception ex) {
      return new Article(candidate.title(), candidate.url(), candidate.date(), "", candidate.imageUrl());
    }
  }

  private String fetchText(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", "Mozilla/5.0 (compatible; TrendCopyAI/0.2)")
            .GET()
            .build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("Research fetch failed: " + response.statusCode());
    }
    String body = response.body();
    return body.length() > 80_000 ? body.substring(0, 80_000) : body;
  }

  private Optional<String> fetchMetaImage(String url) {
    try {
      URI uri = URI.create(url);
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(8))
              .header("User-Agent", "Mozilla/5.0 (compatible; TrendCopyAI/0.2)")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return Optional.empty();
      }
      String body = response.body();
      if (body.length() > 1_000_000) {
        body = body.substring(0, 1_000_000);
      }
      String html = body;
      Optional<String> image = find(META_IMAGE_PATTERN, html)
          .or(() -> find(META_IMAGE_REVERSED_PATTERN, html));
      return image.map(value -> uri.resolve(value.trim()).toString()).filter(this::isUsefulImage);
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  private String webQuery(GenerateRequest request) {
    String query =
        String.join(" ", safe(request.topic()), safe(request.keywords()))
            .replaceAll("\\s+", " ")
            .trim();
    return query.isBlank() ? "AI news latest 2026" : query + " latest 2026";
  }

  private int scoreWeb(String title, String url, GenerateRequest request) {
    String haystack = (title + " " + url).toLowerCase(Locale.ROOT);
    String text = (safe(request.topic()) + " " + safe(request.keywords())).toLowerCase(Locale.ROOT);
    int score = 10;
    for (String term : text.split("[\\s，、,]+")) {
      if (term.length() >= 2 && haystack.contains(term.toLowerCase(Locale.ROOT))) {
        score += 8;
      }
    }
    if (url.contains("anthropic.com")) score += 12;
    if (url.contains("docs") || url.contains("blog") || url.contains("news")) score += 6;
    if (url.contains("youtube.com") || url.contains("reddit.com")) score -= 4;
    return score;
  }

  private String hltvQuery(GenerateRequest request) {
    String text =
        String.join(" ", safe(request.topic()), safe(request.keywords()), safe(request.notes()))
            .toLowerCase(Locale.ROOT);
    List<String> terms = new ArrayList<>();
    if (text.contains("faze")) terms.add("faze");
    if (text.contains("navi")) terms.add("navi");
    if (text.contains("g2")) terms.add("g2");
    if (text.contains("转会") || text.contains("人员") || text.contains("roster")) {
      terms.add("transfer");
      terms.add("roster");
      terms.add("departure");
      terms.add("signing");
      terms.add("loan");
    }
    Matcher latin = Pattern.compile("[a-zA-Z0-9]{2,}").matcher(text);
    while (latin.find() && terms.size() < 8) {
      String term = latin.group().toLowerCase(Locale.ROOT);
      if (!terms.contains(term) && !List.of("latest", "news", "hltv").contains(term)) {
        terms.add(term);
      }
    }
    if (terms.isEmpty()) {
      return "faze transfer roster";
    }
    return String.join(" ", terms);
  }

  private int score(String title, String url, String date, GenerateRequest request) {
    String haystack = (title + " " + url).toLowerCase(Locale.ROOT);
    String text =
        (safe(request.topic()) + " " + safe(request.keywords()) + " " + safe(request.notes()))
            .toLowerCase(Locale.ROOT);
    int score = url.contains("/news/") ? 20 : 8;
    if (haystack.contains("faze") && text.contains("faze")) score += 30;
    if (haystack.contains("transfer")) score += 20;
    if (haystack.contains("roster")) score += 16;
    if (haystack.contains("departure") || haystack.contains("sign") || haystack.contains("loan")) score += 14;
    if (haystack.contains("rumor") || haystack.contains("confirmed")) score += 12;
    if (haystack.contains("academy")) score -= 4;
    if (haystack.startsWith("video:") || haystack.contains("spray transfer")) score -= 55;
    if (date.startsWith("2026")) score += 32;
    else if (date.startsWith("2025")) score += 12;
    else if (!date.isBlank()) score -= 25;
    Matcher idMatcher = Pattern.compile("/news/(\\d+)/").matcher(url);
    if (idMatcher.find()) {
      int id = Integer.parseInt(idMatcher.group(1));
      if (id >= 43000) score += 20;
      else if (id < 40000) score -= 30;
    }
    return score;
  }

  private String bestImage(String markdown) {
    List<String> images = new ArrayList<>();
    Matcher matcher = IMAGE_PATTERN.matcher(markdown);
    while (matcher.find()) {
      images.add(matcher.group(1));
    }
    return images.stream()
        .filter(this::isUsefulImage)
        .filter(image -> image.contains("/gallerypicture/"))
        .findFirst()
        .or(() -> images.stream().filter(image -> image.contains("/playerbodyshot/")).findFirst())
        .or(() -> images.stream().filter(this::isUsefulImage).findFirst())
        .orElse("");
  }

  private String articleExcerpt(String markdown, String title) {
    int start = markdown.indexOf("# " + title);
    String body = start >= 0 ? markdown.substring(start + title.length() + 2) : markdown;
    body = body.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ");
    body = body.replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1");
    body = body.replaceAll("(?m)^\\s*[*#-].*$", " ");
    body = body.replaceAll("\\s+", " ").trim();
    return body.length() > 900 ? body.substring(0, 900) : body;
  }

  private List<String> urlsFrom(GenerateRequest request) {
    String text = String.join(" ", safe(request.game()), safe(request.topic()), safe(request.notes()));
    Matcher matcher = URL_PATTERN.matcher(text);
    List<String> urls = new ArrayList<>();
    while (matcher.find()) {
      if (!urls.contains(matcher.group())) {
        urls.add(matcher.group());
      }
    }
    return urls;
  }

  private boolean looksLikeHltv(GenerateRequest request) {
    String text =
        String.join(" ", safe(request.game()), safe(request.topic()), safe(request.notes()))
            .toLowerCase(Locale.ROOT);
    return text.contains("hltv") || text.contains("counter-strike") || text.contains("cs2");
  }

  private boolean isUsefulWebUrl(String url) {
    if (url == null || url.isBlank()) return false;
    String lower = url.toLowerCase(Locale.ROOT);
    return lower.startsWith("http")
        && !lower.contains("duckduckgo.com")
        && !lower.contains("google.com/search")
        && !lower.endsWith(".pdf");
  }

  private boolean isUsefulImage(String image) {
    String lower = image.toLowerCase(Locale.ROOT);
    return lower.startsWith("http")
        && !lower.contains("favicon")
        && !lower.contains(".ico")
        && !lower.contains("logo")
        && !lower.endsWith(".svg");
  }

  private String unwrapDuckUrl(String url) {
    try {
      URI uri = URI.create(url);
      String query = uri.getRawQuery();
      if (query == null) return url;
      for (String part : query.split("&")) {
        if (part.startsWith("uddg=")) {
          return java.net.URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8);
        }
      }
      return url;
    } catch (Exception ex) {
      return url;
    }
  }

  private String sourceName(String url, String fallback) {
    try {
      String host = URI.create(url).getHost();
      if (host == null || host.isBlank()) return fallback;
      host = host.replaceFirst("^www\\.", "");
      if (host.contains("hltv.org")) return "HLTV";
      if (host.contains("anthropic.com")) return "Anthropic";
      if (host.contains("claude.com")) return "Claude";
      return host;
    } catch (Exception ex) {
      return fallback;
    }
  }

  private Optional<String> find(Pattern pattern, String value) {
    Matcher matcher = pattern.matcher(value);
    return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
  }

  private String cleanTitle(String value) {
    return value
        .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String titleFromUrl(String url) {
    String slug = url.substring(url.lastIndexOf('/') + 1);
    return slug.replace('-', ' ').replaceAll("\\s+", " ").trim();
  }

  private String hostLabel(String url) {
    try {
      return URI.create(url).getHost().replaceFirst("^www\\.", "");
    } catch (Exception ex) {
      return "来源";
    }
  }

  private String hostTitle(String url) {
    return hostLabel(url) + " / 原文链接";
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private String blankTo(String value, String fallback) {
    return safe(value).isBlank() ? fallback : safe(value);
  }

  private record Candidate(String title, String url, String date, int score, String imageUrl) {}

  private record Article(String title, String url, String published, String excerpt, String imageUrl) {}
}
