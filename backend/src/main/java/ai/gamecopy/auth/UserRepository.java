package ai.gamecopy.auth;

import ai.gamecopy.config.DatabaseProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final DatabaseProperties properties;
  private final Map<String, AppUser> memoryUsers = new ConcurrentHashMap<>();
  private final Map<String, EmailCode> memoryCodes = new ConcurrentHashMap<>();
  private volatile boolean initialized;

  public UserRepository(DatabaseProperties properties) {
    this.properties = properties;
  }

  public AppUser upsertEmailUser(String email, String displayName) {
    String normalizedEmail = normalize(email);
    if (!databaseEnabled()) {
      AppUser existing = memoryUsers.get(normalizedEmail);
      AppUser user = new AppUser(
          existing == null ? UUID.randomUUID() : existing.id(),
          normalizedEmail,
          displayName,
          "email",
          "",
          existing == null ? "free" : existing.plan(),
          existing == null ? Instant.now() : existing.createdAt(),
          Instant.now()
      );
      memoryUsers.put(normalizedEmail, user);
      return user;
    }

    initSchema();
    try (Connection connection = connection()) {
      try (PreparedStatement statement = connection.prepareStatement("""
          insert into users (email, display_name, auth_provider, avatar_url, plan, last_login_at)
          values (?, ?, 'email', '', 'free', now())
          on conflict (email) do update set
            display_name = excluded.display_name,
            auth_provider = 'email',
            last_login_at = now()
          returning id, email, display_name, auth_provider, avatar_url, plan, created_at, last_login_at
          """)) {
        statement.setString(1, normalizedEmail);
        statement.setString(2, displayName);
        try (ResultSet result = statement.executeQuery()) {
          result.next();
          return mapUser(result);
        }
      }
    } catch (Exception error) {
      throw new IllegalStateException("Failed to save email user.", error);
    }
  }

  public Optional<AppUser> findByEmail(String email) {
    String normalizedEmail = normalize(email);
    if (!databaseEnabled()) {
      return Optional.ofNullable(memoryUsers.get(normalizedEmail));
    }

    initSchema();
    try (Connection connection = connection();
        PreparedStatement statement = connection.prepareStatement("""
            select id, email, display_name, auth_provider, avatar_url, plan, created_at, last_login_at
            from users
            where email = ?
            """)) {
      statement.setString(1, normalizedEmail);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
      }
    } catch (Exception error) {
      throw new IllegalStateException("Failed to load user.", error);
    }
  }

  public void saveEmailCode(String email, String codeHash, String purpose, Instant expiresAt) {
    String normalizedEmail = normalize(email);
    if (!databaseEnabled()) {
      memoryCodes.put(normalizedEmail + ":" + purpose, new EmailCode(
          normalizedEmail,
          codeHash,
          purpose,
          expiresAt,
          false
      ));
      return;
    }

    initSchema();
    try (Connection connection = connection();
        PreparedStatement statement = connection.prepareStatement("""
            insert into email_login_codes (email, code_hash, purpose, expires_at)
            values (?, ?, ?, ?)
            """)) {
      statement.setString(1, normalizedEmail);
      statement.setString(2, codeHash);
      statement.setString(3, purpose);
      statement.setTimestamp(4, Timestamp.from(expiresAt));
      statement.executeUpdate();
    } catch (Exception error) {
      throw new IllegalStateException("Failed to save email code.", error);
    }
  }

  public Optional<EmailCode> latestEmailCode(String email, String purpose) {
    String normalizedEmail = normalize(email);
    if (!databaseEnabled()) {
      return Optional.ofNullable(memoryCodes.get(normalizedEmail + ":" + purpose));
    }

    initSchema();
    try (Connection connection = connection();
        PreparedStatement statement = connection.prepareStatement("""
            select email, code_hash, purpose, expires_at, used
            from email_login_codes
            where email = ? and purpose = ?
            order by created_at desc
            limit 1
            """)) {
      statement.setString(1, normalizedEmail);
      statement.setString(2, purpose);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        return Optional.of(new EmailCode(
            result.getString("email"),
            result.getString("code_hash"),
            result.getString("purpose"),
            result.getTimestamp("expires_at").toInstant(),
            result.getBoolean("used")
        ));
      }
    } catch (Exception error) {
      throw new IllegalStateException("Failed to load email code.", error);
    }
  }

  public void markEmailCodeUsed(String email, String purpose) {
    String normalizedEmail = normalize(email);
    if (!databaseEnabled()) {
      EmailCode code = memoryCodes.get(normalizedEmail + ":" + purpose);
      if (code != null) {
        memoryCodes.put(normalizedEmail + ":" + purpose, new EmailCode(
            code.email(),
            code.codeHash(),
            code.purpose(),
            code.expiresAt(),
            true
        ));
      }
      return;
    }

    initSchema();
    try (Connection connection = connection();
        PreparedStatement statement = connection.prepareStatement("""
            update email_login_codes
            set used = true
            where email = ? and purpose = ? and used = false
            """)) {
      statement.setString(1, normalizedEmail);
      statement.setString(2, purpose);
      statement.executeUpdate();
    } catch (Exception error) {
      throw new IllegalStateException("Failed to mark email code used.", error);
    }
  }

  private void initSchema() {
    if (initialized || !databaseEnabled()) {
      return;
    }
    synchronized (this) {
      if (initialized) {
        return;
      }
      try (Connection connection = connection()) {
        connection.createStatement().execute("""
            create table if not exists users (
              id uuid primary key default gen_random_uuid(),
              email text unique not null,
              display_name text not null default '',
              auth_provider text not null default 'email',
              provider_subject text not null default '',
              avatar_url text not null default '',
              plan text not null default 'free',
              created_at timestamptz not null default now(),
              last_login_at timestamptz not null default now()
            )
            """);
        connection.createStatement().execute("""
            create table if not exists email_login_codes (
              id uuid primary key default gen_random_uuid(),
              email text not null,
              code_hash text not null,
              purpose text not null,
              expires_at timestamptz not null,
              used boolean not null default false,
              created_at timestamptz not null default now()
            )
            """);
        connection.createStatement().execute("""
            create table if not exists subscriptions (
              id uuid primary key default gen_random_uuid(),
              user_id uuid references users(id) on delete cascade,
              plan_id text not null,
              status text not null default 'active',
              starts_at timestamptz not null default now(),
              ends_at timestamptz,
              alipay_trade_no text,
              created_at timestamptz not null default now()
            )
            """);
        connection.createStatement().execute("""
            create table if not exists usage_events (
              id uuid primary key default gen_random_uuid(),
              user_id uuid references users(id) on delete set null,
              event_type text not null,
              quantity integer not null default 1,
              metadata jsonb not null default '{}'::jsonb,
              created_at timestamptz not null default now()
            )
            """);
        initialized = true;
      } catch (Exception error) {
        throw new IllegalStateException("Failed to initialize database schema.", error);
      }
    }
  }

  private AppUser mapUser(ResultSet result) throws Exception {
    return new AppUser(
        UUID.fromString(result.getString("id")),
        result.getString("email"),
        result.getString("display_name"),
        result.getString("auth_provider"),
        result.getString("avatar_url"),
        result.getString("plan"),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("last_login_at").toInstant()
    );
  }

  private Connection connection() throws Exception {
    if (properties.user() == null || properties.user().isBlank()) {
      return DriverManager.getConnection(properties.url());
    }
    return DriverManager.getConnection(properties.url(), properties.user(), properties.password());
  }

  private boolean databaseEnabled() {
    return properties.enabled();
  }

  private String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}
