/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cleans up stale session work directories at session end.
 * <p>
 * Session work files are stored at {@code {claudeProjectDir}/.cat/work/sessions/{sessionId}/}. A
 * session is considered stale when the corresponding Claude session directory at
 * {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/} no longer exists.
 * <p>
 * The current session's directory is always skipped to avoid deleting files mid-session.
 * Concurrent deletion (e.g., another session cleaning the same stale directory) is handled
 * gracefully.
 */
public final class SessionEndHandler
{
  private static final Logger LOG = LoggerFactory.getLogger(SessionEndHandler.class);
  /**
   * Matches a standard UUID session ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (case-insensitive).
   * <p>
   * Used to validate directory names found under {@code .cat/work/sessions/} before resolving them as
   * paths. Names that do not match this pattern are rejected to prevent path traversal attacks.
   */
  private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    Pattern.CASE_INSENSITIVE);
  private final JvmScope scope;

  /**
   * Creates a new SessionEndHandler.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionEndHandler(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Scans the session work directory and deletes stale session subdirectories.
   * <p>
   * A session directory is stale if its corresponding Claude session directory no longer exists.
   * The current session's directory (identified by {@code scope.getClaudeSessionId()}) is always skipped.
   *
   * @param input the hook input
   * @throws NullPointerException if {@code input} is null
   */
  public void clean(HookInput input)
  {
    requireThat(input, "input").isNotNull();

    Path sessionsDir = scope.getCatWorkPath().resolve("sessions");
    if (Files.notExists(sessionsDir))
      return;

    String currentSessionId = scope.getClaudeSessionId();
    String encodedProjectDir = scope.getEncodedProjectDir();
    Path claudeProjectsDir = scope.getClaudeConfigDir().resolve("projects").resolve(encodedProjectDir);

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir))
    {
      for (Path sessionWorkDir : stream)
      {
        String sessionId = sessionWorkDir.getFileName().toString();

        // Reject non-UUID names to prevent path traversal: only process standard UUID session IDs
        if (!SESSION_ID_PATTERN.matcher(sessionId).matches())
        {
          LOG.warn("Skipping non-UUID entry in sessions directory: {}", sessionId);
          continue;
        }

        // Skip the current session to avoid deleting active work files
        if (sessionId.equals(currentSessionId))
          continue;

        Path claudeSessionDir = claudeProjectsDir.resolve(sessionId);
        if (Files.notExists(claudeSessionDir))
          deleteSessionWorkDirectory(sessionWorkDir);
      }
    }
    catch (IOException e)
    {
      LOG.warn("Failed to scan sessions directory {}: {}", sessionsDir, e.getMessage());
    }
  }

  /**
   * Deletes a session work directory and all its contents.
   * <p>
   * Handles concurrent deletion gracefully: if the directory disappears between the listing
   * and the deletion (e.g., deleted by another session running cleanup concurrently),
   * the error is logged at debug level and processing continues.
   *
   * @param sessionWorkDir the session work directory to delete
   */
  private void deleteSessionWorkDirectory(Path sessionWorkDir)
  {
    try (Stream<Path> walk = Files.walk(sessionWorkDir))
    {
      // Collect and sort deepest paths first so files are deleted before their parent directories
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      boolean deletionFullySucceeded = true;
      for (Path path : paths)
      {
        try
        {
          Files.delete(path);
        }
        catch (IOException e)
        {
          deletionFullySucceeded = false;
          LOG.debug("Could not delete {}: {}", path, e.getMessage());
        }
      }
      // Only log success when all individual deletions completed without error
      if (deletionFullySucceeded)
        LOG.debug("Deleted stale session work directory: {}", sessionWorkDir);
    }
    catch (IOException e)
    {
      // Concurrent deletion or permission error — log and continue
      LOG.debug("Could not walk or delete {}: {}", sessionWorkDir, e.getMessage());
    }
  }
}
