/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Issue-level locking for concurrent CAT execution.
 * <p>
 * Provides atomic lock acquisition with persistent locks.
 * Locks never expire automatically - user must explicitly release or force-release.
 * Prevents multiple Claude instances from executing the same issue simultaneously.
 * <p>
 * Lock files are stored in .claude/cat/locks/&lt;issue-id&gt;.lock with JSON format:
 * {@code {"session_id": "uuid", "created_at": epochSeconds, "worktree": "path", "created_iso": "ISO-8601"}}
 * <p>
 * Lock operations return sealed {@link LockResult} subtypes:
 * <ul>
 *   <li>{@link LockResult.Acquired} - lock successfully acquired</li>
 *   <li>{@link LockResult.Locked} - issue is locked by another session</li>
 *   <li>{@link LockResult.Updated} - lock metadata updated</li>
 *   <li>{@link LockResult.Released} - lock released</li>
 *   <li>{@link LockResult.Error} - operation failed</li>
 *   <li>{@link LockResult.CheckLocked} - check() found active lock</li>
 *   <li>{@link LockResult.CheckUnlocked} - check() found no lock</li>
 * </ul>
 */
public final class IssueLock
{
  private static final Pattern UUID_PATTERN = Pattern.compile(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  private static final DateTimeFormatter ISO_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
  private static final int MAX_LOCK_FILES = 1000;

  private final JvmScope scope;
  private final Path lockDir;

  /**
   * Creates a new issue lock manager.
   *
   * @param scope the JVM scope providing JSON mapper and project directory
   * @throws NullPointerException if {@code scope} is null
   * @throws IllegalArgumentException if the project directory is not a valid CAT project
   */
  public IssueLock(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    Path projectDir = scope.getClaudeProjectDir();
    Path catDir = projectDir.resolve(".claude").resolve("cat");
    if (!Files.isDirectory(catDir))
    {
      throw new IllegalArgumentException("Not a CAT project: " + projectDir +
        " (no .claude/cat directory)");
    }
    this.lockDir = catDir.resolve("locks");
  }

  /**
   * Sealed result hierarchy for lock operations.
   * <p>
   * Each subtype produces a specific JSON structure via its toJson() method.
   */
  public sealed interface LockResult permits
    LockResult.Acquired,
    LockResult.Locked,
    LockResult.Updated,
    LockResult.Released,
    LockResult.Error,
    LockResult.CheckLocked,
    LockResult.CheckUnlocked
  {
    /**
     * Converts this result to JSON format matching the bash skill output.
     *
     * @param mapper the JSON mapper for serialization
     * @return JSON string representation
     * @throws NullPointerException if {@code mapper} is null
     * @throws IOException if JSON serialization fails
     */
    String toJson(JsonMapper mapper) throws IOException;

    /**
     * Lock successfully acquired.
     *
     * @param status the operation status
     * @param message the status message
     */
    record Acquired(String status, String message) implements LockResult
    {
      /**
       * Creates a new acquired result.
       *
       * @param status the operation status
       * @param message the status message
       * @throws NullPointerException if {@code status} or {@code message} are null
       */
      public Acquired
      {
        requireThat(status, "status").isNotNull();
        requireThat(message, "message").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", status,
          "message", message));
      }
    }

