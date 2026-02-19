/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block rm -rf and git worktree remove when deletion would affect protected paths.
 * <p>
 * M464, M491: Prevent shell session corruption from deleting current working directory
 * or other active worktrees.
 * <p>
 * Protection strategy: Build a set of protected paths (main worktree, session CWD,
 * locked worktrees) and block deletion if any protected path is inside or equal to
 * the deletion target.
 */
public final class BlockUnsafeRemoval implements BashHandler
{
  private static final Pattern WORKTREE_REMOVE_PATTERN =
    Pattern.compile("\\bgit\\s+worktree\\s+remove\\s+(?:-[^\\s]+\\s+)*([^\\s;&|]+)", Pattern.CASE_INSENSITIVE);
  private static final Duration STALE_LOCK_THRESHOLD = Duration.ofHours(4);
  private final JsonMapper jsonMapper;
  private final Clock clock;

  /**
   * Creates a new handler for blocking unsafe directory removal.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockUnsafeRemoval(JvmScope scope)
  {
    this(scope, Clock.systemUTC());
  }

  /**
   * Creates a new handler for blocking unsafe directory removal.
   *
   * @param scope the JVM scope providing access to shared resources
   * @param clock the clock to use for determining lock staleness
   * @throws NullPointerException if {@code scope} or {@code clock} are null
   */
  public BlockUnsafeRemoval(JvmScope scope, Clock clock)
  {
    assert that(scope, "scope").isNotNull().elseThrow();
    assert that(clock, "clock").isNotNull().elseThrow();
    this.jsonMapper = scope.getJsonMapper();
    this.clock = clock;
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    try
    {
      String commandLower = command.toLowerCase(Locale.ENGLISH);

      // Check rm -rf commands
      if (commandLower.contains("rm") && hasRecursiveFlag(command))
      {
        Result rmResult = checkRmCommand(command, workingDirectory, sessionId);
        if (rmResult != null)
          return rmResult;
      }

      // Check git worktree remove commands
      if (commandLower.contains("git") && commandLower.contains("worktree") &&
          commandLower.contains("remove"))
      {
        Result worktreeResult = checkWorktreeRemove(command, workingDirectory, sessionId);
        if (worktreeResult != null)
          return worktreeResult;
      }

      return Result.allow();
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Checks whether the command contains a recursive flag for rm.
   * Looks for -r or -R as standalone flags or combined with other flags.
   *
   * @param command the bash command
   * @return true if a recursive flag is present, false otherwise
   */
  private boolean hasRecursiveFlag(String command)
  {
    Pattern flagPattern = Pattern.compile("(?:^|\\s)-[^\\s]*[rR]");
    return flagPattern.matcher(command).find() || command.contains("--recursive");
  }

  /**
   * Extracts path arguments from an rm command.
   * Handles options before, between, and after paths.
   *
   * @param command the bash command
   * @return list of path arguments
   */
  private List<String> extractRmTargets(String command)
  {
    List<String> targets = new ArrayList<>();

    // Find "rm" and extract everything after it until shell operators
    int rmIndex = command.toLowerCase(Locale.ENGLISH).indexOf("rm");
    if (rmIndex == -1)
      return targets;

    // Extract the portion after "rm" until we hit a shell operator
    String afterRm = command.substring(rmIndex + 2);
    int operatorIndex = afterRm.length();
    for (char operator : new char[] {';', '&', '|', '>'})
    {
      int index = afterRm.indexOf(operator);
      if (index != -1 && index < operatorIndex)
        operatorIndex = index;
    }
    String rmArgs = afterRm.substring(0, operatorIndex);

    // Tokenize by whitespace, respecting quotes
    List<String> tokens = tokenize(rmArgs);

    boolean endOfOptions = false;
    for (String token : tokens)
    {
      if (token.equals("--"))
      {
        endOfOptions = true;
        continue;
      }

      // After --, everything is a path even if it starts with -
      if (endOfOptions)
      {
        targets.add(token);
        continue;
      }

      // Skip flag tokens (starting with -)
      if (token.startsWith("-"))
        continue;

      // Non-flag tokens are paths
      targets.add(token);
    }

    return targets;
  }

  /**
   * Tokenizes a string by whitespace, respecting single and double quotes.
   *
   * @param input the input string
   * @return list of tokens
   */
  private List<String> tokenize(String input)
  {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;

    for (int i = 0; i < input.length(); ++i)
    {
      char c = input.charAt(i);

      if (c == '\'' && !inDoubleQuote)
      {
        inSingleQuote = !inSingleQuote;
        continue;
      }

      if (c == '"' && !inSingleQuote)
      {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }

      if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote)
      {
        if (current.length() > 0)
        {
          tokens.add(current.toString());
          current.setLength(0);
        }
        continue;
      }

      current.append(c);
    }

    if (current.length() > 0)
      tokens.add(current.toString());

    return tokens;
  }

  /**
   * Checks if an rm command would delete any protected path.
   *
   * @param command the bash command
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @return a block result if unsafe removal detected, null otherwise
   * @throws IOException if path operations fail
   */
  private Result checkRmCommand(String command, String workingDirectory, String sessionId) throws IOException
  {
    List<String> targets = extractRmTargets(command);

    for (String target : targets)
    {
      Result blockResult = checkProtectedPaths(target, workingDirectory, sessionId, "rm (recursive)");
      if (blockResult != null)
        return blockResult;
    }

    return null;
  }

  /**
   * Checks if a git worktree remove command would delete any protected path.
   *
   * @param command the bash command
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @return a block result if unsafe removal detected, null otherwise
   * @throws IOException if path operations fail
   */
  private Result checkWorktreeRemove(String command, String workingDirectory, String sessionId) throws IOException
  {
    Matcher matcher = WORKTREE_REMOVE_PATTERN.matcher(command);

    while (matcher.find())
    {
      String target = matcher.group(1);
      if (target == null || target.isEmpty())
        continue;

      // Check if deletion would affect protected paths
      Result blockResult = checkProtectedPaths(target, workingDirectory, sessionId, "git worktree remove");
      if (blockResult != null)
        return blockResult;
    }

    return null;
  }

  /**
   * Checks if deletion target would affect any protected paths.
   *
   * @param target the deletion target path
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @param commandType the command type for error messages
   * @return a block result if protected paths would be affected, null otherwise
   * @throws IOException if path operations fail
   */
  private Result checkProtectedPaths(String target, String workingDirectory, String sessionId,
    String commandType) throws IOException
  {
    Set<Path> protectedPaths = getProtectedPaths(workingDirectory, sessionId);
    if (protectedPaths.isEmpty())
      return null;

    // Resolve target path (may not exist yet, so use normalize not toRealPath)
    Path targetPath = resolvePath(target, workingDirectory);

    // Check if any protected path is inside or equal to the target
    for (Path protectedPath : protectedPaths)
    {
      if (isInsideOrEqual(protectedPath, targetPath))
      {
        return Result.block(String.format("""
          UNSAFE DIRECTORY REMOVAL BLOCKED (M464, M491)

          Attempted: %s %s
          Problem:   A protected path is inside the deletion target
          Protected: %s
          Target:    %s

          WHY THIS IS BLOCKED:
          - Deleting a directory containing your current location corrupts the shell session
          - Deleting active worktrees causes loss of uncommitted work
          - All subsequent Bash commands will fail with "Exit code 1"
          - Recovery requires restarting Claude Code entirely

          WHAT TO DO:
          1. Change directory first: cd /workspace (or a safe parent directory)
          2. Then delete: %s %s

          See: /cat:safe-rm for detailed safe removal protocol
          """, commandType, target, protectedPath, targetPath, commandType, target));
      }
    }

    return null;
  }

  /**
   * Builds a set of protected paths that should not be deleted.
   * <p>
   * Protected paths include:
   * <ul>
   *   <li>Main worktree root (derived from git structure)</li>
   *   <li>Current session's CWD (from hook input)</li>
   *   <li>Locked worktrees owned by other active sessions</li>
   * </ul>
   * <p>
   * Worktrees locked by the current session are NOT protected, allowing the owning
   * session to clean up its own worktrees during merge/cleanup. Locks older than 4
   * hours are treated as stale (from dead sessions) and are not protected.
   *
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @return set of protected paths
   * @throws IOException if path operations fail
   */
  private Set<Path> getProtectedPaths(String workingDirectory, String sessionId) throws IOException
  {
    Set<Path> paths = new HashSet<>();

    if (workingDirectory.isEmpty())
      return paths;

    // 1. Main worktree root - walk up from cwd to find .git DIRECTORY (not file)
    Path mainWorktree = findMainWorktree(workingDirectory);
    if (mainWorktree != null)
    {
      paths.add(mainWorktree.toRealPath());

      // 3. Locked worktrees owned by OTHER active sessions
      Path locksDir = mainWorktree.resolve(".claude/cat/locks");
      if (Files.isDirectory(locksDir))
      {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(locksDir, "*.lock"))
        {
          for (Path lockFile : stream)
          {
            // Skip worktrees owned by the current session
            if (isOwnedBySession(lockFile, sessionId))
              continue;

            // Skip stale locks (older than 4 hours) - they are from dead sessions
            if (isStale(lockFile))
              continue;

            // Lock file name = {issue-id}.lock
            // Worktree path = {mainWorktree}/.claude/cat/worktrees/{issue-id}
            String fileName = lockFile.getFileName().toString();
            String issueId = fileName.substring(0, fileName.length() - 5); // Remove ".lock"
            Path worktreePath = mainWorktree.resolve(".claude/cat/worktrees/" + issueId);
            if (Files.isDirectory(worktreePath))
              paths.add(worktreePath.toRealPath());
          }
        }
      }
    }

    // 2. Current session's CWD
    Path cwdPath = Paths.get(workingDirectory);
    if (Files.exists(cwdPath))
      paths.add(cwdPath.toRealPath());

    return paths;
  }

