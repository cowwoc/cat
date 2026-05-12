/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Represents the parsed content of a CAT lock file.
 * <p>
 * Lock files are stored at {@code {catWorkPath}/locks/<issue-id>.lock} and contain
 * a JSON object describing the session that holds the lock.
 *
 * @param sessionId the session ID holding the lock, or empty string if not present
 * @param createdAt the lock creation time as epoch seconds, or 0 if not present
 * @param worktree  the first worktree path associated with the lock, or empty string if not present
 */
public record LockFile(String sessionId, long createdAt, String worktree)
{
  /**
   * Creates a new lock file record.
   *
   * @throws NullPointerException     if {@code sessionId} or {@code worktree} are null
   * @throws IllegalArgumentException if {@code createdAt} is negative
   */
  public LockFile
  {
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(worktree, "worktree").isNotNull();
    requireThat(createdAt, "createdAt").isGreaterThanOrEqualTo(0L);
  }

  /**
   * Reads and parses a lock file.
   *
   * @param path       the path to the lock file
   * @param jsonMapper the JSON mapper for parsing
   * @return the parsed lock file contents
   * @throws NullPointerException if {@code path} or {@code jsonMapper} are null
   * @throws IOException          if the file cannot be read or its content is not valid JSON
   */
  public static LockFile parse(Path path, JsonMapper jsonMapper) throws IOException
  {
    requireThat(path, "path").isNotNull();
    requireThat(jsonMapper, "jsonMapper").isNotNull();

    String content = Files.readString(path);
    JsonNode root = jsonMapper.readTree(content);

    String sessionId = "";
    long createdAt = 0;
    String worktree = "";

    JsonNode sessionIdNode = root.get("session_id");
    if (sessionIdNode != null && sessionIdNode.isString())
      sessionId = sessionIdNode.asString();

    JsonNode createdAtNode = root.get("created_at");
    if (createdAtNode != null && createdAtNode.isNumber())
      createdAt = createdAtNode.longValue();

    JsonNode worktreesNode = root.get("worktrees");
    if (worktreesNode != null && worktreesNode.isObject())
    {
      // The first key in the worktrees map is the worktree path for the lock holder
      Iterator<String> propertyNames = worktreesNode.propertyNames().iterator();
      if (propertyNames.hasNext())
        worktree = propertyNames.next();
    }

    return new LockFile(sessionId, createdAt, worktree);
  }
}
