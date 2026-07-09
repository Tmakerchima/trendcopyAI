package ai.gamecopy.usage;

public record UsageStatus(
    boolean authenticated,
    boolean unlimited,
    int used,
    int limit,
    int remaining
) {}
