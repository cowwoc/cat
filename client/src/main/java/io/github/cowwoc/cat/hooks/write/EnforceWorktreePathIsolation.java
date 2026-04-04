/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.ReadHandler;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Enforce worktree path isolation for both read and write operations.
 * <p>
 * Blocks Read/Edit/Write operations that target a path outside the current session's worktree.
 * Two checks are performed:
 * <ol>
 * <li>If the current session holds a lock, file operations must target that session's worktree.</li>
 * <li>If no session lock exists (e.g., during a "direct fix" in a session that did not start the
 *     worktree), any active worktree in the project that covers the target file is used to redirect
 *     the operation. This prevents accidental access to the main workspace when a worktree
 *     already owns that file path.</li>
 * </ol>
 * <p>
 * Uses session ID to find the matching lock file in the project CAT work directory
 * ({@code {claudeProjectPath}/.cat/work/locks/}), derives the issueId from the lock filename,
 * and checks whether the file being accessed falls within the corresponding worktree.
 */
public final class EnforceWorktreePathIsolation implements FileWriteHandler, ReadHandler
{
  private final ClaudeHook scope;
  private final Path projectPath;
  private final JsonMapper mapper;

  /**
   * Creates a new EnforceWorktreePathIsolation instance.
   *
   * @param scope the JVM scope providing project directory and JSON mapper
   */
  public EnforceWorktreePathIsolation(ClaudeHook scope)
  {
    this.scope = scope;
    this.projectPath = scope.getProjectPath();
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Check if the edit should be blocked due to worktree path isolation violation.
   *
   * @param toolInput the tool input JSON
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the check result
   * @throws NullPointerException if {@code toolInput} or {@code catAgentId} are null
   * @throws IllegalArgumentException if {@code catAgentId} is blank
   */
  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String catAgentId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(catAgentId, "catAgentId").isNotBlank();

    Path absoluteFilePath = extractAbsoluteFilePath(toolInput).orElse(null);
    if (absoluteFilePath == null)
      return FileWriteHandler.Result.allow();

    String blockReason = isolationCheckReason(absoluteFilePath, catAgentId);
    if (blockReason != null)
      return FileWriteHandler.Result.block(blockReason);

    return FileWriteHandler.Result.allow();
  }

  /**
   * Check if the read should be blocked due to worktree path isolation violation.
   * <p>
   * Applies the same isolation check as {@link #check(JsonNode, String)} for write/edit
   * operations. Only the {@code Read} tool checks the {@code file_path} field; Glob and Grep
   * use different input fields and are not checked here.
   *
   * @param toolName the tool name (Read, Glob, or Grep)
   * @param toolInput the tool input JSON
   * @param toolResult the tool result JSON (null for PreToolUse)
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the check result
   * @throws NullPointerException if {@code toolName}, {@code toolInput}, or {@code catAgentId} are null
   * @throws IllegalArgumentException if {@code catAgentId} is blank
   */
  @Override
  public ReadHandler.Result check(String toolName, JsonNode toolInput, JsonNode toolResult,
    String catAgentId)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(catAgentId, "catAgentId").isNotBlank();

    // Only check Read tool (Glob/Grep use pattern/glob fields, not file_path)
    if (!"Read".equals(toolName))
      return ReadHandler.Result.allow();

    Path absoluteFilePath = extractAbsoluteFilePath(toolInput).orElse(null);
    if (absoluteFilePath == null)
      return ReadHandler.Result.allow();

    String blockReason = isolationCheckReason(absoluteFilePath, catAgentId);
    if (blockReason != null)
      return ReadHandler.Result.block(blockReason);

