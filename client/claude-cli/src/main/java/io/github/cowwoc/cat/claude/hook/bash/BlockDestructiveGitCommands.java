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
import java.util.regex.Pattern;

/**
 * Blocks destructive git commands even when prefixed by global git flags.
 */
public final class BlockDestructiveGitCommands implements BashHandler
{
  private static final Pattern PROTECTED_BRANCH_PATTERN =
    Pattern.compile("^(?:main|master|v[0-9]+(?:\\.[0-9]+)*)$");

  private final ClaudeHook scope;

  /**
   * Creates a new handler.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockDestructiveGitCommands(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check()
  {
    for (GitCommandNormalizer.NormalizedGitCommand normalizedCommand :
      GitCommandNormalizer.extractGitCommands(scope.getCommand()))
    {
      String command = normalizedCommand.normalizedCommand();
      List<String> tokens = ShellParser.tokenize(command);
      if (tokens.size() < 2)
        continue;
      if (GitCommandNormalizer.containsAcknowledgedComment(normalizedCommand.rawSegment()))
        continue;
      if (isForcePush(tokens))
      {
        return Result.block("""
          **BLOCKED: Force push rewrites branch history**

          Use `git push --force-with-lease` when you must force-update safely.""");
      }
      if (isProtectedBranchForceDelete(tokens))
      {
        return Result.block("""
          **BLOCKED: Protected/version branch force-delete**

          Force-deleting `main`, `master`, or `v*` branches is not allowed.""");
      }
      if (isCheckoutDiscardAll(tokens) || isRestoreDiscardAll(tokens))
      {
        return Result.block("""
          **BLOCKED: Full-tree discard detected**

          This command discards changes across the entire tree.
          Limit the operation to explicit files instead of `.`.""");
      }
      if (isForceClean(tokens))
      {
        return Result.block("""
          **BLOCKED: Forced clean removes untracked files permanently**

          Use `git clean -n` first to preview what would be removed.""");
      }
      if (isDestructiveStash(tokens))
      {
        return Result.block("""
          **BLOCKED: Destructive stash command**

          Avoid `stash clear` / `stash drop --all` because recovery is difficult.""");
      }
    }
    return Result.allow();
  }

  /**
   * Returns true if command is {@code git push --force} or {@code git push -f}.
   *
   * @param tokens normalized git command tokens
   * @return {@code true} if force push is detected
   */
  private boolean isForcePush(List<String> tokens)
  {
    if (!tokens.get(1).equalsIgnoreCase("push"))
      return false;
    for (int i = 2; i < tokens.size(); ++i)
    {
      String token = tokens.get(i);
      if (token.equals("--force") || token.equals("-f") || token.equals("--force=true"))
        return true;
      if (token.startsWith("-") && !token.startsWith("--") && token.substring(1).contains("f"))
        return true;
    }
    return false;
  }

  /**
   * Returns true if command force-deletes a protected/version branch.
   *
   * @param tokens normalized git command tokens
   * @return {@code true} if a protected branch force-delete is detected
   */
  private boolean isProtectedBranchForceDelete(List<String> tokens)
  {
    if (!tokens.get(1).equalsIgnoreCase("branch"))
      return false;
    boolean delete = false;
    boolean force = false;
    boolean endOfOptions = false;
    for (int i = 2; i < tokens.size(); ++i)
    {
      String token = tokens.get(i);
      if (!endOfOptions && token.equals("--"))
      {
        endOfOptions = true;
        continue;
      }
      if (!endOfOptions && token.startsWith("-"))
      {
        if (token.equals("-D"))
        {
          delete = true;
          force = true;
        }
        else if (token.equals("-d") || token.equals("--delete"))
          delete = true;
        else if (token.equals("-f") || token.equals("--force"))
          force = true;
        else if (!token.startsWith("--"))
        {
          String shortOptions = token.substring(1);
          if (shortOptions.contains("D"))
          {
            delete = true;
            force = true;
          }
          if (shortOptions.contains("d"))
            delete = true;
          if (shortOptions.contains("f"))
            force = true;
        }
        continue;
      }
      if (delete && force)
      {
        String branch = token.toLowerCase(Locale.ROOT);
        if (PROTECTED_BRANCH_PATTERN.matcher(branch).matches())
          return true;
      }
    }
    return false;
  }

  /**
   * Returns true if command is {@code git checkout .} or {@code git checkout -- .}.
   *
   * @param tokens normalized git command tokens
   * @return {@code true} if checkout discards the full tree
   */
  private boolean isCheckoutDiscardAll(List<String> tokens)
  {
    if (!tokens.get(1).equalsIgnoreCase("checkout"))
      return false;
    for (int i = 2; i < tokens.size(); ++i)
    {
      if (tokens.get(i).equals("."))
        return true;
    }
    return false;
  }

  /**
   * Returns true if restore targets the full tree with {@code .}.
   *
   * @param tokens normalized git command tokens
   * @return {@code true} if restore targets the full tree
   */
  private boolean isRestoreDiscardAll(List<String> tokens)
  {
    if (!tokens.get(1).equalsIgnoreCase("restore"))
      return false;
    boolean endOfOptions = false;
    for (int i = 2; i < tokens.size(); ++i)
    {
      String token = tokens.get(i);
      if (!endOfOptions && token.equals("--"))
      {
        endOfOptions = true;
        continue;
      }
      if (!endOfOptions && token.startsWith("-"))
        continue;
      if (token.equals("."))
        return true;
    }
    return false;
  }

  /**
   * Returns true if clean includes a force flag ({@code -f}, {@code -fd}, etc.).
   *
   * @param tokens normalized git command tokens
   * @return {@code true} if force clean is detected
   */
  private boolean isForceClean(List<String> tokens)
  {
    if (!tokens.get(1).equalsIgnoreCase("clean"))
      return false;
    for (int i = 2; i < tokens.size(); ++i)
    {
      String token = tokens.get(i);
      if (token.equals("--"))
        break;
      if (token.equals("--force"))
        return true;
      if (token.startsWith("-") && token.substring(1).contains("f"))
        return true;
    }
    return false;
  }

  /**
   * Returns true for {@code git stash clear} and {@code git stash drop --all}.
   *
   * @param tokens normalized git command tokens
   * @return {@code true} if destructive stash operation is detected
   */
  private boolean isDestructiveStash(List<String> tokens)
  {
    if (tokens.size() < 3)
      return false;
    if (!tokens.get(1).equalsIgnoreCase("stash"))
      return false;
    String subcommand = tokens.get(2).toLowerCase(Locale.ROOT);
    if (subcommand.equals("clear"))
      return true;
    if (!subcommand.equals("drop"))
      return false;
    for (int i = 3; i < tokens.size(); ++i)
    {
      if (tokens.get(i).equals("--all"))
        return true;
    }
    return false;
  }
}
