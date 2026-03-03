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
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enforce worktree path isolation.
 * <p>
 * Blocks Edit/Write operations that target a path outside the current session's worktree. Two checks
 * are performed:
 * <ol>
 * <li>If the current session holds a lock, edits must target that session's worktree.</li>
 * <li>If no session lock exists (e.g., during a "direct fix" in a session that did not start the
 *     worktree), any active worktree in the project that covers the target file is used to redirect
 *     the edit. This prevents accidental modifications to the main workspace when a worktree
 *     already owns that file path.</li>
 * </ol>
 * <p>
 * Uses session ID to find the matching lock file in the external CAT storage location
 * ({@code {claudeConfigDir}/projects/{encodedProjectDir}/cat/locks/}), derives the issue_id from the lock filename,
 * and checks whether the file being edited falls within the corresponding worktree.
 */
public final class EnforceWorktreePathIsolation implements FileWriteHandler
{
  private final JvmScope scope;
  private final Path projectDir;
  private final JsonMapper mapper;

  /**
   * Creates a new EnforceWorktreePathIsolation instance.
   *
   * @param scope the JVM scope providing project directory and JSON mapper
   */
  public EnforceWorktreePathIsolation(JvmScope scope)
  {
    this.scope = scope;
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

    Path absoluteFilePath = Path.of(filePath).toAbsolutePath().normalize();

    WorktreeContext context = WorktreeContext.forSession(scope.getProjectCatDir(), projectDir, mapper, sessionId);
    if (context != null)
      return checkAgainstContext(context, absoluteFilePath);

    // No session lock: fall back to checking if any active worktree covers the target file.
    // This catches "direct fix" edits made in a session that did not start the worktree.
    WorktreeContext anyContext = findCoveringWorktree(absoluteFilePath);
    if (anyContext != null)
      return checkAgainstContext(anyContext, absoluteFilePath);

    return FileWriteHandler.Result.allow();
  }

  /**
   * Scans all active lock files to find a worktree that covers the given file path.
   * <p>
   * Returns the first {@link WorktreeContext} whose worktree directory is an ancestor of
   * {@code absoluteFilePath}, or {@code null} if no active worktree covers the path.
   *
   * @param absoluteFilePath the absolute normalized path of the file being edited
   * @return a matching worktree context, or {@code null} if none found
   */
  private WorktreeContext findCoveringWorktree(Path absoluteFilePath)
  {
    Path lockDir = scope.getProjectCatDir().resolve("locks");
    if (!Files.isDirectory(lockDir))
      return null;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        String filename = lockFile.getFileName().toString();
        String issueId = filename.substring(0, filename.length() - ".lock".length());
        Path worktreePath = scope.getProjectCatDir().resolve("worktrees").resolve(issueId);
        if (!Files.isDirectory(worktreePath))
          continue;

        Path absoluteWorktreePath = worktreePath.toAbsolutePath().normalize();
        if (absoluteFilePath.startsWith(absoluteWorktreePath))
        {
          Path absoluteProjectDirectory = projectDir.toAbsolutePath().normalize();
          return new WorktreeContext(absoluteWorktreePath, absoluteProjectDirectory);
        }
      }
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }

    return null;
  }

  /**
   * Returns a block result if {@code absoluteFilePath} is not inside {@code context}'s worktree,
   * or an allow result if it is.
   *
   * @param context the resolved worktree context
   * @param absoluteFilePath the absolute normalized path of the file being edited
   * @return the check result
   */
  private FileWriteHandler.Result checkAgainstContext(WorktreeContext context, Path absoluteFilePath)
  {
    if (absoluteFilePath.startsWith(context.absoluteWorktreePath()))
      return FileWriteHandler.Result.allow();

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
