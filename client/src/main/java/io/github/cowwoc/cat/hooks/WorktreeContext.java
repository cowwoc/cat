/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolved worktree context for a session.
 * <p>
 * Encapsulates the resolved absolute paths for the worktree and project directory, providing
 * methods to check path containment and compute corrected worktree-relative paths.
 *
 * @param absoluteWorktreePath the absolute normalized path to the worktree directory
 * @param absoluteProjectDirectory the absolute normalized path to the project root directory
 */
public record WorktreeContext(Path absoluteWorktreePath, Path absoluteProjectDirectory)
{
  /**
   * Creates a new worktree context.
   *
   * @throws NullPointerException if {@code absoluteWorktreePath} or {@code absoluteProjectDirectory} are null
   */
  public WorktreeContext
  {
    requireThat(absoluteWorktreePath, "absoluteWorktreePath").isNotNull();
    requireThat(absoluteProjectDirectory, "absoluteProjectDirectory").isNotNull();
  }

  /**
   * Resolves the worktree context for a session by looking up the session's lock file and
   * deriving the worktree path.
   * <p>
   * Returns {@code null} if no active worktree is found for the session (no lock file or
   * worktree directory does not exist).
   *
   * @param projectDir the project root directory
   * @param mapper the JSON mapper for reading lock files
   * @param sessionId the session ID to look up
   * @return the resolved worktree context, or {@code null} if no active worktree exists
   * @throws NullPointerException if {@code projectDir}, {@code mapper}, or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  public static WorktreeContext forSession(Path projectDir, JsonMapper mapper, String sessionId)
  {
    requireThat(projectDir, "projectDir").isNotNull();
    requireThat(mapper, "mapper").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    try
    {
      String issueId = WorktreeLock.findIssueIdForSession(projectDir, mapper, sessionId);
      if (issueId == null)
        return null;

      Path worktreePath = projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(issueId);
      if (!Files.isDirectory(worktreePath))
        return null;

      Path absoluteWorktreePath = worktreePath.toAbsolutePath().normalize();
      Path absoluteProjectDirectory = projectDir.toAbsolutePath().normalize();
      return new WorktreeContext(absoluteWorktreePath, absoluteProjectDirectory);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Computes the corrected worktree path for a file that targets the project directory.
   * <p>
   * Given a path under the project directory, relativizes it against the project root and
   * resolves it under the worktree directory instead.
   *
   * @param targetPath the absolute normalized path to correct
   * @return the corresponding path inside the worktree
   * @throws NullPointerException if {@code targetPath} is null
   */
  public Path correctedPath(Path targetPath)
  {
    requireThat(targetPath, "targetPath").isNotNull();
    Path relativePath = absoluteProjectDirectory.relativize(targetPath);
    return absoluteWorktreePath.resolve(relativePath);
  }
}