  /**
   * Reads and parses a lock file as JSON.
   *
   * @param lockFile the lock file to parse
   * @return the parsed JSON node, or null if the file cannot be parsed
   */
  private JsonNode parseLockFile(Path lockFile)
  {
    try
    {
      String content = Files.readString(lockFile);
      return jsonMapper.readTree(content);
    }
    catch (IOException _)
    {
      return null;
    }
  }

  /**
   * Checks if a lock file is owned by the given session.
   *
   * @param lockFile the lock file path
   * @param sessionId the session ID to check against
   * @return true if the lock is owned by the given session
   */
  private boolean isOwnedBySession(Path lockFile, String sessionId)
  {
    if (sessionId.isEmpty())
      return false;
    JsonNode lock = parseLockFile(lockFile);
    if (lock == null)
      return false;
    JsonNode lockSessionNode = lock.get("session_id");
    if (lockSessionNode == null || !lockSessionNode.isString())
      return false;
    return sessionId.equals(lockSessionNode.asString());
  }

  /**
   * Checks if a lock file is stale (older than the staleness threshold).
   *
   * @param lockFile the lock file path
   * @return true if the lock is stale and should not be treated as active protection
   */
  private boolean isStale(Path lockFile)
  {
    JsonNode lock = parseLockFile(lockFile);
    if (lock == null)
      return false;
    JsonNode createdAtNode = lock.get("created_at");
    if (createdAtNode == null || !createdAtNode.isNumber())
      return false;
    long createdAtEpoch = createdAtNode.asLong();
    if (createdAtEpoch <= 0)
      return false;
    Instant createdAt = Instant.ofEpochSecond(createdAtEpoch);
    Duration age = Duration.between(createdAt, clock.instant());
    return age.compareTo(STALE_LOCK_THRESHOLD) > 0;
  }

