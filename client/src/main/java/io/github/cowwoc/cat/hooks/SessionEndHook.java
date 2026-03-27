/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.SessionEndHandler;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified SessionEnd hook for CAT.
 * <p>
 * TRIGGER: SessionEnd
 * <p>
 * Handles session cleanup operations:
 * <ul>
 *   <li>Lock removal from the project CAT work directory
 *     ({@code {claudeProjectDir}/.cat/work/locks/}):
 *     project lock file, task locks owned by the current session, and stale locks older than 24 hours
 *   </li>
 *   <li>Stale session work directory removal from
 *     {@code {claudeProjectDir}/.cat/work/sessions/} for sessions whose corresponding Claude
 *     session directory no longer exists
 *   </li>
 * </ul>
 */
public final class SessionEndHook implements HookHandler
{
  private static final long STALE_LOCK_AGE_SECONDS = 24L * 60L * 60L;
  private final ClaudeHook scope;

  /**
   * Creates a new SessionEndHook instance.
   *
   * @param scope the hook scope
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionEndHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the session end hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(SessionEndHook.class).error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
  }

  /**
   * Testable entry point with injectable I/O.
   *
   * @param scope the hook scope
   * @param args command line arguments (unused)
   * @param in input stream (unused)
   * @param out output stream for writing JSON response
   * @throws NullPointerException if any argument is null
   */
  public static void run(ClaudeHook scope, String[] args, InputStream in, PrintStream out)
  {
    HookRunner.execute(SessionEndHook::new, scope, out);
  }

  /**
   * Processes hook data and returns the result with any warnings.
   *
   * @param scope the hook scope providing input data and output building
   * @return the hook result containing JSON output and warnings
   */
  @Override
  public HookResult run(ClaudeHook scope)
  {
    return runWithProjectDir(scope, this.scope.getProjectPath());
  }

  /**
   * Processes hook data and releases locks for a specific project directory.
   * <p>
   * This method is public for testing purposes. The lock directory is resolved from the scope's
   * config directory using the encoded project path.
   *
   * @param scope the hook scope providing input data and output building
   * @param projectPath the project directory path (used only to derive the project name for
   *   the main project lock file filename)
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code scope} or {@code projectPath} are null
   */
  public HookResult runWithProjectDir(ClaudeHook scope, Path projectPath)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(projectPath, "projectPath").isNotNull();

    try
    {
      String projectName = projectPath.getFileName().toString();
      String sessionId = scope.getSessionId();

      List<String> messages = new ArrayList<>();

      removeProjectLock(projectName, messages);

      if (!sessionId.isBlank())
      {
        cleanTaskLocks(sessionId, messages);
      }

      cleanStaleLocks(messages);

      new SessionEndHandler(this.scope).clean(sessionId);

      return new HookResult(scope.empty(), messages);
    }
    catch (Exception e)
    {
      return new HookResult(scope.empty(), List.of("SessionEndHook error: " + e.getMessage()));
    }
  }

  /**
   * Removes the project lock file.
   *
   * @param projectName the project name
   * @param messages list to collect status messages
   */
  private void removeProjectLock(String projectName, List<String> messages)
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    Path lockFile = lockDir.resolve(projectName + ".lock");
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
   * @param sessionId the session ID
   * @param messages list to collect status messages
   */
  private void cleanTaskLocks(String sessionId, List<String> messages)
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    cleanLocksInDirectory(lockDir, sessionId, messages, "Task lock released");
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
   * @param messages list to collect status messages
   */
  private void cleanStaleLocks(List<String> messages)
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
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
