/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility for looking up the worktree lock associated with a session.
 * <p>
 * Scans the lock files under the project CAT work directory
 * ({@code {claudeProjectPath}/.cat/work/locks/}) to find which
 * issue ID (if any) is currently checked out for a given session.
 */
public final class WorktreeLock
{
  /**
   * Prevent instantiation.
   */
  private WorktreeLock()
  {
  }

  /**
   * Scans the lock directory to find the issue ID associated with the given session ID.
   * <p>
   * Returns {@code null} if no matching lock file is found.
   *
   * @param projectCatDir the project CAT directory ({@code {claudeProjectPath}/.cat/work/})
   * @param jsonMapper the JSON mapper for reading lock files
   * @param sessionId the session ID to search for
   * @return the issue ID extracted from the lock filename, or {@code null} if not found
   * @throws NullPointerException if {@code projectCatDir}, {@code jsonMapper}, or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   * @throws IOException if an I/O error occurs while reading lock files
   */
  public static String findIssueIdForSession(Path projectCatDir, JsonMapper jsonMapper, String sessionId)
    throws IOException
  {
    requireThat(projectCatDir, "projectCatDir").isNotNull();
    requireThat(jsonMapper, "jsonMapper").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    Path lockDir = projectCatDir.resolve("locks");
    if (!Files.isDirectory(lockDir))
      return null;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        try
        {
          String content = Files.readString(lockFile);
          @SuppressWarnings("unchecked")
          Map<String, Object> lockData = jsonMapper.readValue(content, Map.class);
          boolean ownedBySession = false;

          Object worktreesObject = lockData.get("worktrees");
          if (worktreesObject instanceof Map<?, ?> worktreesMap)
          {
            for (Object ownerSessionId : worktreesMap.values())
            {
              if (sessionId.equals(ownerSessionId))
              {
                ownedBySession = true;
                break;
              }
            }
          }
          if (!ownedBySession)
          {
            Object legacySessionId = lockData.get("session_id");
            ownedBySession = sessionId.equals(legacySessionId);
          }
          if (!ownedBySession)
            continue;
          String filename = lockFile.getFileName().toString();
          return filename.substring(0, filename.length() - ".lock".length());
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

  /**
   * Scans lock files and returns all worktree paths owned by the given session.
   *
   * @param projectCatDir the project CAT directory ({@code {claudeProjectPath}/.cat/work/})
   * @param jsonMapper the JSON mapper for reading lock files
   * @param sessionId the session ID to search for
   * @return the owned worktree paths in lock-file scan order
   * @throws NullPointerException if {@code projectCatDir}, {@code jsonMapper}, or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  @SuppressWarnings("unchecked")
  public static List<Path> findWorktreesForSession(Path projectCatDir, JsonMapper jsonMapper, String sessionId)
  {
    requireThat(projectCatDir, "projectCatDir").isNotNull();
    requireThat(jsonMapper, "jsonMapper").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    List<Path> ownedWorktrees = new ArrayList<>();
    Path lockDir = projectCatDir.resolve("locks");
    if (!Files.isDirectory(lockDir))
      return ownedWorktrees;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        try
        {
          String content = Files.readString(lockFile);
          Map<String, Object> lockData = jsonMapper.readValue(content, Map.class);
          Object worktreesObject = lockData.get("worktrees");
          if (!(worktreesObject instanceof Map<?, ?> worktreesMap) || worktreesMap.isEmpty())
            continue;
          for (Map.Entry<?, ?> entry : worktreesMap.entrySet())
          {
            if (!(entry.getKey() instanceof String worktreePath))
              continue;
            if (!(entry.getValue() instanceof String ownerSessionId))
              continue;
            if (!sessionId.equals(ownerSessionId))
              continue;
            ownedWorktrees.add(Path.of(worktreePath).toAbsolutePath().normalize());
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
    return ownedWorktrees;
  }
}