  /**
   * Finds the main worktree root by walking up from cwd.
   * <p>
   * A .git directory (not file) indicates the main worktree.
   * A .git file indicates a sub-worktree.
   *
   * @param workingDirectory the starting directory
   * @return the main worktree root, or null if not found
   */
  private Path findMainWorktree(String workingDirectory)
  {
    Path current = Paths.get(workingDirectory);
    while (current != null)
    {
      Path gitPath = current.resolve(".git");
      if (Files.isDirectory(gitPath))
        return current;
      current = current.getParent();
    }
    return null;
  }

  /**
   * Resolves a path (absolute or relative) against a base directory.
   *
   * @param path the path to resolve
   * @param base the base directory for relative paths
   * @return the resolved absolute path
   */
  private Path resolvePath(String path, String base)
  {
    path = path.replaceAll("['\"]", "").strip();
    Path p = Paths.get(path);
    if (p.isAbsolute())
      return p.normalize();
    return Paths.get(base, path).normalize();
  }

  /**
   * Checks if a path is inside a parent directory or equal to it.
   *
   * @param path the path to check (must be resolved via toRealPath)
   * @param parent the potential parent directory (must be normalized)
   * @return true if path is inside parent or equal to it
   */
  private boolean isInsideOrEqual(Path path, Path parent)
  {
    assert that(path, "path").isNotNull().elseThrow();
    assert that(parent, "parent").isNotNull().elseThrow();
    return path.equals(parent) || path.startsWith(parent);
  }
}
