/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility for looking up the worktree lock associated with a session.
 * <p>
 * Scans the lock files under {@code {projectDir}/.claude/cat/locks/} to find which
 * issue ID (if any) is currently checked out for a given session.
 */
public final class WorktreeLock
{
  // Cache from sessionId to issueId. Empty string "" is a sentinel meaning "no lock found".
  private static final ConcurrentMap<String, String> SESSION_CACHE = new ConcurrentHashMap<>();
  private static final int MAX_CACHE_SIZE = 100;

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
   * @param projectDir the project root directory
   * @param jsonMapper the JSON mapper for reading lock files
   * @param sessionId the session ID to search for
   * @return the issue ID extracted from the lock filename, or {@code null} if not found
   * @throws NullPointerException if {@code projectDir}, {@code jsonMapper}, or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   * @throws IOException if an I/O error occurs while reading lock files
   */
  public static String findIssueIdForSession(Path projectDir, JsonMapper jsonMapper, String sessionId)
    throws IOException
  {
    requireThat(projectDir, "projectDir").isNotNull();
    requireThat(jsonMapper, "jsonMapper").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    String cached = SESSION_CACHE.get(sessionId);
    if (cached != null)
    {
      if (cached.isEmpty())
        return null;
      return cached;
    }

    Path lockDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
    if (!Files.isDirectory(lockDir))
    {
      cacheResult(sessionId, "");
      return null;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
    {
      for (Path lockFile : stream)
      {
        try
        {
          String content = Files.readString(lockFile);
          JsonNode lockData = jsonMapper.readTree(content);
          JsonNode sessionNode = lockData.get("session_id");
          if (sessionNode == null)
            continue;

          if (sessionId.equals(sessionNode.asString()))
          {
            String filename = lockFile.getFileName().toString();
            String issueId = filename.substring(0, filename.length() - ".lock".length());
            cacheResult(sessionId, issueId);
            return issueId;
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

    cacheResult(sessionId, "");
    return null;
  }

  /**
   * Stores a result in the session cache, clearing the cache first if the size limit is reached.
   *
   * @param sessionId the session ID to cache
   * @param value the issue ID, or an empty string to indicate no lock was found
   */
  private static void cacheResult(String sessionId, String value)
  {
    if (SESSION_CACHE.size() >= MAX_CACHE_SIZE)
      SESSION_CACHE.clear();
    SESSION_CACHE.put(sessionId, value);
  }
}
