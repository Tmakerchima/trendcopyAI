package ai.gamecopy.trends;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trends")
public class TrendImageController {
  private static final Pattern REPO_PATTERN =
      Pattern.compile("\\b([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)\\b");
  private static final Pattern URL_PATTERN =
      Pattern.compile("https?://[^\\s\"'<>，。)）]+", Pattern.CASE_INSENSITIVE);
  private static final Pattern META_IMAGE_PATTERN =
      Pattern.compile(
          "<meta\\s+[^>]*(?:property|name)=[\"'](?:og:image|twitter:image|twitter:image:src)[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern META_IMAGE_REVERSED_PATTERN =
      Pattern.compile(
          "<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image|twitter:image:src)[\"'][^>]*>",
          Pattern.CASE_INSENSITIVE);

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(4))
          .build();
  private final TrendResearchService trendResearchService;

  public TrendImageController(TrendResearchService trendResearchService) {
    this.trendResearchService = trendResearchService;
  }

  @GetMapping("/image")
  public ResponseEntity<?> image(
      @RequestParam(defaultValue = "") String source,
      @RequestParam(defaultValue = "") String topic,
      @RequestParam(defaultValue = "") String keywords,
      @RequestParam(defaultValue = "") String notes,
      @RequestParam(defaultValue = "") String imageUrl) {
    Optional<String> resolvedImageUrl =
        publicImage(imageUrl)
            .or(() -> repoImage(topic))
            .or(() -> pageImage(topic + " " + notes))
            .or(() -> trendResearchService.resolveImage(source, topic, keywords, notes));

    if (resolvedImageUrl.isPresent()) {
      Optional<ResponseEntity<byte[]>> proxiedImage = fetchImage(resolvedImageUrl.get());
      if (proxiedImage.isPresent()) {
        return proxiedImage.get();
      }
    }

    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
        .contentType(MediaType.valueOf("image/svg+xml"))
        .body(generatedTrendSvg(source, topic, keywords));
  }

  private Optional<String> publicImage(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return Optional.empty();
    }
    try {
      URI uri = URI.create(imageUrl.trim());
      return isPublicHttpUrl(uri) ? Optional.of(uri.toString()) : Optional.empty();
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private Optional<ResponseEntity<byte[]>> fetchImage(String imageUrl) {
    URI uri;
    try {
      uri = URI.create(imageUrl);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
    if (!isPublicHttpUrl(uri)) {
      return Optional.empty();
    }

    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(6))
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (compatible; TrendCopyAI/0.2; +https://localhost)")
              .GET()
              .build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      String contentType = response.headers().firstValue("content-type").orElse("image/jpeg");
      if (response.statusCode() < 200
          || response.statusCode() >= 300
          || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
          || response.body().length > 5_000_000) {
        return Optional.empty();
      }
      return Optional.of(
          ResponseEntity.ok()
              .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
              .contentType(MediaType.parseMediaType(contentType))
              .body(response.body()));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  private Optional<String> repoImage(String topic) {
    Matcher matcher = REPO_PATTERN.matcher(topic == null ? "" : topic);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of("https://opengraph.githubassets.com/trendcopy-ai/" + matcher.group(1));
  }

  private Optional<String> pageImage(String text) {
    Matcher urlMatcher = URL_PATTERN.matcher(text == null ? "" : text);
    if (!urlMatcher.find()) {
      return Optional.empty();
    }

    String url = urlMatcher.group();
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
    if (!isPublicHttpUrl(uri)) {
      return Optional.empty();
    }

    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(5))
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (compatible; TrendCopyAI/0.2; +https://localhost)")
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
      return metaImage(body).map(image -> uri.resolve(image).toString());
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  private Optional<String> metaImage(String html) {
    Matcher matcher = META_IMAGE_PATTERN.matcher(html);
    if (matcher.find()) {
      return Optional.of(matcher.group(1).trim());
    }
    matcher = META_IMAGE_REVERSED_PATTERN.matcher(html);
    if (matcher.find()) {
      return Optional.of(matcher.group(1).trim());
    }
    return Optional.empty();
  }

  private boolean isPublicHttpUrl(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null) {
      return false;
    }
    String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
    if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
      return false;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return false;
    }
    try {
      for (InetAddress address : InetAddress.getAllByName(host)) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()) {
          return false;
        }
      }
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private String generatedTrendSvg(String source, String topic, String keywords) {
    String title = truncate(clean(topic).isBlank() ? "AI Trend" : clean(topic), 42);
    String meta = truncate(clean(source).isBlank() ? "Trend source" : clean(source), 28);
    String tag = truncate(clean(keywords).isBlank() ? "AI tools" : clean(keywords), 30);
    return """
        <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="900" viewBox="0 0 1200 900">
          <defs>
            <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0" stop-color="#fffaf1"/>
              <stop offset="0.52" stop-color="#efe5d7"/>
              <stop offset="1" stop-color="#c8b79f"/>
            </linearGradient>
            <linearGradient id="ink" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0" stop-color="#15120f"/>
              <stop offset="1" stop-color="#394c65"/>
            </linearGradient>
          </defs>
          <rect width="1200" height="900" fill="url(#bg)"/>
          <rect x="70" y="70" width="1060" height="760" rx="34" fill="#fffaf1" opacity=".72"/>
          <circle cx="920" cy="230" r="150" fill="#b9462f" opacity=".16"/>
          <circle cx="230" cy="650" r="190" fill="#5f7057" opacity=".14"/>
          <path d="M170 590 C340 470 420 520 570 390 C730 250 850 300 1010 170" fill="none" stroke="#15120f" stroke-width="18" stroke-linecap="round" opacity=".16"/>
          <path d="M170 635 C350 515 470 575 635 430 C775 305 895 360 1030 235" fill="none" stroke="#b9462f" stroke-width="5" stroke-linecap="round"/>
          <text x="92" y="132" font-family="Inter, Arial, sans-serif" font-size="32" font-weight="700" fill="#b9462f">TREND</text>
          <text x="92" y="230" font-family="Georgia, 'Times New Roman', serif" font-size="66" font-weight="600" fill="#15120f">%s</text>
          <text x="94" y="302" font-family="Inter, Arial, sans-serif" font-size="30" fill="#394c65">%s</text>
          <rect x="92" y="690" width="430" height="82" rx="41" fill="#15120f"/>
          <text x="128" y="743" font-family="Inter, Arial, sans-serif" font-size="28" font-weight="700" fill="#fffaf1">%s</text>
          <text x="940" y="770" font-family="Inter, Arial, sans-serif" font-size="52" font-weight="800" fill="url(#ink)">AI</text>
        </svg>
        """.formatted(escapeXml(title), escapeXml(meta), escapeXml(tag));
  }

  private String clean(String value) {
    return (value == null ? "" : value).replaceAll("[\\r\\n]+", " ").trim();
  }

  private String truncate(String value, int maxLength) {
    String safeValue = value == null ? "" : value;
    return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength - 1) + "…";
  }

  private String escapeXml(String value) {
    return clean(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