    return ReadHandler.Result.allow();
  }

  /**
   * Extracts and normalizes the {@code file_path} field from tool input.
   *
   * @param toolInput the tool input JSON
   * @return the absolute normalized path, or empty if absent or blank
   */
  private Optional<Path> extractAbsoluteFilePath(JsonNode toolInput)
  {
    JsonNode filePathNode = toolInput.get("file_path");
    if (filePathNode == null)
      return Optional.empty();
    String filePath = filePathNode.asString();
    if (filePath == null || filePath.isEmpty())
      return Optional.empty();
    return Optional.of(Path.of(filePath).toAbsolutePath().normalize());
  }

  /**
   * Runs the isolation check and returns a block reason string, or {@code null} if the path is allowed.
   *
   * @param absoluteFilePath the absolute normalized file path to check
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the block reason, or {@code null} if allowed
   */
  private String isolationCheckReason(Path absoluteFilePath, String catAgentId)
  {
    // Extract the session ID from the catAgentId (everything before the first "/" or the whole string)
    String sessionId;
    int slashIndex = catAgentId.indexOf('/');
    if (slashIndex >= 0)
      sessionId = catAgentId.substring(0, slashIndex);
    else
      sessionId = catAgentId;

    WorktreeContext sessionContext = WorktreeContext.forSession(
      scope.getCatWorkPath(), projectPath, mapper, sessionId).orElse(null);
    if (sessionContext != null)
    {
      if (absoluteFilePath.startsWith(sessionContext.absoluteWorktreePath()))
        return null;
      // For subagents (catAgentId contains "/"), allow writes to any worktree the path falls within.
      // Subagents (e.g., SPRT test-run subagents) may operate in runner worktrees that have no lock file.
      if (slashIndex >= 0)
      {
        WorktreeContext coveringContext = findCoveringWorktreeOnDisk(absoluteFilePath);
        if (coveringContext != null)
          return null;
      }
      FileWriteHandler.Result writeResult = checkAgainstContext(sessionContext, absoluteFilePath);
      if (writeResult.blocked())
        return writeResult.reason();
      return null;
    }

    // No session lock: fall back to checking if any active worktree covers the target file.
    // This catches "direct fix" edits made in a session that did not start the worktree.
    WorktreeContext anyContext = findCoveringWorktree(absoluteFilePath);
    if (anyContext != null)
    {
      FileWriteHandler.Result writeResult = checkAgainstContext(anyContext, absoluteFilePath);
      if (writeResult.blocked())
        return writeResult.reason();
    }

    return null;
  }

  /**
   * Scans all worktree directories on disk (regardless of lock status) to find one that covers the
   * given file path. Used for subagents that operate in worktrees without their own lock file
   * (e.g., SPRT test-run subagents in runner worktrees).
   *
   * @param absoluteFilePath the absolute normalized path of the file being accessed
   * @return a matching worktree context, or {@code null} if none found
   */
  private WorktreeContext findCoveringWorktreeOnDisk(Path absoluteFilePath)
  {
    Path worktreeDir = scope.getCatWorkPath().resolve("worktrees");
    if (!Files.isDirectory(worktreeDir))
      return null;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(worktreeDir))
    {
      for (Path worktreePath : stream)
      {
        if (!Files.isDirectory(worktreePath))
          continue;
        Path absoluteWorktreePath = worktreePath.toAbsolutePath().normalize();
        if (absoluteFilePath.startsWith(absoluteWorktreePath))
        {
          Path absoluteProjectDirectory = projectPath.toAbsolutePath().normalize();
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
   * Scans all active lock files to find a worktree that covers the given file path.
   * <p>
   * Returns the first {@link WorktreeContext} whose worktree directory is an ancestor of
   * {@code absoluteFilePath}, or {@code null} if no active worktree covers the path.
   * <p>
   * The iteration order over lock files is determined by the filesystem's directory stream
   * ordering, which is non-deterministic. When multiple active worktrees cover the same
   * file path (which is not a normal state), the returned context is arbitrary.
   *
   * @param absoluteFilePath the absolute normalized path of the file being edited
   * @return a matching worktree context, or {@code null} if none found
   */
  private WorktreeContext findCoveringWorktree(Path absoluteFilePath)
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    if (!Files.isDirectory(lockDir))
      return null;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        String filename = lockFile.getFileName().toString();
        String issueId = filename.substring(0, filename.length() - ".lock".length());
        Path worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve(issueId);
        if (!Files.isDirectory(worktreePath))
          continue;

        Path absoluteWorktreePath = worktreePath.toAbsolutePath().normalize();
        if (absoluteFilePath.startsWith(absoluteWorktreePath))
        {
          Path absoluteProjectDirectory = projectPath.toAbsolutePath().normalize();
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
   * Returns a block result if {@code absoluteFilePath} is not inside {@code context}'s worktree
   * but is inside the project directory, or an allow result otherwise.
   * <p>
   * Allows writes to paths outside both the worktree and the project directory (e.g., /tmp/).
   * Only blocks writes targeting the main workspace when a worktree is active.
   *
   * @param context the resolved worktree context
   * @param absoluteFilePath the absolute normalized path of the file being edited
   * @return the check result
   */
  private FileWriteHandler.Result checkAgainstContext(WorktreeContext context, Path absoluteFilePath)
  {
    if (absoluteFilePath.startsWith(context.absoluteWorktreePath()))
      return FileWriteHandler.Result.allow();

    // Allow paths that are outside the project directory (e.g., /tmp/, /home/user/...)
    if (!absoluteFilePath.startsWith(context.absoluteProjectDirectory()))
      return FileWriteHandler.Result.allow();

    // Block only paths inside the project directory but outside the worktree
    Path correctedPath = context.correctedPath(absoluteFilePath);
    String message = """
      ERROR: Worktree isolation violation

      You are working in worktree: %s
      But attempting to access outside it: %s

      Use the corrected worktree path instead:
        %s

      Do NOT bypass this hook using Bash (cat, echo, tee, etc.) to access the file directly. \
      The worktree exists to isolate changes from the main workspace until merge.""".formatted(
      context.absoluteWorktreePath(),
      absoluteFilePath,
      correctedPath);

    return FileWriteHandler.Result.block(message);
  }
}
