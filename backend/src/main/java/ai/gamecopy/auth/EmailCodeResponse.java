package ai.gamecopy.auth;

public record EmailCodeResponse(
    boolean sent,
    String message,
    int expiresInMinutes
) {}
