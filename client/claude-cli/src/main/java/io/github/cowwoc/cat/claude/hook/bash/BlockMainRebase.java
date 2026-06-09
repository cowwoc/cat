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
import io.github.cowwoc.cat.tool.util.WorktreeContext;
import io.github.cowwoc.cat.tool.util.GitCommands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block git rebase on main branch and checkout changes in main worktree.
 * <p>
 * M205: Block ANY checkout in main worktree.
 */
public final class BlockMainRebase implements BashHandler
{
  private static final Pattern CHECKOUT_PATTERN =
    Pattern.compile("(^|[;&|\\n\\r])\\s*git\\s+.*\\b(checkout|switch)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHECKOUT_TARGET_PATTERN =
    Pattern.compile("git\\s+" +
      "(?:(?:-C\\s+\\S+|--git-dir(?:=|\\s+)\\S+|--work-tree(?:=|\\s+)\\S+)\\s+)*" +
      "(?:checkout|switch)\\s+([^\\s;&|\\n\\r]+)");
  private static final Pattern REBASE_PATTERN =
    Pattern.compile("(^|[;&|\\n\\r])\\s*git\\s+.*\\brebase\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern CD_TARGET_PATTERN =
    Pattern.compile("^cd\\s+['\"]?([^'\";&|\\n\\r]+)['\"]?");

  private final ClaudeHook scope;
  private final Path projectPath;
  private final Pattern cdProjectPattern;

  /**
   * Creates a new handler for blocking main branch rebase.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockMainRebase(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.projectPath = scope.getProjectPath();
    String escaped = Pattern.quote(projectPath.toString());
    this.cdProjectPattern =
      Pattern.compile("cd\\s+(" + escaped + "|['\"]+" + escaped + "['\"]*)([\\s]|&&|;|$)");
  }

  @Override
  public Result check()
  {
    String command = scope.getCommand();
    String commandLower = GitCommands.toLowerCase(command);
    String sessionId = scope.getSessionId();

    // Check for git checkout/switch in main worktree
    if (CHECKOUT_PATTERN.matcher(commandLower).find())
    {
      Result checkoutResult = checkCheckoutInMainWorktree(command, sessionId);
      if (checkoutResult != null)
        return checkoutResult;
    }

    // Check for git rebase command
    if (!REBASE_PATTERN.matcher(commandLower).find())
      return Result.allow();

    // Check if rebasing on main
    String currentBranch = getCurrentBranch(command, sessionId);
    if (currentBranch == null)
    {
      return Result.warn(
        "⚠️ Branch detection failed while checking rebase safety.\n" +
        "Cannot determine if rebasing on a protected branch.\n" +
        "Proceeding without rebase branch check.");
    }
    if (currentBranch.equals("main"))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      return Result.block(String.format("""
        REBASE ON MAIN BLOCKED

        Attempted: git rebase on main branch
        Correct:   Main branch should never be rebased

        WHY THIS IS BLOCKED:
        - Rebasing main rewrites commit history
        - Merged commits get recreated as direct commits
        - This breaks the audit trail

        TO REBASE AN ISSUE BRANCH ONTO MAIN:
        Run from your issue's worktree, not main:

          cd %s/<issue-branch>
          git rebase main""", worktreesDir));
    }

    return Result.allow();
  }

  /**
   * Checks if a checkout command is targeting the main worktree.
   *
   * @param command the bash command
   * @param sessionId the session ID for worktree context lookup
   * @return a block result if checkout in main worktree detected, null otherwise
   */
  private Result checkCheckoutInMainWorktree(String command, String sessionId)
  {
    // Check if command cd's to the project directory
    if (cdProjectPattern.matcher(command).find())
    {
      String target = extractCheckoutTarget(command);
      if (!isCheckoutFlag(target))
      {
        Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
        return Result.block(String.format("""
          GIT CHECKOUT IN MAIN WORKTREE BLOCKED

          Attempted: git checkout %s in main worktree
          Correct:   Use task worktrees - never change main worktree's branch

          WHY THIS IS BLOCKED:
          - The main worktree (%s) should keep its current branch
          - Issue worktrees exist precisely to avoid touching main workspace state
          - Changing main worktree's branch disrupts operations

          WHAT TO DO INSTEAD:
          - For issue work: Use the issue worktree at %s/<branch>
          - For cleanup: Delete the worktree directory, don't checkout in main""",
          target, projectPath, worktreesDir));
      }
    }

    Optional<WorktreeContext> context = WorktreeContext.forSession(
      scope.getCatWorkPath(), projectPath, scope.getJsonMapper(), sessionId);
    // If git command explicitly scopes to main worktree via -C/--git-dir/--work-tree, block checkout.
    Path scopedDir = resolveExplicitGitScope(command);
    Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
    if (scopedDir != null && scopedDir.toAbsolutePath().normalize().startsWith(normalizedProjectPath))
    {
      String target = extractCheckoutTarget(command);
      if (!isCheckoutFlag(target))
        return Result.block(String.format(
          "Blocked: Cannot checkout '%s' in main worktree. Use issue worktrees instead.", target));
    }
    if (context.isPresent())
      return null;
    // No active worktree for this session — this is the main context; block checkout
    String target = extractCheckoutTarget(command);
    if (!isCheckoutFlag(target))
    {
      return Result.block(String.format(
        "Blocked: Cannot checkout '%s' in main worktree. Use issue worktrees instead.",
        target));
    }
    return null;
  }

  /**
   * Checks if the target is a checkout flag rather than a branch name.
   *
   * @param target the checkout target
   * @return true if target is a flag like -- or -b
   */
  private boolean isCheckoutFlag(String target)
  {
    return "--".equals(target) || "-b".equals(target) || "-B".equals(target);
  }

  /**
   * Extracts the checkout target from a git checkout/switch command.
   *
   * @param command the bash command
   * @return the checkout target, or "unknown" if not found
   */
  private String extractCheckoutTarget(String command)
  {
    Matcher matcher = CHECKOUT_TARGET_PATTERN.matcher(command);
    if (matcher.find())
      return matcher.group(1);
    return "unknown";
  }

  /**
   * Determines the current branch for the command's target directory.
   *
   * @param command the bash command (may contain cd to another directory)
   * @param sessionId the session ID for worktree context lookup
   * @return the branch name, or {@code null} if branch detection failed
   */
  private String getCurrentBranch(String command, String sessionId)
  {
    // Check if command cd's to the project directory
    if (cdProjectPattern.matcher(command).find())
      return "main";

    Path scopedDir = resolveExplicitGitScope(command);
    if (scopedDir != null)
    {
      try
      {
        return GitCommands.getCurrentBranch(scopedDir);
      }
      catch (IllegalArgumentException | IOException _)
      {
        return null;
      }
    }

    // Check if command cd's elsewhere
    Matcher cdMatcher = CD_TARGET_PATTERN.matcher(command);
    if (cdMatcher.find())
    {
      Path targetDir = Path.of(cdMatcher.group(1).strip());
      try
      {
        return GitCommands.getCurrentBranch(targetDir);
      }
      catch (IllegalArgumentException | IOException _)
      {
        return null;
      }
    }

    // Use lock-based worktree context to determine branch
    Path branchDir = WorktreeContext.forSession(
        scope.getCatWorkPath(), projectPath, scope.getJsonMapper(), sessionId).
      map(WorktreeContext::absoluteWorktreePath).
      orElse(projectPath);
    try
    {
      return GitCommands.getCurrentBranch(branchDir);
    }
    catch (IOException _)
    {
      return null;
    }
  }

  /**
   * Resolves explicit git scope override from command, if present.
   *
   * @param command raw shell command
   * @return normalized explicit working tree, or {@code null} if command keeps implicit scope
   */
  private Path resolveExplicitGitScope(String command)
  {
    GitCommandScopeResolver.GitScopeTarget target = GitCommandScopeResolver.resolve(command, scope.getWorkDir());
    if (!target.overridesScope())
      return null;
    return target.workingTree().toAbsolutePath().normalize();
  }
}
