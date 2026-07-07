package ai.gamecopy.auth;

import java.time.Instant;

record EmailCode(
    String email,
    String codeHash,
    String purpose,
    Instant expiresAt,
    boolean used
) {}
