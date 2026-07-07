package ai.gamecopy.auth;

public record CurrentUser(
    boolean authenticated,
    String provider,
    String name,
    String email,
    boolean permanent,
    String plan,
    String avatarUrl
) {
  public static CurrentUser anonymous() {
    return new CurrentUser(false, "", "", "", false, "", "");
  }

  public static CurrentUser authenticated(String provider, String name, String email, String avatarUrl) {
    boolean permanent = "tamkerchima@gmail.com".equalsIgnoreCase(email == null ? "" : email.trim());
    return new CurrentUser(
        true,
        provider,
        name,
        email == null ? "" : email,
        permanent,
        permanent ? "Permanent" : "",
        avatarUrl == null ? "" : avatarUrl
    );
  }
}
