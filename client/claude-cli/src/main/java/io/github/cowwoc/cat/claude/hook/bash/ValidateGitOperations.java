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
import io.github.cowwoc.cat.tool.util.WorktreeContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Validate dangerous git operations.
 * <p>
 * Warns or blocks commands like git push --force, reset --hard, etc.
 */
public final class ValidateGitOperations implements BashHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new handler for validating dangerous git operations.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public ValidateGitOperations(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check()
  {
    for (CommandContext context : parseCommandContexts(scope.getCommand()))
    {
      String normalized = context.normalizedCommand();
      String lower = normalized.toLowerCase(Locale.ROOT);
      if (isForcePushToProtectedBranch(lower))
        return blockForcePush();
      if (!isResetHardCommand(lower))
        continue;
      if (GitCommandNormalizer.containsAcknowledgedComment(context.rawSegment()))
        continue;
      if (isWorktreeDirectory(context.baseDirectory()) && !hasExplicitScopeOverride(context.rawSegment()))
        continue;
      if (isResetHardAllowed(context))
        continue;
      return blockResetHard();
    }
    return Result.allow();
  }

  /**
   * Detects force-push attempts targeting protected mainline branches.
   *
   * @param normalizedLower normalized git command in lowercase
   * @return {@code true} if command force-pushes to {@code main} or {@code master}
   */
  private boolean isForcePushToProtectedBranch(String normalizedLower)
  {
    if (!(normalizedLower.startsWith("git push ") || normalizedLower.equals("git push")))
      return false;
    if (!normalizedLower.contains(" --force ") &&
      !normalizedLower.endsWith(" --force") &&
      !normalizedLower.contains(" -f ") &&
      !normalizedLower.endsWith(" -f"))
    {
      return false;
    }
    // `--force-with-lease` is explicitly allowed.
    return !normalizedLower.contains("--force-with-lease") &&
      normalizedLower.matches(".*(\\s|:)(main|master)(\\s|$).*");
  }

  /**
   * Builds blocking result for protected-branch force push.
   *
   * @return blocking hook result
   */
  private Result blockForcePush()
  {
    return Result.block("""
      **BLOCKED: Force push to main/master**

      Force pushing to main/master rewrites shared history and can cause:
      - Lost commits from other contributors
      - Broken references
      - Confused collaborators

      Use --force-with-lease instead, or ask the user if they really want this.""");
  }

  /**
   * Detects hard reset command.
   *
   * @param normalizedLower normalized git command in lowercase
   * @return {@code true} if command starts with {@code git reset --hard}
   */
  private boolean isResetHardCommand(String normalizedLower)
  {
    return normalizedLower.startsWith("git reset --hard");
  }

  /**
   * Builds blocking result for unsafe hard reset.
   *
   * @return blocking hook result
   */
  private Result blockResetHard()
  {
    return Result.block("""
      **BLOCKED: git reset --hard can lose uncommitted work**

      This command discards all uncommitted changes permanently.

      If you're sure:
      - In a worktree: Use /cat:git-rebase skill
      - Main worktree: Add # ACKNOWLEDGED comment

      Consider: git stash to save work before reset.""");
  }

  /**
   * Indicates whether hard reset stays within allowed worktree scope.
   *
   * @param context parsed command context
   * @return {@code true} if reset applies only to current issue worktree
   */
  private boolean isResetHardAllowed(CommandContext context)
  {
    GitCommandScopeResolver.GitScopeTarget target = GitCommandScopeResolver.resolve(context.rawSegment(),
      context.baseDirectory());
    Optional<WorktreeContext> worktreeContext = WorktreeContext.forSession(scope.getCatWorkPath(),
      scope.getProjectPath(), scope.getJsonMapper(), scope.getSessionId());
    if (worktreeContext.isEmpty())
      return false;
    if (!target.overridesScope())
      return true;
    Path worktreePath = worktreeContext.get().absoluteWorktreePath().toAbsolutePath().normalize();
    Path workingTree = target.workingTree().toAbsolutePath().normalize();
    if (target.overridesScope() && !workingTree.startsWith(worktreePath))
      return false;
    Path gitDirectory = target.gitDirectory();
    if (target.overridesScope() && gitDirectory != null &&
      !gitDirectory.toAbsolutePath().normalize().startsWith(worktreePath))
    {
      return false;
    }
    return workingTree.startsWith(worktreePath);
  }

  /**
   * Detects CAT worktree directory path by naming convention.
   *
   * @param path candidate path
   * @return {@code true} if path looks like nested worktrees directory
   */
  private boolean isWorktreeDirectory(Path path)
  {
    String normalized = path.toAbsolutePath().normalize().toString();
    return normalized.contains("/worktrees/") || normalized.contains("\\worktrees\\");
  }

  /**
   * Detects explicit git scope flags such as {@code -C}, {@code --work-tree}, or
   * {@code --git-dir}.
   *
   * @param rawSegment raw shell segment
   * @return {@code true} if command overrides implicit directory scope
   */
  private boolean hasExplicitScopeOverride(String rawSegment)
  {
    List<String> tokens = ShellParser.tokenize(rawSegment);
    for (String token : tokens)
    {
      if (token.startsWith("-C") && token.length() > 2)
        return true;
      if ("-C".equals(token) || token.startsWith("--work-tree=") || token.startsWith("--git-dir="))
        return true;
      if ("--work-tree".equals(token) || "--git-dir".equals(token))
        return true;
    }
    return false;
  }

  /**
   * Parses shell command into git-relevant execution contexts.
   *
   * @param command raw shell command
   * @return parsed command contexts
   */
  private List<CommandContext> parseCommandContexts(String command)
  {
    List<CommandContext> contexts = new ArrayList<>();
    Path currentBase = scope.getWorkDir().toAbsolutePath().normalize();
    for (String segment : splitSegments(command))
    {
      List<String> tokens = ShellParser.tokenize(segment);
      if (tokens.isEmpty())
        continue;
      if ("cd".equals(tokens.getFirst()) && tokens.size() > 1)
      {
        currentBase = ShellParser.resolvePath(tokens.get(1), currentBase).toAbsolutePath().normalize();
        continue;
      }
      int gitIndex = GitCommandNormalizer.findGitTokenIndex(tokens);
      if (gitIndex < 0)
        continue;
      List<GitCommandNormalizer.NormalizedGitCommand> commands = GitCommandNormalizer.extractGitCommands(segment);
      if (commands.isEmpty())
        continue;
      String normalized = commands.getFirst().normalizedCommand();
      contexts.add(new CommandContext(segment, normalized, currentBase));
    }
    return contexts;
  }

  /**
   * Splits shell command into sequential segments on top-level separators.
   *
   * @param command raw shell command
   * @return stripped command segments
   */
  private List<String> splitSegments(String command)
  {
    List<String> segments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < command.length(); ++i)
    {
      char c = command.charAt(i);
      if (c == '\'' && !inDoubleQuote)
      {
        inSingleQuote = !inSingleQuote;
        current.append(c);
        continue;
      }
      if (c == '"' && !inSingleQuote)
      {
        inDoubleQuote = !inDoubleQuote;
        current.append(c);
        continue;
      }
      if (!inSingleQuote && !inDoubleQuote)
      {
        if (c == '\n' || c == '\r')
        {
          addSegment(segments, current);
          continue;
        }
        if (c == ';' || c == '|')
        {
          addSegment(segments, current);
          continue;
        }
        if (c == '&')
        {
          addSegment(segments, current);
          if (i + 1 < command.length() && command.charAt(i + 1) == '&')
            ++i;
          continue;
        }
      }
      current.append(c);
    }
    addSegment(segments, current);
    return segments;
  }

  /**
   * Flushes current parsed shell segment into destination list.
   *
   * @param segments destination segment list
   * @param current current mutable segment buffer
   */
  private void addSegment(List<String> segments, StringBuilder current)
  {
    String segment = current.toString().strip();
    if (!segment.isEmpty())
      segments.add(segment);
    current.setLength(0);
  }

  private record CommandContext(String rawSegment, String normalizedCommand, Path baseDirectory)
  {
  }
}
