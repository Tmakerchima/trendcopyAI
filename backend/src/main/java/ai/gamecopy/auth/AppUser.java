package ai.gamecopy.auth;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
    UUID id,
    String email,
    String displayName,
    String authProvider,
    String avatarUrl,
    String plan,
    Instant createdAt,
    Instant lastLoginAt
) {}
