/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Warn when editing source files directly (A003/M097/M220/M302/M442).
 * <p>
 * CAT workflow requires:
 * <ol>
 *   <li>Issue work happens in isolated worktrees</li>
 *   <li>Main agent delegates source edits to subagents (A003/M097)</li>
 * </ol>
 * <p>
 * This hook warns when editing source files outside proper workflow.
 * <p>
 * Allowed without warning:
 * <ul>
 *   <li>.claude/cat/, .claude/rules/, .claude/settings* (orchestration only, not commands/client)</li>
 *   <li>STATE.md, PLAN.md, CHANGELOG.md, ROADMAP.md files</li>
 *   <li>CLAUDE.md, PROJECT.md (project instructions)</li>
 *   <li>retrospectives/ directory</li>
 *   <li>mistakes.json, retrospectives.json</li>
 *   <li>client/, skills/ directories (only for existing files)</li>
 *   <li>When in a issue worktree editing orchestration files only</li>
 * </ul>
 */
public final class WarnBaseBranchEdit implements FileWriteHandler
{
  private static final List<String> ALLOWED_PATTERNS = List.of(
    ".claude/settings.json",
    ".claude/settings.local.json",
    ".claude/cat/",
    ".claude/rules/",
    "STATE.md",
    "PLAN.md",
    "CHANGELOG.md",
    "ROADMAP.md",
    "CLAUDE.md",
    "PROJECT.md",
    "retrospectives/",
    "mistakes.json",
    "mistakes-",
    "retrospectives.json",
    "retrospectives-",
    "index.json");

  private final JvmScope scope;

  /**
   * Creates a new WarnBaseBranchEdit instance.
   *
   * @param scope the JVM scope providing project directory configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public WarnBaseBranchEdit(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Check if the edit should be warned about.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if toolInput or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    JsonNode filePathNode = toolInput.get("file_path");
    String filePath;
    if (filePathNode != null)
      filePath = filePathNode.asString();
    else
      filePath = "";

    if (filePath.isEmpty())
      return FileWriteHandler.Result.allow();

    for (String pattern : ALLOWED_PATTERNS)
    {
      if (filePath.contains(pattern))
        return FileWriteHandler.Result.allow();
    }

    if (filePath.contains("client/") || filePath.contains("skills/"))
    {
      Path path = Path.of(filePath);
      if (Files.exists(path))
        return FileWriteHandler.Result.allow();
    }

    String directory = findExistingAncestor(filePath);
    String currentBranch;
    try
    {
      currentBranch = GitCommands.getCurrentBranch(directory);
    }
    catch (IllegalArgumentException | IOException _)
    {
      return FileWriteHandler.Result.warn(
        "⚠️ Branch detection failed for: " + filePath + "\n" +
        "Cannot determine if editing on a base branch.\n" +
        "Proceeding without base branch check.");
    }

    if (isBaseBranch(currentBranch))
    {
      String warning = "⚠️ BASE BRANCH EDIT DETECTED\n" +
                       "\n" +
                       "Branch: " + currentBranch + "\n" +
                       "File: " + filePath + "\n" +
                       "\n" +
                       "You are editing source files directly on a base branch.\n" +
                       "CAT workflow requires issue work to happen in isolated worktrees.\n" +
                       "\n" +
                       "If working on an issue:\n" +
                       "1. Run /cat:work to create a worktree\n" +
                       "2. Or manually: git worktree add " +
                       scope.getProjectCatDir().resolve("worktrees").resolve("issue-name") +
                       " -b issue-branch\n" +
                       "\n" +
                       "If this is intentional infrastructure work (not a task), proceed.\n" +
                       "\n" +
                       "Proceeding with edit (warning only, not blocked).";
      return FileWriteHandler.Result.warn(warning);
    }

    WorktreeContext worktreeContext = WorktreeContext.forSession(
      scope.getProjectCatDir(), scope.getClaudeProjectDir(), scope.getJsonMapper(), sessionId);
    boolean inTaskWorktree = worktreeContext != null;
    if (inTaskWorktree && filePath.startsWith(scope.getClaudeProjectDir().toString()))
    {
      Path absoluteFilePath = Path.of(filePath).toAbsolutePath().normalize();
      if (!absoluteFilePath.startsWith(worktreeContext.absoluteWorktreePath()))
      {
        String warning = "⚠️ WORKTREE PATH BYPASS DETECTED (ESCALATE-A003/M267)\n" +
                         "\n" +
                         "File: " + filePath + "\n" +
                         "Worktree: " + worktreeContext.absoluteWorktreePath() + "\n" +
                         "\n" +
                         "Absolute project paths bypass worktree isolation!\n" +
                         "You are in an issue worktree but editing the main workspace.\n" +
                         "\n" +
                         "Fix: Use the path within your current worktree.\n" +
                         "Example: Instead of " + scope.getClaudeProjectDir() + "/plugin/... use " +
                         worktreeContext.absoluteWorktreePath() + "/plugin/...\n" +
                         "\n" +
                         "Ref: plugin/concepts/worktree-isolation.md\n" +
                         "\n" +
                         "Proceeding with edit (warning only, not blocked).";
        return FileWriteHandler.Result.warn(warning);
      }
    }

    String worktreeNote = "";
    if (inTaskWorktree)
    {
      worktreeNote = "\n(In issue worktree - proper isolation, but main agent should still delegate)";
    }

    String warning = "⚠️ MAIN AGENT SOURCE EDIT DETECTED (A003/M097/M302)\n" +
                     "\n" +
                     "File: " + filePath + worktreeNote + "\n" +
                     "\n" +
                     "Main agent should delegate source code edits to subagents.\n" +
                     "If you are the main CAT orchestrator:\n" +
                     "1. Spawn a subagent via Task tool for implementation\n" +
                     "2. Only proceed directly if: trivial fix OR not during issue execution\n" +
                     "\n" +
                     "Proceeding with edit (warning only, not blocked).";

    return FileWriteHandler.Result.warn(warning);
  }

  /**
   * Find the first existing ancestor directory of a file path.
   *
   * @param filePath the file path to check
   * @return the first existing ancestor directory, or the file path itself if none found
   */
  private static String findExistingAncestor(String filePath)
  {
    Path path = Path.of(filePath);
    Path current = path.getParent();
    while (current != null)
    {
      if (current.toFile().isDirectory())
        return current.toString();
      current = current.getParent();
    }
    return filePath;
  }

  /**
   * Check if the branch is a base branch.
   *
   * @param branch the branch name
   * @return true if it's a base branch
   */
  private static boolean isBaseBranch(String branch)
  {
    if (branch.isEmpty())
      return false;

    if (branch.equals("main") || branch.equals("master") || branch.equals("develop"))
      return true;

    return branch.matches("^v[0-9]+\\.[0-9]+$");
  }
}
