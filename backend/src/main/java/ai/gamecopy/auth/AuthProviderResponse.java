package ai.gamecopy.auth;

public record AuthProviderResponse(
    String id,
    String name,
    boolean enabled,
    String loginUrl
) {
}
