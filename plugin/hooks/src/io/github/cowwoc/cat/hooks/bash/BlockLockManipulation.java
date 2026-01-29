package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Block direct manipulation of CAT lock files.
 *
 * <p>M096: Agent deleted lock files without user permission.</p>
 */
public final class BlockLockManipulation implements BashHandler
{
  private static final Pattern LOCK_FILE_PATTERN =
    Pattern.compile("rm\\s+(-[frivI]+\\s+)*.*\\.claude/cat/locks");
  private static final Pattern LOCKS_DIR_PATTERN =
    Pattern.compile("rm\\s+(-[frivI]+\\s+)*.*\\.claude/cat/locks/?(\\s|$|\")");

  /**
   * Creates a new handler for blocking lock file manipulation.
   */
  public BlockLockManipulation()
  {
    // Handler class
  }

  @Override
  @SuppressWarnings("UnusedVariable")
  public Result check(String command, JsonNode _toolInput, JsonNode _toolResult, String _sessionId)
  {
    // Check for rm commands targeting lock files
    if (LOCK_FILE_PATTERN.matcher(command).find())
    {
      return Result.block("""
        BLOCKED: Direct deletion of lock files is not allowed.

        Lock files exist to prevent concurrent task execution. Deleting them directly
        bypasses safety checks and could cause:
        - Concurrent execution of the same task
        - Merge conflicts
        - Duplicate work
        - Data corruption

        CORRECT ACTIONS when encountering a lock:
        1. Execute a DIFFERENT task instead (use /cat:status to find available tasks)
        2. If you believe the lock is from a crashed session, ask the USER to run /cat:cleanup

        NEVER delete lock files directly.""");
    }

    // Also block force removal of the entire locks directory
    if (LOCKS_DIR_PATTERN.matcher(command).find())
    {
      return Result.block("BLOCKED: Cannot remove the locks directory. " +
        "Use /cat:cleanup to safely remove stale locks.");
    }

    return Result.allow();
  }
}