    /**
     * Issue is locked by another session.
     *
     * @param status the operation status
     * @param message the status message
     * @param owner the session ID that owns the lock
     * @param action the suggested action
     * @param guidance the detailed guidance text
     */
    record Locked(
      String status,
      String message,
      String owner,
      String action,
      String guidance) implements LockResult
    {
      /**
       * Creates a new locked result.
       *
       * @param status the operation status
       * @param message the status message
       * @param owner the session ID that owns the lock
       * @param action the suggested action
       * @param guidance the detailed guidance text
       * @throws NullPointerException if {@code status}, {@code message}, {@code owner}, {@code action}
       *   or {@code guidance} are null
       */
      public Locked
      {
        requireThat(status, "status").isNotNull();
        requireThat(message, "message").isNotNull();
        requireThat(owner, "owner").isNotNull();
        requireThat(action, "action").isNotNull();
        requireThat(guidance, "guidance").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", status,
          "message", message,
          "owner", owner,
          "action", action,
          "guidance", guidance));
      }
    }

    /**
     * Lock metadata updated with new worktree path.
     *
     * @param status the operation status
     * @param message the status message
     * @param worktree the worktree path
     */
    record Updated(String status, String message, String worktree) implements LockResult
    {
      /**
       * Creates a new updated result.
       *
       * @param status the operation status
       * @param message the status message
       * @param worktree the worktree path
       * @throws NullPointerException if {@code status}, {@code message} or {@code worktree} are null
       */
      public Updated
      {
        requireThat(status, "status").isNotNull();
        requireThat(message, "message").isNotNull();
        requireThat(worktree, "worktree").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", status,
          "message", message,
          "worktree", worktree));
      }
    }

    /**
     * Lock released successfully.
     *
     * @param status the operation status
     * @param message the status message
     */
    record Released(String status, String message) implements LockResult
    {
      /**
       * Creates a new released result.
       *
       * @param status the operation status
       * @param message the status message
       * @throws NullPointerException if {@code status} or {@code message} are null
       */
      public Released
      {
        requireThat(status, "status").isNotNull();
        requireThat(message, "message").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", status,
          "message", message));
      }
    }

    /**
     * Operation failed with error.
     *
     * @param status the operation status
     * @param message the error message
     */
    record Error(String status, String message) implements LockResult
    {
      /**
       * Creates a new error result.
       *
       * @param status the operation status
       * @param message the error message
       * @throws NullPointerException if {@code status} or {@code message} are null
       */
      public Error
      {
        requireThat(status, "status").isNotNull();
        requireThat(message, "message").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", status,
          "message", message));
      }
    }

    /**
     * Check operation found an active lock.
     *
     * @param locked always true
     * @param sessionId the session ID that owns the lock
     * @param ageSeconds the lock age in seconds
     * @param worktree the worktree path
     */
    record CheckLocked(boolean locked, String sessionId, long ageSeconds, String worktree)
      implements LockResult
    {
      /**
       * Creates a new check locked result.
       *
       * @param locked always true
       * @param sessionId the session ID that owns the lock
       * @param ageSeconds the lock age in seconds
       * @param worktree the worktree path
       * @throws NullPointerException if {@code sessionId} or {@code worktree} are null
       * @throws IllegalArgumentException if {@code locked} is false
       */
      public CheckLocked
      {
        requireThat(locked, "locked").isTrue();
        requireThat(sessionId, "sessionId").isNotNull();
        requireThat(worktree, "worktree").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "locked", true,
          "session_id", sessionId,
          "age_seconds", ageSeconds,
          "worktree", worktree));
      }
    }

    /**
     * Check operation found no lock.
     *
     * @param locked always false
     * @param message the status message
     */
    record CheckUnlocked(boolean locked, String message) implements LockResult
    {
      /**
       * Creates a new check unlocked result.
       *
       * @param locked always false
       * @param message the status message
       * @throws NullPointerException if {@code message} is null
       * @throws IllegalArgumentException if {@code locked} is true
       */
      public CheckUnlocked
      {
        requireThat(locked, "locked").isFalse();
        requireThat(message, "message").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "locked", false,
          "message", message));
      }
    }
  }

  /**
   * Lock list entry.
   *
   * @param issue the issue ID
   * @param session the session ID
   * @param ageSeconds the lock age in seconds
   */
  public record LockListEntry(String issue, String session, long ageSeconds)
  {
    /**
     * Creates a new lock list entry.
     *
     * @param issue the issue ID
     * @param session the session ID
     * @param ageSeconds the lock age in seconds
     * @throws NullPointerException if any parameter is null
     */
    public LockListEntry
    {
      requireThat(issue, "issue").isNotNull();
      requireThat(session, "session").isNotNull();
    }
  }

  /**
   * Acquires a lock for an issue.
   * <p>
   * If the lock already exists and is owned by a different session, returns a locked status
   * with guidance. If owned by the same session, returns success (idempotent).
   *
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @param worktree the worktree path (may be empty)
   * @return the lock result
   * @throws IllegalArgumentException if sessionId is not a valid UUID
   * @throws IOException if file operations fail
   */
  public LockResult acquire(String issueId, String sessionId, String worktree) throws IOException
  {
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(worktree, "worktree").isNotNull();

    validateSessionId(sessionId);

    Files.createDirectories(lockDir);
    Path lockFile = getLockFile(issueId);

    if (Files.exists(lockFile))
    {
      String content = Files.readString(lockFile);
      @SuppressWarnings("unchecked")
      Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);
      String existingSession = lockData.get("session_id").toString();

      if (existingSession.equals(sessionId))
        return new LockResult.Acquired("acquired", "Lock already held by this session");

      return new LockResult.Locked("locked", "Issue locked by another session", existingSession,
        "FIND_ANOTHER_ISSUE",
        "Do NOT investigate, remove, or question this lock. Execute a different issue instead. " +
        "If you believe this is a stale lock from a crashed session, ask the USER to run /cat:cleanup.");
    }

    long now = Instant.now().getEpochSecond();
    String createdIso = ISO_FORMATTER.format(Instant.now());

    Map<String, Object> lockData = Map.of(
      "session_id", sessionId,
      "created_at", now,
      "worktree", worktree,
      "created_iso", createdIso);

    Path tempFile = lockDir.resolve(sanitizeIssueId(issueId) + ".lock." + ProcessHandle.current().pid());
    Files.writeString(tempFile, scope.getJsonMapper().writeValueAsString(lockData));

    try
    {
      Files.move(tempFile, lockFile, StandardCopyOption.ATOMIC_MOVE);
      return new LockResult.Acquired("acquired", "Lock acquired successfully");
    }
    catch (IOException _)
    {
      Files.deleteIfExists(tempFile);
      // Another process acquired the lock during the race; read to find the owner
      try
      {
        String content = Files.readString(lockFile);
        @SuppressWarnings("unchecked")
        Map<String, Object> raceLockData = scope.getJsonMapper().readValue(content, Map.class);
        String raceOwner = raceLockData.get("session_id").toString();
        return new LockResult.Locked("locked", "Issue locked by another session", raceOwner,
          "FIND_ANOTHER_ISSUE",
          "Do NOT investigate, remove, or question this lock. Execute a different issue instead. " +
          "If you believe this is a stale lock from a crashed session, ask the USER to run /cat:cleanup.");
      }
      catch (Exception readEx)
      {
        return new LockResult.Error("error", "Race condition: could not read lock owner: " + readEx.getMessage());
      }
    }
  }

  /**
   * Updates the lock metadata with a new worktree path.
   * <p>
   * Only succeeds if the lock is owned by the specified session.
   *
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @param worktree the worktree path
   * @return the lock result
   * @throws IllegalArgumentException if sessionId is not a valid UUID
   * @throws IOException if file operations fail
   */
  public LockResult update(String issueId, String sessionId, String worktree) throws IOException
  {
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(worktree, "worktree").isNotBlank();

    validateSessionId(sessionId);

    Path lockFile = getLockFile(issueId);

    if (!Files.exists(lockFile))
      return new LockResult.Error("error", "No lock exists to update");

    String content = Files.readString(lockFile);
    @SuppressWarnings("unchecked")
    Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);
    String existingSession = lockData.get("session_id").toString();

    if (!existingSession.equals(sessionId))
      return new LockResult.Error("error", "Lock owned by different session: " + existingSession);

    long createdAt = ((Number) lockData.get("created_at")).longValue();
    String createdIso = lockData.getOrDefault("created_iso", "").toString();

    Map<String, Object> updatedData = Map.of(
      "session_id", sessionId,
      "created_at", createdAt,
      "worktree", worktree,
      "created_iso", createdIso);

    Path tempFile = lockDir.resolve(sanitizeIssueId(issueId) + ".lock." + ProcessHandle.current().pid());
    Files.writeString(tempFile, scope.getJsonMapper().writeValueAsString(updatedData));
    Files.move(tempFile, lockFile, StandardCopyOption.REPLACE_EXISTING);

    return new LockResult.Updated("updated", "Lock updated with worktree", worktree);
  }

  /**
   * Releases a lock for an issue.
   * <p>
   * Only succeeds if the lock is owned by the specified session.
   *
   * @param issueId the issue identifier
   * @param sessionId the Claude session UUID
   * @return the lock result
   * @throws IllegalArgumentException if sessionId is not a valid UUID
   * @throws IOException if file operations fail
   */
  public LockResult release(String issueId, String sessionId) throws IOException
  {
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();

    validateSessionId(sessionId);

    Path lockFile = getLockFile(issueId);

    if (!Files.exists(lockFile))
      return new LockResult.Released("released", "No lock exists");

    String content = Files.readString(lockFile);
    @SuppressWarnings("unchecked")
    Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);
    String existingSession = lockData.get("session_id").toString();

    if (!existingSession.equals(sessionId))
      return new LockResult.Error("error", "Lock owned by different session: " + existingSession);

    Files.delete(lockFile);
    return new LockResult.Released("released", "Lock released successfully");
  }

  /**
   * Force releases a lock for an issue, ignoring session ownership.
   * <p>
   * This is a user action for cleaning up stale locks from crashed sessions.
   *
   * @param issueId the issue identifier
   * @return the lock result
   * @throws IOException if file operations fail
   */
  public LockResult forceRelease(String issueId) throws IOException
  {
    requireThat(issueId, "issueId").isNotBlank();

    Path lockFile = getLockFile(issueId);

    if (!Files.exists(lockFile))
      return new LockResult.Released("released", "No lock exists");

    String content = Files.readString(lockFile);
    @SuppressWarnings("unchecked")
    Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);
    String existingSession = lockData.get("session_id").toString();

    Files.delete(lockFile);
    return new LockResult.Released("released", "Lock forcibly released (was owned by " + existingSession + ")");
  }

  /**
   * Checks if an issue is locked.
   *
   * @param issueId the issue identifier
   * @return the lock result with status information
   * @throws IOException if file operations fail
   */
  public LockResult check(String issueId) throws IOException
  {
    requireThat(issueId, "issueId").isNotBlank();

    Path lockFile = getLockFile(issueId);

    if (!Files.exists(lockFile))
      return new LockResult.CheckUnlocked(false, "Issue not locked");

    String content = Files.readString(lockFile);
    @SuppressWarnings("unchecked")
    Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);

    String sessionId = lockData.get("session_id").toString();
    long createdAt = ((Number) lockData.get("created_at")).longValue();
    String worktree = lockData.getOrDefault("worktree", "").toString();

    long now = Instant.now().getEpochSecond();
    long age = now - createdAt;

    return new LockResult.CheckLocked(true, sessionId, age, worktree);
  }

  /**
   * Lists all locks.
   * <p>
   * Skips malformed lock files with a warning to stderr.
   * Returns at most {@value MAX_LOCK_FILES} entries; warns to stderr if the limit is exceeded.
   *
   * @return list of lock entries
   * @throws IOException if file operations fail
   */
  public List<LockListEntry> list() throws IOException
  {
    Files.createDirectories(lockDir);

    List<LockListEntry> locks = new ArrayList<>();
    long now = Instant.now().getEpochSecond();
    int[] count = {0};

    Files.list(lockDir).
      filter(path -> path.toString().endsWith(".lock")).
      forEach(lockFile ->
      {
        if (count[0] >= MAX_LOCK_FILES)
        {
          System.err.println("WARNING: More than " + MAX_LOCK_FILES +
            " lock files found in " + lockDir + ". Only the first " + MAX_LOCK_FILES + " are listed.");
          return;
        }
        try
        {
          String issueId = lockFile.getFileName().toString().replace(".lock", "");
          String content = Files.readString(lockFile);
          @SuppressWarnings("unchecked")
          Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);

          String sessionId = lockData.get("session_id").toString();
          long createdAt = ((Number) lockData.get("created_at")).longValue();
          long age = now - createdAt;

          locks.add(new LockListEntry(issueId, sessionId, age));
          ++count[0];
        }
        catch (Exception e)
        {
          System.err.println("WARNING: Skipping malformed lock file " + lockFile + ": " + e.getMessage());
        }
      });

    return locks;
  }

  /**
   * Gets the lock file path for an issue.
   *
   * @param issueId the issue identifier
   * @return the lock file path
   */
  private Path getLockFile(String issueId)
  {
    return lockDir.resolve(sanitizeIssueId(issueId) + ".lock");
  }

  /**
   * Sanitizes an issue ID for use as a filename.
   * <p>
   * Replaces forward slashes, backslashes, and {@code ..} sequences with hyphens to prevent
   * directory traversal attacks.
   *
   * @param issueId the issue identifier
   * @return the sanitized identifier
   */
  private String sanitizeIssueId(String issueId)
  {
    return issueId.
      replace('/', '-').
      replace('\\', '-').
      replace("..", "-");
  }

  /**
   * Validates that a session ID is a valid UUID.
   *
   * @param sessionId the session ID to validate
   * @throws IllegalArgumentException if the session ID is not a valid UUID
   */
  private void validateSessionId(String sessionId)
  {
    if (!UUID_PATTERN.matcher(sessionId).matches())
    {
      throw new IllegalArgumentException("Invalid session_id format: '" + sessionId +
        "'. Expected UUID. Did you swap issue_id and session_id arguments?");
    }
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Commands:
   * <ul>
   *   <li>{@code acquire <issue-id> <session-id> [worktree]}</li>
   *   <li>{@code update <issue-id> <session-id> <worktree>}</li>
   *   <li>{@code release <issue-id> <session-id>}</li>
   *   <li>{@code force-release <issue-id>}</li>
   *   <li>{@code check <issue-id>}</li>
   *   <li>{@code list}</li>
   * </ul>
   * <p>
   * The project directory is read from the {@code CLAUDE_PROJECT_DIR} environment variable.
   *
   * @param args command-line arguments
   * @throws IOException if file operations fail
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 1)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: issue-lock <command> [args].\
         Commands: acquire, update, release, force-release, check, list"
        }""");
      System.exit(1);
      return;
    }

    String command = args[0];

    try (MainJvmScope scope = new MainJvmScope())
    {
      boolean success = runWithScope(args, command, scope, System.out, System.err);
      if (!success)
        System.exit(1);
    }
  }

  /**
   * Executes a command with an injectable scope and output streams for testability.
   * <p>
   * This method does not call {@link System#exit(int)}.
   * Error output is written to {@code err} and {@code false} is returned on failure.
   *
   * @param args the command-line arguments
   * @param scope the JVM scope to use for lock operations
   * @param out the output stream for successful results
   * @param err the error stream for error messages
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if file operations fail
   */
  public static boolean run(String[] args, JvmScope scope, PrintStream out, PrintStream err) throws IOException
  {
    if (args.length < 1)
    {
      err.println("""
        {
          "status": "error",
          "message": "Usage: issue-lock <command> [args].\
         Commands: acquire, update, release, force-release, check, list"
        }""");
      return false;
    }

    String command = args[0];
    return runWithScope(args, command, scope, out, err);
  }

  /**
   * Executes a command using the provided scope.
   *
   * @param args the full command-line arguments
   * @param command the parsed command name
   * @param scope the JVM scope to use
   * @param out the output stream for successful results
   * @param err the error stream for error messages
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if file operations fail
   */
  private static boolean runWithScope(String[] args, String command, JvmScope scope, PrintStream out,
    PrintStream err) throws IOException
  {
    JsonMapper mapper = scope.getJsonMapper();
    try
    {
      IssueLock lock = new IssueLock(scope);

      return switch (command)
      {
        case "acquire" -> handleAcquire(lock, mapper, args, out, err);
        case "update" -> handleUpdate(lock, mapper, args, out, err);
        case "release" -> handleRelease(lock, mapper, args, out, err);
        case "force-release" -> handleForceRelease(lock, mapper, args, out, err);
        case "check" -> handleCheck(lock, mapper, args, out, err);
        case "list" ->
        {
          handleList(lock, mapper, out);
          yield true;
        }
        default ->
        {
          err.println(mapper.writeValueAsString(Map.of(
            "status", "error",
            "message", "Unknown command: " + command +
              ". Use acquire, update, release, force-release, check, or list.")));
          yield false;
        }
      };
    }
    catch (IllegalArgumentException e)
    {
      err.println(mapper.writeValueAsString(Map.of(
        "status", "error",
        "message", e.getMessage())));
      return false;
    }
  }

  /**
   * Handles the acquire subcommand.
   *
   * @param lock the issue lock instance
   * @param mapper the JSON mapper
   * @param args the command-line arguments
   * @param out the output stream
   * @param err the error stream
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if the operation fails
   */
  private static boolean handleAcquire(IssueLock lock, JsonMapper mapper, String[] args, PrintStream out,
    PrintStream err) throws IOException
  {
    if (args.length < 3)
    {
      err.println("""
        {
          "status": "error",
          "message": "Usage: acquire <issue-id> <session-id> [worktree]"
        }""");
      return false;
    }
    String issueId = args[1];
    String sessionId = args[2];
    String worktree;
    if (args.length > 3)
      worktree = args[3];
    else
      worktree = "";
    LockResult result = lock.acquire(issueId, sessionId, worktree);
    out.println(result.toJson(mapper));
    return !(result instanceof LockResult.Locked);
  }

  /**
   * Handles the update subcommand.
   *
   * @param lock the issue lock instance
   * @param mapper the JSON mapper
   * @param args the command-line arguments
   * @param out the output stream
   * @param err the error stream
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if the operation fails
   */
  private static boolean handleUpdate(IssueLock lock, JsonMapper mapper, String[] args, PrintStream out,
    PrintStream err) throws IOException
  {
    if (args.length < 4)
    {
      err.println("""
        {
          "status": "error",
          "message": "Usage: update <issue-id> <session-id> <worktree>"
        }""");
      return false;
    }
    LockResult result = lock.update(args[1], args[2], args[3]);
    out.println(result.toJson(mapper));
    return !(result instanceof LockResult.Error);
  }

  /**
   * Handles the release subcommand.
   *
   * @param lock the issue lock instance
   * @param mapper the JSON mapper
   * @param args the command-line arguments
   * @param out the output stream
   * @param err the error stream
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if the operation fails
   */
  private static boolean handleRelease(IssueLock lock, JsonMapper mapper, String[] args, PrintStream out,
    PrintStream err) throws IOException
  {
    if (args.length < 3)
    {
      err.println("""
        {
          "status": "error",
          "message": "Usage: release <issue-id> <session-id>"
        }""");
      return false;
    }
    LockResult result = lock.release(args[1], args[2]);
    out.println(result.toJson(mapper));
    return !(result instanceof LockResult.Error);
  }

  /**
   * Handles the force-release subcommand.
   *
   * @param lock the issue lock instance
   * @param mapper the JSON mapper
   * @param args the command-line arguments
   * @param out the output stream
   * @param err the error stream
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if the operation fails
   */
  private static boolean handleForceRelease(IssueLock lock, JsonMapper mapper, String[] args, PrintStream out,
    PrintStream err) throws IOException
  {
    if (args.length < 2)
    {
      err.println("""
        {
          "status": "error",
          "message": "Usage: force-release <issue-id>"
        }""");
      return false;
    }
    LockResult result = lock.forceRelease(args[1]);
    out.println(result.toJson(mapper));
    return true;
  }

  /**
   * Handles the check subcommand.
   *
   * @param lock the issue lock instance
   * @param mapper the JSON mapper
   * @param args the command-line arguments
   * @param out the output stream
   * @param err the error stream
   * @return true if the command succeeded, false if an error occurred
   * @throws IOException if the operation fails
   */
  private static boolean handleCheck(IssueLock lock, JsonMapper mapper, String[] args, PrintStream out,
    PrintStream err) throws IOException
  {
    if (args.length < 2)
    {
      err.println("""
        {
          "status": "error",
          "message": "Usage: check <issue-id>"
        }""");
      return false;
    }
    LockResult result = lock.check(args[1]);
    out.println(result.toJson(mapper));
    return true;
  }

  /**
   * Handles the list subcommand.
   *
   * @param lock the issue lock instance
   * @param mapper the JSON mapper
   * @param out the output stream
   * @throws IOException if the operation fails
   */
  private static void handleList(IssueLock lock, JsonMapper mapper, PrintStream out) throws IOException
  {
    List<LockListEntry> locks = lock.list();
    List<Map<String, Object>> lockMaps = new ArrayList<>();
    for (LockListEntry entry : locks)
    {
      Map<String, Object> entryMap = new LinkedHashMap<>();
      entryMap.put("issue", entry.issue());
      entryMap.put("session", entry.session());
      entryMap.put("age_seconds", entry.ageSeconds());
      lockMaps.add(entryMap);
    }
    out.println(mapper.writeValueAsString(lockMaps));
  }
}
