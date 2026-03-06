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
   * Verifies that handle() injects a cd instruction and the batch ToolSearch instruction when
   * source is "compact" and the .cwd file exists with a valid directory path.
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
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path savedDir = tempDir.resolve("worktrees/my-issue");
      Files.createDirectories(savedDir);
      Files.writeString(sessionCatDir.resolve("session.cwd"), savedDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("cd");
      requireThat(result.additionalContext(), "additionalContext").contains(savedDir.toString());
      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() injects the batch ToolSearch instruction even when no .cwd file exists.
   */
  @Test
  public void compactSourceWithNoCwdFileInjectsBatchToolSearch() throws IOException
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

      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
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
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path savedDir = tempDir.resolve("some/dir");
      Files.createDirectories(savedDir);
      Files.writeString(sessionCatDir.resolve("session.cwd"), savedDir.toString());

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
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path savedDir = tempDir.resolve("some/dir");
      Files.createDirectories(savedDir);
      Files.writeString(sessionCatDir.resolve("session.cwd"), savedDir.toString());

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
   * Verifies that handle() injects the batch ToolSearch instruction when source is "compact" but
   * the saved path no longer exists as a directory.
   */
  @Test
  public void compactSourceWithNonExistentPathInjectsBatchToolSearch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-missing-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-missing";

      // Write a .cwd file pointing to a path that does not exist
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path nonExistentDir = tempDir.resolve("does/not/exist");
      Files.writeString(sessionCatDir.resolve("session.cwd"), nonExistentDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() injects both a cd instruction and the batch ToolSearch instruction
   * when session_id is blank but a valid .cwd file exists.
   */
  @Test
  public void blankSessionIdInjectsCdAndBatchToolSearch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-blank-session-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Write a .cwd file with valid content
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path savedDir = tempDir.resolve("some/dir");
      Files.createDirectories(savedDir);
      Files.writeString(sessionCatDir.resolve("session.cwd"), savedDir.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("cd");
      requireThat(result.additionalContext(), "additionalContext").contains(savedDir.toString());
      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() injects the batch ToolSearch instruction when .cwd file contains only whitespace.
   */
  @Test
  public void compactSourceWithBlankPathContentInjectsBatchToolSearch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-blank-content-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-blank-content";

      // Write .cwd file with only whitespace
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Files.writeString(sessionCatDir.resolve("session.cwd"), "   \n  ");

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() injects the batch ToolSearch instruction and handles IOException
   * gracefully when reading .cwd file.
   */
  @Test
  public void ioErrorReadingCwdFileInjectsBatchToolSearch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-io-error-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-io-error";

      // Create .cwd as a directory (not a file) so readString throws IOException
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path cwdAsDir = sessionCatDir.resolve("session.cwd");
      Files.createDirectory(cwdAsDir);

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() injects both a cd instruction and the batch ToolSearch instruction when
   * .cwd file contains a symlink path pointing to a directory. Symlinks are followed, so the cd
   * instruction is present along with the symlink path.
   */
  @Test
  public void compactSourceWithSymlinkPathInjectsCdAndBatchToolSearch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-restore-cwd-symlink-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-symlink";

      // Create a real directory and a symlink pointing to it
      Path sessionCatDir = scope.getSessionCatDir();
      Files.createDirectories(sessionCatDir);
      Path realDir = tempDir.resolve("real-dir");
      Files.createDirectory(realDir);
      Path symlinkPath = tempDir.resolve("symlink-to-dir");
      Files.createSymbolicLink(symlinkPath, realDir);

      // Write the symlink path to .cwd file
      Files.writeString(sessionCatDir.resolve("session.cwd"), symlinkPath.toString());

      RestoreCwdAfterCompaction handler = new RestoreCwdAfterCompaction(scope);
      HookInput input = createInput(mapper,
        "{\"source\": \"compact\", \"session_id\": \"" + sessionId + "\"}");

      SessionStartHandler.Result result = handler.handle(input);

      // Symlinks to directories are followed, so cd instruction is present with symlink path
      requireThat(result.additionalContext(), "additionalContext").contains("cd");
      requireThat(result.additionalContext(), "additionalContext").contains(symlinkPath.toString());
      requireThat(result.additionalContext(), "additionalContext").
        contains(RestoreCwdAfterCompaction.BATCH_TOOLSEARCH_INSTRUCTION);
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
