/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PreCompactHook;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for PreCompactHook.
 */
public final class PreCompactHookTest
{
  /**
   * Verifies that a PreCompact event with a non-root CWD writes the path to the .cwd file.
   */
  @Test
  public void nonRootCwdWritesCwdFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-pre-compact-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path subDir = tempDir.resolve("some/subdir");
      Files.createDirectories(subDir);
      String sessionId = "test-session-compact";

      String json = """
        {
          "session_id": "%s",
          "cwd": "%s"
        }""".formatted(sessionId, subDir.toString());
      HookInput input = HookInput.readFrom(mapper,
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      HookOutput output = new HookOutput(scope);

      HookResult result = new PreCompactHook(scope).run(input, output);

      requireThat(result, "result").isNotNull();

      Path cwdFile = scope.getSessionCatDir().resolve("session.cwd");
      requireThat(Files.exists(cwdFile), "cwdFileExists").isTrue();
      String cwdContent = Files.readString(cwdFile);
      requireThat(cwdContent, "cwdContent").isEqualTo(subDir.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a PreCompact event with a CWD equal to the project root does NOT write the .cwd file.
   */
  @Test
  public void projectRootCwdSkipsCwdFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-pre-compact-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-root";

      String json = """
        {
          "session_id": "%s",
          "cwd": "%s"
        }""".formatted(sessionId, tempDir.toString());
      HookInput input = HookInput.readFrom(mapper,
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      HookOutput output = new HookOutput(scope);

      new PreCompactHook(scope).run(input, output);

      Path cwdFile = scope.getSessionCatDir().resolve("session.cwd");
      requireThat(Files.exists(cwdFile), "cwdFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a PreCompact event with a blank/missing CWD does NOT write the .cwd file.
   */
  @Test
  public void blankCwdSkipsCwdFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-pre-compact-blank-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-blank";

      String json = """
        {
          "session_id": "%s",
          "cwd": ""
        }""".formatted(sessionId);
      HookInput input = HookInput.readFrom(mapper,
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      HookOutput output = new HookOutput(scope);

      new PreCompactHook(scope).run(input, output);

      Path cwdFile = scope.getSessionCatDir().resolve("session.cwd");
      requireThat(Files.exists(cwdFile), "cwdFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a PreCompact event with a blank session_id does NOT write the .cwd file.
   */
  @Test
  public void blankSessionIdSkipsCwdFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-pre-compact-blank-session-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path subDir = tempDir.resolve("some/subdir");
      Files.createDirectories(subDir);

      String json = """
        {
          "session_id": "",
          "cwd": "%s"
        }""".formatted(subDir.toString());
      HookInput input = HookInput.readFrom(mapper,
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      HookOutput output = new HookOutput(scope);

      new PreCompactHook(scope).run(input, output);

      Path cwdFile = scope.getSessionCatDir().resolve("session.cwd");
      requireThat(Files.exists(cwdFile), "cwdFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an I/O error writing the .cwd file is handled gracefully.
   */
  @Test
  public void ioErrorWritingCwdFileHandledGracefully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-pre-compact-io-error-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-io-error";
      Path subDir = tempDir.resolve("some/subdir");
      Files.createDirectories(subDir);

      // Create a regular file where the directory should be, causing createDirectories to fail
      Path sessionCatDir = scope.getSessionCatDir();
      Path parentDir = sessionCatDir.getParent();
      Files.createDirectories(parentDir);
      Files.createFile(sessionCatDir);

      String json = """
        {
          "session_id": "%s",
          "cwd": "%s"
        }""".formatted(sessionId, subDir.toString());
      HookInput input = HookInput.readFrom(mapper,
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      HookOutput output = new HookOutput(scope);

      HookResult result = new PreCompactHook(scope).run(input, output);

      requireThat(result, "result").isNotNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
