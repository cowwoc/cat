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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

/**
 * Enforce worktree path isolation.
 * <p>
 * Blocks Edit/Write operations where the file_path targets any path outside the current worktree
 * when a session lock exists for the session. This prevents accidental edits to the main workspace
 * or any other location when an agent should be editing files in the issue worktree.
 * <p>
 * Uses session ID to find the matching lock file in {@code {projectDir}/.claude/cat/locks/},
 * derives the issue_id from the lock filename, and checks whether the file being edited falls
 * within {@code {projectDir}/.claude/cat/worktrees/{issue_id}/}.
 */
public final class EnforceWorktreePathIsolation implements FileWriteHandler
{
  private final Path projectDir;
  private final JsonMapper mapper;

  /**
   * Creates a new EnforceWorktreePathIsolation instance.
   *
   * @param scope the JVM scope providing project directory and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public EnforceWorktreePathIsolation(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.projectDir = scope.getClaudeProjectDir();
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Check if the edit should be blocked due to worktree path isolation violation.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if {@code toolInput} or {@code sessionId} is null
   * @throws IllegalArgumentException if {@code sessionId} is blank
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

    WorktreeContext context = WorktreeContext.forSession(projectDir, mapper, sessionId);
    if (context == null)
      return FileWriteHandler.Result.allow();

    Path absoluteFilePath = Path.of(filePath).toAbsolutePath().normalize();

    if (absoluteFilePath.startsWith(context.absoluteWorktreePath()))
      return FileWriteHandler.Result.allow();

    // Compute the corrected worktree-relative path
    Path correctedPath = context.correctedPath(absoluteFilePath);

    String message = """
      ERROR: Worktree isolation violation

      You are working in worktree: %s
      But attempting to edit outside it: %s

      Use the corrected worktree path instead:
        %s

      Do NOT bypass this hook using Bash (cat, echo, tee, etc.) to write the file directly. \
      The worktree exists to isolate changes from the main workspace until merge.""".formatted(
      context.absoluteWorktreePath(),
      absoluteFilePath,
      correctedPath);

    return FileWriteHandler.Result.block(message);
  }
}
