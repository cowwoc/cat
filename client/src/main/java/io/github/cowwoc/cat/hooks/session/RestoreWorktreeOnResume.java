/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

/**
 * Restores the agent's working directory to the active worktree on session resume.
 * <p>
 * When a session is resumed ({@code source} is {@code "resume"}), this handler scans lock files
 * in {@code .claude/cat/locks/} for one matching the current session ID. If a matching lock is found
 * and its worktree directory still exists on disk, additional context is injected instructing the agent
 * to {@code cd} into that worktree.
 */
public final class RestoreWorktreeOnResume implements SessionStartHandler
{
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };
  private final Logger log = LoggerFactory.getLogger(RestoreWorktreeOnResume.class);
  private final JvmScope scope;

  /**
   * Creates a new RestoreWorktreeOnResume handler.
   *
   * @param scope the JVM scope providing environment paths and configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public RestoreWorktreeOnResume(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();

    String source = input.getString("source");
    if (!source.equals("resume"))
      return Result.empty();

    String sessionId = input.getSessionId();
    if (sessionId.isEmpty())
      return Result.empty();

    Path projectDir = scope.getClaudeProjectDir();
    Path locksDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
    if (!Files.isDirectory(locksDir))
      return Result.empty();

    String worktreePath = findWorktreeForSession(locksDir, sessionId);
    if (worktreePath.isEmpty())
      return Result.empty();

    if (!isValidWorktreePath(worktreePath, projectDir))
    {
      log.debug("Rejected invalid worktree path: {}", worktreePath);
      return Result.empty();
    }

    Path worktree = Path.of(worktreePath);
    if (!Files.isDirectory(worktree, LinkOption.NOFOLLOW_LINKS))
      return Result.empty();

    String context = """
      You are resuming a previous session. Your active worktree is: %s

      Run `cd %s` once now, then work from that directory for the rest of the session.\
      """.formatted(worktreePath, worktreePath);
    return Result.context(context);
  }

  /**
   * Validates that a worktree path is safe to use.
   * <p>
   * Rejects paths that contain path traversal sequences, control characters, tilde expansion,
   * or that resolve outside the project directory.
   *
   * @param path the worktree path to validate
   * @param projectDir the project directory that the worktree must reside within
   * @return {@code true} if the path is valid, {@code false} otherwise
   */
  private boolean isValidWorktreePath(String path, Path projectDir)
  {
    assert that(path, "path").isNotNull().elseThrow();
    assert that(projectDir, "projectDir").isNotNull().elseThrow();

    if (path.isBlank() || path.contains("..") || path.contains("~"))
      return false;

    // Reject control characters (chars < 0x20)
    for (int i = 0; i < path.length(); ++i)
    {
      if (path.charAt(i) < 0x20)
        return false;
    }

    // Verify the normalized path starts with the project directory
    Path normalized = Path.of(path).normalize();
    Path normalizedProject = projectDir.normalize();
    return normalized.startsWith(normalizedProject);
  }

  /**
   * Scans lock files in the given directory for one matching the specified session ID.
   *
   * @param locksDir the directory containing lock files
   * @param sessionId the session ID to match
   * @return the worktree path from the matching lock, or empty string if no match found
   */
  private String findWorktreeForSession(Path locksDir, String sessionId)
  {
    assert that(locksDir, "locksDir").isNotNull().elseThrow();
    assert that(sessionId, "sessionId").isNotBlank().elseThrow();
    JsonMapper mapper = scope.getJsonMapper();
    try (Stream<Path> lockFiles = Files.list(locksDir))
    {
      for (Path lockFile : (Iterable<Path>) lockFiles.filter(
        p -> p.toString().endsWith(".lock"))::iterator)
      {
        try
        {
          String content = Files.readString(lockFile);
          Map<String, Object> lockData = mapper.readValue(content, MAP_TYPE);

          Object lockSessionId = lockData.get("session_id");
          if (lockSessionId != null && lockSessionId.toString().equals(sessionId))
          {
            @SuppressWarnings("unchecked")
            Map<String, Object> worktrees = (Map<String, Object>) lockData.get("worktrees");
            if (worktrees != null)
            {
              for (Map.Entry<String, Object> entry : worktrees.entrySet())
              {
                if (sessionId.equals(entry.getValue()))
                  return entry.getKey();
              }
            }
            return "";
          }
        }
        catch (IOException | JacksonException e)
        {
          log.debug("Failed to read lock file: {}", lockFile, e);
        }
      }
    }
    catch (IOException e)
    {
      log.debug("Failed to list lock files in: {}", locksDir, e);
    }
    return "";
  }
}
