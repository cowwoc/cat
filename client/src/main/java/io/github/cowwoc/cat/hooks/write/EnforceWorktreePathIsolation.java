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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
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

    String issueId = findIssueIdForSession(sessionId);
    if (issueId == null)
      return FileWriteHandler.Result.allow();

    Path worktreePath = projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(issueId);
    if (!Files.isDirectory(worktreePath))
      return FileWriteHandler.Result.allow();

    Path filePathAbs = Path.of(filePath).toAbsolutePath().normalize();
    Path worktreePathAbs = worktreePath.toAbsolutePath().normalize();

    if (filePathAbs.startsWith(worktreePathAbs))
      return FileWriteHandler.Result.allow();

    // Compute the corrected worktree-relative path
    Path projectDirAbs = projectDir.toAbsolutePath().normalize();
    Path relativePath = projectDirAbs.relativize(filePathAbs);
    Path correctedPath = worktreePathAbs.resolve(relativePath);

    String message = """
      ERROR: Worktree isolation violation

      You are working in worktree: %s
      But attempting to edit outside it: %s

      Use the corrected worktree path instead:
        %s

      Do NOT bypass this hook using Bash (cat, echo, tee, etc.) to write the file directly. \
      The worktree exists to isolate changes from the main workspace until merge.""".formatted(
      worktreePathAbs,
      filePathAbs,
      correctedPath);

    return FileWriteHandler.Result.block(message);
  }

  /**
   * Scans the lock directory to find the issue ID associated with the given session ID.
   * <p>
   * Returns {@code null} if no matching lock file is found or if an I/O error occurs during
   * the scan (treated as no active lock context).
   *
   * @param sessionId the session ID to search for
   * @return the issue ID extracted from the lock filename, or {@code null} if not found
   */
  private String findIssueIdForSession(String sessionId)
  {
    Path lockDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
    if (!Files.isDirectory(lockDir))
      return null;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        try
        {
          String content = Files.readString(lockFile);
          JsonNode lockData = mapper.readTree(content);
          JsonNode sessionNode = lockData.get("session_id");
          if (sessionNode == null)
            continue;

          if (sessionId.equals(sessionNode.asString()))
          {
            String filename = lockFile.getFileName().toString();
            return filename.substring(0, filename.length() - ".lock".length());
          }
        }
        catch (IOException _)
        {
          // Skip unreadable or malformed lock files
        }
      }
    }
    catch (IOException _)
    {
      // Lock directory not accessible - no active lock context
    }

    return null;
  }
}
