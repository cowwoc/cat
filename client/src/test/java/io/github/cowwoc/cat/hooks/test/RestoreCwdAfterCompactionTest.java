/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.RestoreCwdAfterCompaction;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for RestoreCwdAfterCompaction.
 */
public final class RestoreCwdAfterCompactionTest
{
  /**
   * Creates a HookInput from a JSON string.
   *
   * @param mapper the JSON mapper
   * @param json   the JSON input string
   * @return the parsed HookInput
   */
  private HookInput createInput(JsonMapper mapper, String json)
  {
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    return HookInput.readFrom(mapper, stream);
  }

  /**
   * Verifies that handle() injects a cd instruction when source is "compact" and the .cwd file
   * exists with a valid directory path.
   */
  @Test
  public void compactSourceWithExistingCwdFileInjectsContext() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-compact-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-compact";

      // Create the .cwd file pointing to an existing directory
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      Files.createDirectories(sessionsDir);
      Path savedDir = tempDir.resolve("worktrees/my-issue");
      Files.createDirectories(savedDir);
      Files.writeString(sessionsDir.resolve(sessionId + ".cwd"), savedDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("cd");
      requireThat(result.additionalContext(), "additionalContext").contains(savedDir.toString());
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns empty when source is "compact" but no .cwd file exists.
   */
  @Test
  public void compactSourceWithNoCwdFileReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-nofile-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-nofile";

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns empty when source is "startup" (not "compact").
   */
  @Test
  public void startupSourceReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-startup-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-startup";

      // Even with a .cwd file present, startup should not inject
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      Files.createDirectories(sessionsDir);
      Path savedDir = tempDir.resolve("some/dir");
      Files.createDirectories(savedDir);
      Files.writeString(sessionsDir.resolve(sessionId + ".cwd"), savedDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"startup\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns empty when source is "resume" (not "compact").
   */
  @Test
  public void resumeSourceReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-resume-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-resume";

      // Even with a .cwd file present, resume should not inject
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      Files.createDirectories(sessionsDir);
      Path savedDir = tempDir.resolve("some/dir");
      Files.createDirectories(savedDir);
      Files.writeString(sessionsDir.resolve(sessionId + ".cwd"), savedDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"resume\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns empty when source is "compact" but the saved path no longer
   * exists as a directory.
   */
  @Test
  public void compactSourceWithNonExistentPathReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-missing-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-missing";

      // Write a .cwd file pointing to a path that does not exist
      Path sessionsDir = scope.getProjectCatDir().resolve("sessions");
      Files.createDirectories(sessionsDir);
      Path nonExistentDir = tempDir.resolve("does/not/exist");
      Files.writeString(sessionsDir.resolve(sessionId + ".cwd"), nonExistentDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
