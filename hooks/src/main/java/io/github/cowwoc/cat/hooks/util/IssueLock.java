package io.github.cowwoc.cat.hooks.util;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

  private final JsonMapper mapper;
  private final Path lockDir;

  /**
   * Creates a new issue lock manager.
   *
   * @param mapper the JSON mapper to use for serialization
   * @param projectDir the project root directory containing .claude/cat/
   * @throws NullPointerException if mapper or projectDir is null
   * @throws IllegalArgumentException if projectDir is not a valid CAT project
   */
  public IssueLock(JsonMapper mapper, Path projectDir)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(projectDir, "projectDir").isNotNull();
    this.mapper = mapper;
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
     * Converts this result to JSON format matching the bash script output.
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
     * @param remoteAuthor the remote branch author
     * @param remoteEmail the remote branch author email
     * @param remoteDate the remote branch last commit date
     */
    record Locked(
      String status,
      String message,
      String owner,
      String action,
      String guidance,
      String remoteAuthor,
      String remoteEmail,
      String remoteDate) implements LockResult
    {
      /**
       * Creates a new locked result.
       *
       * @param status the operation status
       * @param message the status message
       * @param owner the session ID that owns the lock
       * @param action the suggested action
       * @param guidance the detailed guidance text
       * @param remoteAuthor the remote branch author
       * @param remoteEmail the remote branch author email
       * @param remoteDate the remote branch last commit date
       * @throws NullPointerException if {@code status}, {@code message}, {@code owner}, {@code action},
       *   {@code guidance}, {@code remoteAuthor}, {@code remoteEmail} or {@code remoteDate} are null
       */
      public Locked
      {
        requireThat(status, "status").isNotNull();
        requireThat(message, "message").isNotNull();
        requireThat(owner, "owner").isNotNull();
        requireThat(action, "action").isNotNull();
        requireThat(guidance, "guidance").isNotNull();
        requireThat(remoteAuthor, "remoteAuthor").isNotNull();
        requireThat(remoteEmail, "remoteEmail").isNotNull();
        requireThat(remoteDate, "remoteDate").isNotNull();
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
          "guidance", guidance,
          "remote_author", remoteAuthor,
          "remote_email", remoteEmail,
          "remote_date", remoteDate));
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
      Map<String, Object> lockData = mapper.readValue(content, Map.class);
      String existingSession = lockData.get("session_id").toString();

      if (existingSession.equals(sessionId))
        return new LockResult.Acquired("acquired", "Lock already held by this session");

      String remoteAuthor = "unknown";
      String remoteEmail = "";
      String remoteDate = "unknown";
      String remoteBranch = "";

      try
      {
        String branchList = GitCommands.runGitCommand("branch", "-r");
        for (String pattern : List.of("origin/" + issueId, "origin/*-" + issueId.replaceFirst("^[^-]*-", "")))
        {
          for (String line : branchList.split("\n"))
          {
            String branch = line.trim();
            if (branch.matches(".*" + Pattern.quote(pattern.replace("*", ".*")) + ".*"))
            {
              remoteBranch = branch;
              break;
            }
          }
          if (!remoteBranch.isEmpty())
            break;
        }

        if (!remoteBranch.isEmpty())
        {
          remoteAuthor = GitCommands.runGitCommand("log", "-1", "--format=%an", remoteBranch).trim();
          remoteEmail = GitCommands.runGitCommand("log", "-1", "--format=%ae", remoteBranch).trim();
          remoteDate = GitCommands.runGitCommand("log", "-1", "--format=%cr", remoteBranch).trim();
        }
      }
      catch (IOException _)
      {
        // Git operations failed - use defaults
      }

      return new LockResult.Locked("locked", "Issue locked by another session", existingSession,
        "FIND_ANOTHER_ISSUE",
        "Do NOT investigate, remove, or question this lock. Execute a different issue instead. " +
        "If you believe this is a stale lock from a crashed session, ask the USER to run /cat:cleanup.",
        remoteAuthor, remoteEmail, remoteDate);
    }

    long now = Instant.now().getEpochSecond();
    String createdIso = ISO_FORMATTER.format(Instant.now());

    Map<String, Object> lockData = Map.of(
      "session_id", sessionId,
      "created_at", now,
      "worktree", worktree,
      "created_iso", createdIso);

    Path tempFile = lockDir.resolve(sanitizeIssueId(issueId) + ".lock." + ProcessHandle.current().pid());
    Files.writeString(tempFile, mapper.writeValueAsString(lockData));

    try
    {
      Files.move(tempFile, lockFile, StandardCopyOption.ATOMIC_MOVE);
      return new LockResult.Acquired("acquired", "Lock acquired successfully");
    }
    catch (IOException _)
    {
      Files.deleteIfExists(tempFile);
      return new LockResult.Acquired("locked", "Lock acquired by another process during race");
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
    Map<String, Object> lockData = mapper.readValue(content, Map.class);
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
    Files.writeString(tempFile, mapper.writeValueAsString(updatedData));
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
    Map<String, Object> lockData = mapper.readValue(content, Map.class);
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
    Map<String, Object> lockData = mapper.readValue(content, Map.class);
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
    Map<String, Object> lockData = mapper.readValue(content, Map.class);

    String sessionId = lockData.get("session_id").toString();
    long createdAt = ((Number) lockData.get("created_at")).longValue();
    String worktree = lockData.getOrDefault("worktree", "").toString();

    long now = Instant.now().getEpochSecond();
    long age = now - createdAt;

    return new LockResult.CheckLocked(true, sessionId, age, worktree);
  }

  /**
   * Lists all locks.
   *
   * @return list of lock entries
   * @throws IOException if file operations fail
   */
  public List<LockListEntry> list() throws IOException
  {
    Files.createDirectories(lockDir);

    List<LockListEntry> locks = new ArrayList<>();
    long now = Instant.now().getEpochSecond();

    Files.list(lockDir).
      filter(path -> path.toString().endsWith(".lock")).
      forEach(lockFile ->
      {
        try
        {
          String issueId = lockFile.getFileName().toString().replace(".lock", "");
          String content = Files.readString(lockFile);
          @SuppressWarnings("unchecked")
          Map<String, Object> lockData = mapper.readValue(content, Map.class);

          String sessionId = lockData.get("session_id").toString();
          long createdAt = ((Number) lockData.get("created_at")).longValue();
          long age = now - createdAt;

          locks.add(new LockListEntry(issueId, sessionId, age));
        }
        catch (Exception _)
        {
          // Skip malformed lock files
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
   * Replaces forward slashes with hyphens to avoid directory traversal.
   *
   * @param issueId the issue identifier
   * @return the sanitized identifier
   */
  private String sanitizeIssueId(String issueId)
  {
    return issueId.replace('/', '-');
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
}
