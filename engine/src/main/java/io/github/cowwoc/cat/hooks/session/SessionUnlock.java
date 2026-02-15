/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookHandler;
import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.JvmScope;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SessionUnlock - Handles lock cleanup on session end.
 * <p>
 * Removes:
 * <ul>
 *   <li>Project lock file (.claude/cat/locks/${PROJECT_NAME}.lock)</li>
 *   <li>Task locks owned by the current session</li>
 *   <li>Legacy worktree locks owned by the current session</li>
 *   <li>Stale locks older than 24 hours</li>
 * </ul>
 */
public final class SessionUnlock implements HookHandler
{
  private static final long STALE_LOCK_AGE_SECONDS = 24L * 60L * 60L;
  private final JvmScope scope;

  /**
   * Creates a new SessionUnlock handler.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionUnlock(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Processes hook input and releases locks.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();
    return runWithProjectDir(input, output, scope.getClaudeProjectDir());
  }

  /**
   * Processes hook input and releases locks for a specific project directory.
   * <p>
   * This method is public for testing purposes.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @param projectPath the project directory path
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input}, {@code output}, or {@code projectPath} are null
   */
  public HookResult runWithProjectDir(HookInput input, HookOutput output, Path projectPath)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();
    requireThat(projectPath, "projectPath").isNotNull();

    try
    {
      String projectName = projectPath.getFileName().toString();
      String sessionId = input.getSessionId();

      List<String> messages = new ArrayList<>();

      removeProjectLock(projectPath, projectName, messages);

      if (!sessionId.isBlank())
      {
        cleanTaskLocks(projectPath, sessionId, messages);
        cleanLegacyWorktreeLocks(projectPath, sessionId, messages);
      }

      cleanStaleLocks(projectPath, messages);

      return new HookResult(output.empty(), messages);
    }
    catch (Exception e)
    {
      return new HookResult(output.empty(), List.of("SessionUnlock error: " + e.getMessage()));
    }
  }

  /**
   * Removes the project lock file.
   *
   * @param projectPath the project directory
   * @param projectName the project name
   * @param messages list to collect status messages
   */
  private void removeProjectLock(Path projectPath, String projectName, List<String> messages)
  {
    Path lockFile = projectPath.resolve(".claude/cat/locks").resolve(projectName + ".lock");
    if (Files.exists(lockFile))
    {
      try
      {
        Files.delete(lockFile);
        messages.add("Session lock released: " + lockFile);
      }
      catch (IOException e)
      {
        messages.add("Failed to delete project lock " + lockFile + ": " + e.getMessage());
      }
    }
  }

  /**
   * Removes task locks owned by the current session.
   *
   * @param projectPath the project directory
   * @param sessionId the session ID
   * @param messages list to collect status messages
   */
  private void cleanTaskLocks(Path projectPath, String sessionId, List<String> messages)
  {
    cleanLocksInDirectory(projectPath.resolve(".claude/cat/locks"), sessionId, messages, "Task lock released");
  }

  /**
   * Removes legacy worktree locks owned by the current session.
   *
   * @param projectPath the project directory
   * @param sessionId the session ID
   * @param messages list to collect status messages
   */
  private void cleanLegacyWorktreeLocks(Path projectPath, String sessionId, List<String> messages)
  {
    cleanLocksInDirectory(projectPath.resolve(".claude/cat/worktree-locks"), sessionId, messages,
      "Worktree lock released");
  }

  /**
   * Removes locks owned by the current session from a directory.
   *
   * @param lockDir the directory containing lock files
   * @param sessionId the session ID to match
   * @param messages list to collect status messages
   * @param successMessage the message prefix for successfully deleted locks
   */
  private void cleanLocksInDirectory(Path lockDir, String sessionId, List<String> messages,
    String successMessage)
  {
    if (!Files.isDirectory(lockDir))
      return;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        if (isLockOwnedBySession(lockFile, sessionId))
        {
          try
          {
            Files.delete(lockFile);
            messages.add(successMessage + ": " + lockFile);
          }
          catch (IOException e)
          {
            messages.add("Failed to delete lock " + lockFile + ": " + e.getMessage());
          }
        }
      }
    }
    catch (IOException e)
    {
      messages.add("Failed to iterate locks in " + lockDir + ": " + e.getMessage());
    }
  }

  /**
   * Checks if a lock file is owned by the specified session.
   *
   * @param lockFile the lock file to check
   * @param sessionId the session ID to match
   * @return true if the lock file contains the session ID
   */
  private boolean isLockOwnedBySession(Path lockFile, String sessionId)
  {
    try
    {
      String content = Files.readString(lockFile);
      return content.contains(sessionId);
    }
    catch (IOException _)
    {
      return false;
    }
  }

  /**
   * Removes stale lock files older than 24 hours.
   *
   * @param projectPath the project directory
   * @param messages list to collect status messages
   */
  private void cleanStaleLocks(Path projectPath, List<String> messages)
  {
    Path lockDir = projectPath.resolve(".claude/cat/locks");
    if (!Files.isDirectory(lockDir))
      return;

    Instant now = Instant.now();
    Instant staleThreshold = now.minusSeconds(STALE_LOCK_AGE_SECONDS);

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        try
        {
          BasicFileAttributes attrs = Files.readAttributes(lockFile, BasicFileAttributes.class);
          Instant lastModified = attrs.lastModifiedTime().toInstant();

          if (lastModified.isBefore(staleThreshold))
          {
            Files.delete(lockFile);
            messages.add("Stale lock removed: " + lockFile);
          }
        }
        catch (IOException e)
        {
          messages.add("Failed to process lock file " + lockFile + ": " + e.getMessage());
        }
      }
    }
    catch (IOException e)
    {
      messages.add("Failed to iterate locks in " + lockDir + ": " + e.getMessage());
    }
  }
}
