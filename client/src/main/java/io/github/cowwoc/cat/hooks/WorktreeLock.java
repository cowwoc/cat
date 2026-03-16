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
          JsonNode lockData = jsonMapper.readTree(content);
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
