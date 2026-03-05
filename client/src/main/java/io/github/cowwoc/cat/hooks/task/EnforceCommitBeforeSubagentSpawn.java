/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.task;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Block cat:work-execute subagent spawn when the session's worktree has uncommitted changes.
 * <p>
 * Each implementation subagent is spawned with {@code isolation: "worktree"}, which creates
 * a new git worktree branched from the issue branch HEAD. If the main agent has uncommitted
 * changes at spawn time, those changes will not be visible in the subagent's worktree — leading
 * to incomplete or incorrect implementation.
 * <p>
 * This handler blocks Task spawning of cat:work-execute when the session's worktree is dirty
 * (has uncommitted modifications or untracked files). The main agent must commit all changes
 * before spawning any implementation subagent.
 */
public final class EnforceCommitBeforeSubagentSpawn implements TaskHandler
{
  private final JvmScope scope;

  /**
   * Creates a new EnforceCommitBeforeSubagentSpawn handler.
   *
   * @param scope the JVM scope providing project directory and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public EnforceCommitBeforeSubagentSpawn(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId, String cwd)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();

    JsonNode subagentTypeNode = toolInput.get("subagent_type");
    String subagentType;
    if (subagentTypeNode != null)
      subagentType = subagentTypeNode.asString();
    else
      subagentType = "";

    if (!subagentType.equals("cat:work-execute"))
      return Result.allow();

    WorktreeContext context;
    try
    {
      context = WorktreeContext.forSession(scope.getProjectCatDir(), scope.getClaudeProjectDir(),
        scope.getJsonMapper(), sessionId);
    }
    catch (RuntimeException _)
    {
      // If we cannot determine the worktree context, allow the spawn rather than failing
      return Result.allow();
    }

    if (context == null)
      return Result.allow();

    Path worktreePath = context.absoluteWorktreePath();
    if (!Files.isDirectory(worktreePath))
      return Result.allow();

    String statusOutput;
    try
    {
      statusOutput = runGitStatus(worktreePath);
    }
    catch (IOException _)
    {
      // If git status fails (e.g., not a git repo), allow the spawn
      return Result.allow();
    }

    if (statusOutput.isEmpty())
      return Result.allow();

    String reason = """
      BLOCKED: Worktree has uncommitted changes. Commit all changes before spawning a subagent.

      Worktree: %s
      Uncommitted changes detected (git status --porcelain):
      %s

      Rationale: Each subagent is spawned with isolation: "worktree", creating a new git worktree
      branched from the current HEAD. Uncommitted changes are NOT visible in the subagent's
      worktree. All changes must be committed so the subagent sees the complete implementation state.

      Required fix: Commit all changes in the worktree, then retry spawning the subagent.""".
      formatted(worktreePath, statusOutput);
    return Result.block(reason);
  }

  /**
   * Runs {@code git status --porcelain} in the given directory and returns the output.
   * <p>
   * An empty string means the worktree is clean. Non-empty output indicates uncommitted changes.
   *
   * @param directory the directory to run git status in
   * @return the trimmed output of git status --porcelain
   * @throws IOException if the git command fails or is interrupted
   */
  private String runGitStatus(Path directory) throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder("git", "-C", directory.toString(), "status", "--porcelain");
    pb.redirectErrorStream(true);
    Process process = pb.start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      String line = reader.readLine();
      while (line != null)
      {
        if (output.length() > 0)
          output.append('\n');
        output.append(line);
        line = reader.readLine();
      }
    }
    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while running git status in: " + directory, e);
    }
    if (exitCode != 0)
      throw new IOException("git status --porcelain failed with exit code " + exitCode +
        " in directory: " + directory);
    return output.toString().strip();
  }
}
