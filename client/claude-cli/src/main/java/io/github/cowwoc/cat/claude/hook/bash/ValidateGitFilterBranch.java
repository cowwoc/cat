/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.ShellParser;

import java.util.List;
import java.util.Locale;

/**
 * Validate git filter-branch and history-rewriting commands.
 * <p>
 * Prevents use of --all or --branches flags that would rewrite protected branches.
 */
public final class ValidateGitFilterBranch implements BashHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new handler for validating git filter-branch commands.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public ValidateGitFilterBranch(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check()
  {
    for (String command : GitCommandNormalizer.extractNormalizedGitCommands(scope.getCommand()))
    {
      List<String> tokens = ShellParser.tokenize(command);
      if (tokens.size() < 3)
        continue;
      String subcommand = tokens.get(1).toLowerCase(Locale.ROOT);
      if (!subcommand.equals("filter-branch") && !subcommand.equals("rebase"))
        continue;
      boolean dangerousFlag = false;
      for (int i = 2; i < tokens.size(); ++i)
      {
        String token = tokens.get(i);
        if (token.equals("--all") || token.equals("--branches"))
        {
          dangerousFlag = true;
          break;
        }
      }

      // BLOCK: dangerous --all or --branches flags with history rewriting
      if (dangerousFlag)
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
    }

    return Result.allow();
  }
}
