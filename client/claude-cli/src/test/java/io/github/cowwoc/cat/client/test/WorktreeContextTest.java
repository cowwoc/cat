/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.util.WorktreeContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link WorktreeContext#forSession(Path, Path, JsonMapper, String)} lock schema handling.
 */
public final class WorktreeContextTest
{
  private enum Outcome
  {
    EMPTY_OPTIONAL,
    PRESENT_OPTIONAL
  }

  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Returns lock file payloads that violate expected lock schema.
   *
   * @return data provider matrix: description + lock file content
   */
  @DataProvider
  public Object[][] invalidLockSchemas()
  {
    return new Object[][]
      {
        {"missing session_id", """
          {"worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
          """, Outcome.EMPTY_OPTIONAL},
        {"missing worktrees", """
          {"session_id": "%s", "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
          """.formatted(SESSION_ID), Outcome.PRESENT_OPTIONAL},
        {"malformed json", "{\"session_id\":\"%s\"".formatted(SESSION_ID), Outcome.EMPTY_OPTIONAL},
        {"unexpected field types", """
          {"session_id": 123, "worktrees": "invalid", "created_at": "oops"}
          """, Outcome.EMPTY_OPTIONAL},
        {"empty file", "", Outcome.EMPTY_OPTIONAL}
      };
  }

  /**
   * Verifies malformed or incomplete lock files do not resolve to a session worktree context.
   *
   * @param _description scenario description
   * @param lockContent lock file content to write
   * @param expectedOutcome expected behavior for this schema
   * @throws IOException if test setup fails
   */
  @Test(dataProvider = "invalidLockSchemas")
  public void lockSchemaCases(String _description, String lockContent, Outcome expectedOutcome)
    throws IOException
  {
    Path projectPath = TestUtils.createTempDir("wtc-project-");
    Path catWorkPath = projectPath.resolve(".cat").resolve("work");
    try
    {
      Path locksDir = catWorkPath.resolve("locks");
      Path worktreeDir = catWorkPath.resolve("worktrees").resolve(ISSUE_ID);
      Files.createDirectories(locksDir);
      Files.createDirectories(worktreeDir);
      Files.writeString(locksDir.resolve(ISSUE_ID + ".lock"), lockContent);

      Optional<WorktreeContext> context = WorktreeContext.forSession(catWorkPath, projectPath,
        new JsonMapper(), SESSION_ID);
      if (expectedOutcome == Outcome.PRESENT_OPTIONAL)
        requireThat(context.isPresent(), "context.isPresent()").isTrue();
      else
        requireThat(context.isPresent(), "context.isPresent()").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }
}
