/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ShellParser;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block rm -rf and git worktree remove when deletion would affect protected paths.
 * <p>
 * M464, M491: Prevent shell session corruption from deleting current working directory
 * or other active worktrees.
 * <p>
 * Protection strategy: Build a map of protected paths (main worktree, working directory,
 * locked worktrees) and block deletion if any protected path is inside or equal to
 * the deletion target. Each protected path carries a reason used to produce an
 * actionable error message.
 */
public final class BlockUnsafeRemoval implements BashHandler
{
  private static final Pattern WORKTREE_REMOVE_PATTERN =
    Pattern.compile("\\bgit\\s+worktree\\s+remove\\s+(?:-[^\\s]+\\s+)*([^\\s;&|]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern RECURSIVE_FLAG_PATTERN = Pattern.compile("(?:^|\\s)-[^\\s]*[rR]");
  private static final Pattern CAT_AGENT_ID_PATTERN =
    Pattern.compile("^CAT_AGENT_ID=(\\S+)\\s+(.*)$", Pattern.DOTALL);

  /**
   * The reason a path is protected.
   */
  private enum ProtectionReason
  {
    /**
     * The current working directory is inside or equal to the target — shell corruption risk.
     */
    CURRENT_WORKING_DIRECTORY,
    /**
     * Worktree locked by a different agent (known agent_id in command but doesn't match lock).
     */
    LOCKED_BY_OTHER_AGENT,
    /**
     * Worktree locked but no CAT_AGENT_ID in command (fail-safe).
     */
    UNKNOWN_AGENT,
    /**
     * Target is the main git worktree root.
     */
    MAIN_WORKTREE
  }

  /**
   * Parsed CAT agent ID prefix result.
   *
   * @param catAgentId the extracted CAT agent ID, or empty string if not present
   * @param rawCommand the command without the CAT_AGENT_ID prefix
   */
  private record CatAgentIdParse(String catAgentId, String rawCommand)
  {
    /**
     * Compact constructor for CatAgentIdParse.
     *
     * @param catAgentId the extracted CAT agent ID, or empty string if not present
     * @param rawCommand the command without the CAT_AGENT_ID prefix
     * @throws NullPointerException if {@code catAgentId} or {@code rawCommand} are null
     */
    CatAgentIdParse
    {
      assert that(catAgentId, "catAgentId").isNotNull().elseThrow();
      assert that(rawCommand, "rawCommand").isNotNull().elseThrow();
    }
  }

  private final Clock clock;
  private final JvmScope scope;

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
    this.scope = scope;
    this.clock = clock;
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    try
    {
      // Extract CAT_AGENT_ID prefix if present, using single regex parse
      CatAgentIdParse parse = parseCatAgentIdPrefix(command);
      String commandCatAgentId = parse.catAgentId;
      String rawCommand = parse.rawCommand;

      String commandLower = rawCommand.toLowerCase(Locale.ENGLISH);

      // Check rm -rf commands
      if (commandLower.contains("rm") && hasRecursiveFlag(rawCommand))
      {
        Result rmResult = checkRmCommand(rawCommand, workingDirectory, sessionId, commandCatAgentId);
        if (rmResult != null)
          return rmResult;
      }

      // Check git worktree remove commands
      if (commandLower.contains("git") && commandLower.contains("worktree") &&
          commandLower.contains("remove"))
      {
        Result worktreeResult = checkWorktreeRemove(rawCommand, workingDirectory, sessionId,
          commandCatAgentId);
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
   * Parses the CAT_AGENT_ID prefix from a command string, if present.
   * <p>
   * Runs the regex once to extract both the CAT agent ID and the raw command.
   * Looks for a prefix of the form {@code CAT_AGENT_ID=<value> <rest>}.
   *
   * @param command the bash command
   * @return parsed CAT agent ID and raw command (CAT agent ID is empty if not present)
   */
  private CatAgentIdParse parseCatAgentIdPrefix(String command)
  {
    Matcher matcher = CAT_AGENT_ID_PATTERN.matcher(command.strip());
    if (matcher.matches())
      return new CatAgentIdParse(matcher.group(1), matcher.group(2));
    return new CatAgentIdParse("", command);
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
    return RECURSIVE_FLAG_PATTERN.matcher(command).find() || command.contains("--recursive");
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
    List<String> tokens = ShellParser.tokenize(rmArgs);

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
   * Checks if an rm command would delete any protected path.
   *
   * @param command the bash command (without CAT_AGENT_ID prefix)
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @param commandCatAgentId the CAT_AGENT_ID extracted from the original command
   * @return a block result if unsafe removal detected, null otherwise
   * @throws IOException if path operations fail
   */
  private Result checkRmCommand(String command, String workingDirectory, String sessionId,
    String commandCatAgentId) throws IOException
  {
    List<String> targets = extractRmTargets(command);

    for (String target : targets)
    {
      Result blockResult = checkProtectedPaths(target, workingDirectory, sessionId, commandCatAgentId,
        "rm (recursive)");
      if (blockResult != null)
        return blockResult;
    }

    return null;
  }

  /**
   * Checks if a git worktree remove command would delete any protected path.
   *
   * @param command the bash command (without CAT_AGENT_ID prefix)
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @param commandCatAgentId the CAT_AGENT_ID extracted from the original command
   * @return a block result if unsafe removal detected, null otherwise
   * @throws IOException if path operations fail
   */
  private Result checkWorktreeRemove(String command, String workingDirectory, String sessionId,
    String commandCatAgentId) throws IOException
  {
    Matcher matcher = WORKTREE_REMOVE_PATTERN.matcher(command);

    while (matcher.find())
    {
      String target = matcher.group(1);
      if (target == null || target.isEmpty())
        continue;

      // Check if deletion would affect protected paths
      Result blockResult = checkProtectedPaths(target, workingDirectory, sessionId, commandCatAgentId,
        "git worktree remove");
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
   * @param commandCatAgentId the CAT_AGENT_ID from the command (empty if not provided)
   * @param commandType the command type for error messages
   * @return a block result if protected paths would be affected, null otherwise
   * @throws IOException if path operations fail
   */
  private Result checkProtectedPaths(String target, String workingDirectory, String sessionId,
    String commandCatAgentId, String commandType) throws IOException
  {
    Map<Path, ProtectionReason> protectedPaths = getProtectedPaths(workingDirectory, sessionId,
      commandCatAgentId);
    if (protectedPaths.isEmpty())
      return null;

    // Resolve target path, resolving symlinks when the path exists on disk so that symlink-based
    // path traversal cannot bypass the safety check. Fall back to the normalized path when the
    // target doesn't exist yet.
    Path normalizedPath = ShellParser.resolvePath(target, workingDirectory);
    Path targetPath;
    try
    {
      targetPath = normalizedPath.toRealPath();
    }
    catch (IOException _)
    {
      targetPath = normalizedPath;
    }

    // Check if any protected path is inside or equal to the target
    for (Map.Entry<Path, ProtectionReason> entry : protectedPaths.entrySet())
    {
      Path protectedPath = entry.getKey();
      ProtectionReason reason = entry.getValue();
      if (isInsideOrEqual(protectedPath, targetPath))
        return buildBlockResult(reason, commandType, target, targetPath, workingDirectory,
          commandCatAgentId, protectedPath);
    }

    return null;
  }

  /**
   * Builds a block result with a reason-specific error message.
   *
   * @param reason the protection reason
   * @param commandType the command type string for display
   * @param target the deletion target as provided in the command
   * @param targetPath the resolved target path
   * @param workingDirectory the shell's current working directory
   * @param commandCatAgentId the CAT_AGENT_ID from the command (empty if not provided)
   * @param protectedPath the protected path that caused the block
   * @return a block result with an appropriate error message
   * @throws IOException if lock file reading fails
   */
  private Result buildBlockResult(ProtectionReason reason, String commandType, String target,
    Path targetPath, String workingDirectory, String commandCatAgentId,
    Path protectedPath) throws IOException
  {
    String message = "";
    switch (reason)
    {
      case CURRENT_WORKING_DIRECTORY ->
      {
        message = """
          UNSAFE DIRECTORY REMOVAL BLOCKED

          Attempted: %s %s
          Problem:   Your shell's working directory is inside the deletion target
          Working directory: %s
          Target:    %s

          WHY THIS IS BLOCKED:
          - Deleting a directory containing your current location corrupts the shell session
          - All subsequent Bash commands will fail with "Exit code 1"

          WHAT TO DO:
          1. Change directory first: cd /workspace
          2. Then retry: %s %s
          """.formatted(commandType, target, workingDirectory, targetPath, commandType, target);
      }
      case MAIN_WORKTREE ->
      {
        message = """
          UNSAFE DIRECTORY REMOVAL BLOCKED

          Attempted: %s %s
          Problem:   Target is the main git worktree
          Target:    %s

          WHY THIS IS BLOCKED:
          - Deleting the main worktree would destroy the entire repository

          WHAT TO DO:
          - This operation is not allowed. Use a more specific target path.
          """.formatted(commandType, target, targetPath);
      }
      case LOCKED_BY_OTHER_AGENT, UNKNOWN_AGENT ->
      {
        String lockOwner = getLockOwner(protectedPath, targetPath);
        String yourId;
        if (commandCatAgentId.isBlank())
          yourId = "(not provided)";
        else
          yourId = commandCatAgentId;
        String issueId = getIssueIdFromWorktreePath(targetPath);
        message = """
          UNSAFE DIRECTORY REMOVAL BLOCKED

          Attempted: %s %s
          Problem:   Worktree is locked by another agent
          Lock owner: %s
          Your ID:    %s
          Target:     %s

          WHY THIS IS BLOCKED:
          - Another agent may be actively using this worktree
          - Deleting it could corrupt that agent's shell and lose uncommitted work

          WHAT TO DO:
          1. If you own this worktree, prefix your command:
             CAT_AGENT_ID=<your-agent-id> %s %s
          2. If another agent owns it, release the lock first:
             issue-lock force-release %s
          3. Or use /cat:cleanup to release all stale locks
          """.formatted(commandType, target, lockOwner, yourId, targetPath,
          commandType, target, issueId);
      }
    }
    return Result.block(message);
  }

  /**
   * Reads the lock owner (agent_id or session_id) for the lock file corresponding to a worktree path.
   *
   * @param protectedPath the protected worktree path (resolved)
   * @param targetPath the deletion target path (may differ from protectedPath in parent-target cases)
   * @return the agent_id if present, session_id as fallback, or "(unknown)" if unreadable
   */
  private String getLockOwner(Path protectedPath, Path targetPath)
  {
    // Find the lock file by locating the main worktree and looking up locks dir
    Path mainWorktree = findMainWorktreeFromPath(protectedPath);
    if (mainWorktree == null)
      mainWorktree = findMainWorktreeFromPath(targetPath);
    if (mainWorktree == null)
      return "(unknown)";

    Path locksDir = mainWorktree.resolve(".claude/cat/locks");
    // The lock file name is the issue ID: derive from worktree path's last segment
    String issueId = getIssueIdFromWorktreePath(protectedPath);
    if (issueId.isBlank())
      return "(unknown)";
    Path lockFile = locksDir.resolve(issueId + ".lock");
    JsonNode lock = parseLockFile(lockFile);
    if (lock == null)
      return "(unknown)";

    JsonNode worktreesNode = lock.get("worktrees");
    if (worktreesNode != null && worktreesNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : worktreesNode.properties())
      {
        JsonNode agentNode = entry.getValue();
        if (agentNode != null && agentNode.isString() && !agentNode.asString().isBlank())
          return agentNode.asString();
      }
    }

    JsonNode sessionNode = lock.get("session_id");
    if (sessionNode != null && sessionNode.isString())
      return sessionNode.asString();
    return "(unknown)";
  }

  /**
   * Derives the issue ID from a worktree path by taking the last path component.
   *
   * @param worktreePath the worktree path
   * @return the issue ID (last segment of path), or empty string if not determinable
   */
  private String getIssueIdFromWorktreePath(Path worktreePath)
  {
    Path fileName = worktreePath.getFileName();
    if (fileName == null)
      return "";
    return fileName.toString();
  }

  /**
   * Builds a map of protected paths → protection reasons.
   * <p>
   * Protected paths include:
   * <ul>
   *   <li>Main worktree root (derived from git structure) → MAIN_WORKTREE reason</li>
   *   <li>Current session's working directory (from hook input) → CURRENT_WORKING_DIRECTORY reason</li>
   *   <li>Locked worktrees owned by other agents → LOCKED_BY_OTHER_AGENT or UNKNOWN_AGENT</li>
   * </ul>
   * <p>
   * Lock ownership determination:
   * <ul>
   *   <li>Lock's worktrees map has agent IDs: match against commandCatAgentId; no match → LOCKED_BY_OTHER_AGENT</li>
   *   <li>Lock has agent IDs but no commandCatAgentId: fail-safe → UNKNOWN_AGENT</li>
   *   <li>Lock's worktrees map has no agent IDs: fall back to session_id comparison</li>
   * </ul>
   * Stale locks (older than 4 hours) are skipped. Locks owned by the current agent are skipped.
   *
   * @param workingDirectory the shell's current working directory
   * @param sessionId the current session ID
   * @param commandCatAgentId the CAT_AGENT_ID extracted from the command (empty if not provided)
   * @return map from protected path to protection reason
   * @throws IOException if path operations fail
   */
  private Map<Path, ProtectionReason> getProtectedPaths(String workingDirectory, String sessionId,
    String commandCatAgentId) throws IOException
  {
    // Use LinkedHashMap so the working directory check takes priority when it is inside the target.
    // The working directory entry is inserted first so it wins over lock entries in iteration order.
    // Per PLAN.md, working directory check blocks even if CAT_AGENT_ID matches.
    Map<Path, ProtectionReason> paths = new LinkedHashMap<>();

    if (workingDirectory.isEmpty())
      return paths;

    // 1. Current session's working directory (checked first — takes priority over lock reasons)
    Path cwdPath = Paths.get(workingDirectory);
    if (Files.exists(cwdPath))
      paths.put(cwdPath.toRealPath(), ProtectionReason.CURRENT_WORKING_DIRECTORY);

    // 2. Main worktree root and locked worktrees
    // Try to find main worktree from current working directory first, then from project directory
    Path mainWorktree = findMainWorktreeFromPath(Paths.get(workingDirectory));
    if (mainWorktree == null)
      mainWorktree = findMainWorktreeFromPath(scope.getClaudeProjectDir());
    if (mainWorktree != null)
    {
      paths.put(mainWorktree.toRealPath(), ProtectionReason.MAIN_WORKTREE);

      // 3. Locked worktrees owned by OTHER agents
      Path locksDir = mainWorktree.resolve(".claude/cat/locks");
      if (Files.isDirectory(locksDir))
      {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(locksDir, "*.lock"))
        {
          for (Path lockFile : stream)
          {
            // Skip stale locks (older than 4 hours) - they are from dead sessions
            if (IssueLock.isStale(lockFile, clock, scope.getJsonMapper()))
              continue;

            JsonNode lock = parseLockFile(lockFile);
            if (lock == null)
              continue;

            ProtectionReason lockReason = determineLockReason(lock, sessionId, commandCatAgentId);
            if (lockReason == null)
              continue;

            // Read worktree paths from the worktrees map
            JsonNode worktreesNode = lock.get("worktrees");
            if (worktreesNode == null || !worktreesNode.isObject())
              continue;
            for (Map.Entry<String, JsonNode> worktreeEntry : worktreesNode.properties())
            {
              Path worktreePath = Paths.get(worktreeEntry.getKey());
              if (Files.isDirectory(worktreePath))
              {
                Path resolvedWorktree = worktreePath.toRealPath();
                if (!paths.containsKey(resolvedWorktree))
                  paths.put(resolvedWorktree, lockReason);
              }
            }
          }
        }
      }
    }

    return paths;
  }

  /**
   * Determines the protection reason for a parsed lock, given the current agent context.
   * <p>
   * Returns {@code null} if the lock is owned by the current agent (allow).
   *
   * @param lock the parsed lock JSON node
   * @param sessionId the current session ID
   * @param commandCatAgentId the CAT_AGENT_ID from the command (empty if not provided)
   * @return the protection reason, or null if the lock is owned by the current agent
   */
  private ProtectionReason determineLockReason(JsonNode lock, String sessionId,
    String commandCatAgentId)
  {
    // Check worktrees map values for agent ownership
    JsonNode worktreesNode = lock.get("worktrees");
    if (worktreesNode != null && worktreesNode.isObject())
    {
      boolean hasAgentId = false;
      for (Map.Entry<String, JsonNode> entry : worktreesNode.properties())
      {
        JsonNode agentNode = entry.getValue();
        if (agentNode != null && agentNode.isString() && !agentNode.asString().isBlank())
        {
          hasAgentId = true;
          if (!commandCatAgentId.isBlank() && commandCatAgentId.equals(agentNode.asString()))
            return null; // Owner — allow
        }
      }
      if (hasAgentId)
      {
        if (commandCatAgentId.isBlank())
          return ProtectionReason.UNKNOWN_AGENT;
        return ProtectionReason.LOCKED_BY_OTHER_AGENT;
      }
    }

    // No agent_id in worktrees: fall back to session_id comparison
    JsonNode lockSessionNode = lock.get("session_id");
    if (lockSessionNode != null && lockSessionNode.isString())
    {
      String lockSessionId = lockSessionNode.asString();
      if (!sessionId.isBlank() && sessionId.equals(lockSessionId))
        return null; // Same session — allow
      return ProtectionReason.LOCKED_BY_OTHER_AGENT;
    }

    return ProtectionReason.UNKNOWN_AGENT;
  }

  /**
   * Reads and parses a lock file as JSON.
   *
   * @param lockFile the lock file to parse
   * @return the parsed JSON node, or null if the file cannot be parsed
   */
  private JsonNode parseLockFile(Path lockFile)
  {
    return IssueLock.parseLockFile(lockFile, scope.getJsonMapper());
  }

  /**
   * Finds the main worktree root by walking up from a given path.
   * <p>
   * A .git directory (not file) indicates the main worktree.
   * A .git file indicates a sub-worktree.
   *
   * @param start the starting path
   * @return the main worktree root, or null if not found
   */
  private Path findMainWorktreeFromPath(Path start)
  {
    Path current = start;
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
