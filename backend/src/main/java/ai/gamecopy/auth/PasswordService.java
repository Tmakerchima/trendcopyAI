package ai.gamecopy.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int ITERATIONS = 120_000;
  private static final int KEY_LENGTH = 256;

  public String hash(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    byte[] hash = pbkdf2(password, salt, ITERATIONS);
    return "pbkdf2$%d$%s$%s".formatted(
        ITERATIONS,
        Base64.getEncoder().encodeToString(salt),
        Base64.getEncoder().encodeToString(hash)
    );
  }

  public boolean verify(String password, String encodedHash) {
    if (password == null || encodedHash == null || encodedHash.isBlank()) {
      return false;
    }
    String[] parts = encodedHash.split("\\$");
    if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
      return false;
    }
    int iterations = Integer.parseInt(parts[1]);
    byte[] salt = Base64.getDecoder().decode(parts[2]);
    byte[] expected = Base64.getDecoder().decode(parts[3]);
    byte[] actual = pbkdf2(password, salt, iterations);
    return MessageDigest.isEqual(expected, actual);
  }

  private byte[] pbkdf2(String password, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(
          password == null ? new char[0] : password.toCharArray(),
          salt,
          iterations,
          KEY_LENGTH
      );
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception error) {
      throw new IllegalStateException("Unable to hash password.", error);
    }
  }
}
