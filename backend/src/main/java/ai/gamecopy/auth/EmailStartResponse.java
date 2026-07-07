package ai.gamecopy.auth;

public record EmailStartResponse(
    boolean registered,
    String nextStep,
    String message,
    int expiresInMinutes
) {}
