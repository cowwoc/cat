package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Validate git filter-branch and history-rewriting commands.
 *
 * <p>Prevents use of --all or --branches flags that would rewrite protected branches.</p>
 */
public final class ValidateGitFilterBranch implements BashHandler
{
  /**
   * Creates a new handler for validating git filter-branch commands.
   */
  public ValidateGitFilterBranch()
  {
    // Handler class
  }

  private static final Pattern DANGEROUS_FLAGS_PATTERN =
    Pattern.compile("(^|;|&&|\\|)\\s*git\\s+(filter-branch|rebase)\\s+.*\\s+--(all|branches)(\\s|$)");

  @Override
  @SuppressWarnings("UnusedVariable")
  public Result check(String command, JsonNode _toolInput, JsonNode _toolResult, String _sessionId)
  {
    // BLOCK: dangerous --all or --branches flags with history rewriting
    if (DANGEROUS_FLAGS_PATTERN.matcher(command).find())
    {
      return Result.block("""
        CRITICAL: DANGEROUS GIT HISTORY REWRITING DETECTED

        **Blocked command**: git filter-branch/rebase with --all or --branches

        This would rewrite history on ALL branches including:
        - Version branches (v1.0, v2.0, etc.)
        - Release branches
        - Other protected branches

        **WHAT TO DO INSTEAD:**
        1. Target specific branches explicitly:
           git filter-branch --tree-filter 'command' main feature-branch

        2. Use git-filter-repo with explicit refs:
           git filter-repo --refs main --refs feature-branch

        **See**: /cat:git-rewrite-history skill for proper usage""");
    }

    return Result.allow();
  }
}
