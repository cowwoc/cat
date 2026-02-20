/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.SessionStartHook;
import io.github.cowwoc.cat.hooks.session.CheckRetrospectiveDue;
import io.github.cowwoc.cat.hooks.session.CheckUpdateAvailable;
import io.github.cowwoc.cat.hooks.session.CheckUpgrade;
import io.github.cowwoc.cat.hooks.session.ClearSkillMarkers;
import io.github.cowwoc.cat.hooks.session.EchoSessionId;
import io.github.cowwoc.cat.hooks.session.InjectEnv;
import io.github.cowwoc.cat.hooks.session.InjectSessionInstructions;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import io.github.cowwoc.cat.hooks.util.VersionUtils;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for SessionStartHook and individual session start handlers.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class SessionStartHookTest
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
    return HookInput.readFrom(mapper, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }


  // --- EchoSessionId tests ---

  /**
   * Verifies that EchoSessionId returns the session ID as additional context.
   */
  @Test
  public void echoSessionIdReturnsSessionIdAsContext() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"session_id\": \"test-session-123\"}");
      SessionStartHandler.Result result = new EchoSessionId().handle(input);
      requireThat(result.additionalContext(), "additionalContext").isEqualTo("Session ID: test-session-123");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
  }

  /**
   * Verifies that EchoSessionId returns empty when no session ID is present.
   */
  @Test
  public void echoSessionIdReturnsEmptyWhenNoSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{}");
      SessionStartHandler.Result result = new EchoSessionId().handle(input);
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
  }

  // --- ClearSkillMarkers tests ---

  /**
   * Verifies that ClearSkillMarkers returns empty result when no session ID is present.
   */
  @Test
  public void clearSkillMarkersReturnsEmptyResultWhenNoSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{}");
      SessionStartHandler.Result result = new ClearSkillMarkers().handle(input);
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
  }

  /**
   * Verifies that ClearSkillMarkers deletes the marker file for the current session.
   */
  @Test
  public void clearSkillMarkersDeletesCurrentSessionMarker() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-" + System.nanoTime();
      Path markerFile = Path.of("/tmp/cat-skills-loaded-" + sessionId);
      Files.writeString(markerFile, "marker");
      try
      {
        HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
        SessionStartHandler.Result result = new ClearSkillMarkers().handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
        requireThat(Files.exists(markerFile), "markerFileExists").isFalse();
      }
      finally
      {
        Files.deleteIfExists(markerFile);
      }
    }
  }

  /**
   * Verifies that ClearSkillMarkers skips symlinks in /tmp.
   */
  @Test
  public void clearSkillMarkersSkipsSymlinks() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-symlink-" + System.nanoTime();
      Path targetFile = Path.of("/tmp/cat-skills-loaded-symlink-target-" + sessionId);
      Path symlink = Path.of("/tmp/cat-skills-loaded-" + sessionId);
      Files.writeString(targetFile, "should-not-be-deleted");
      Files.createSymbolicLink(symlink, targetFile);
      try
      {
        HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
        SessionStartHandler.Result result = new ClearSkillMarkers().handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
        // Symlink should not have been deleted
        requireThat(Files.isSymbolicLink(symlink), "symlinkExists").isTrue();
      }
      finally
      {
        Files.deleteIfExists(symlink);
        Files.deleteIfExists(targetFile);
      }
    }
  }

  // --- InjectSessionInstructions tests ---

  /**
   * Verifies that InjectSessionInstructions returns instructions with session ID.
   */
  @Test
  public void injectSessionInstructionsIncludesSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"session_id\": \"my-session\"}");
      SessionStartHandler.Result result = new InjectSessionInstructions().handle(input);
      requireThat(result.additionalContext(), "additionalContext").contains("CAT SESSION INSTRUCTIONS");
      requireThat(result.additionalContext(), "additionalContext").contains("Session ID: my-session");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
  }

  /**
   * Verifies that InjectSessionInstructions uses "unknown" when session ID is missing.
   */
  @Test
  public void injectSessionInstructionsUsesUnknownWhenNoSessionId() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{}");
      SessionStartHandler.Result result = new InjectSessionInstructions().handle(input);
      requireThat(result.additionalContext(), "additionalContext").contains("Session ID: unknown");
    }
  }

  /**
   * Verifies that InjectSessionInstructions includes key instruction sections.
   */
  @Test
  public void injectSessionInstructionsIncludesKeyInstructions() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"session_id\": \"test\"}");
      SessionStartHandler.Result result = new InjectSessionInstructions().handle(input);
      String context = result.additionalContext();
      requireThat(context, "context").contains("User Input Handling");
      requireThat(context, "context").contains("Mandatory Mistake Handling");
      requireThat(context, "context").contains("Commit Before Review");
      requireThat(context, "context").contains("Skill Workflow Compliance");
      requireThat(context, "context").contains("Work Request Handling");
      requireThat(context, "context").contains("Worktree Isolation");
      requireThat(context, "context").contains("Fail-Fast Protocol");
      requireThat(context, "context").contains("Verbatim Output Skills");
    }
  }

  // --- InjectEnv tests ---

  /**
   * Verifies that InjectEnv throws when CLAUDE_ENV_FILE is not set.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void injectEnvThrowsWhenNoEnvFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      // CLAUDE_ENV_FILE is not set in the test environment
      HookInput input = createInput(mapper, "{}");
      new InjectEnv(scope).handle(input);
    }
  }

  /**
   * Verifies that writeToAllSessionDirs writes to multiple UUID-named sibling directories.
   */
  @Test
  public void injectEnvWritesToMultipleSessionDirs() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      // sessionEnvBase/ (2 levels up from envFile)
      //   startupId/sessionstart-hook-1.sh  ← CLAUDE_ENV_FILE
      //   siblingId/                        ← sibling session dir (UUID-named)
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String siblingId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
      String sessionId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Path siblingDir = sessionEnvBase.resolve(siblingId);
      Files.createDirectories(startupDir);
      Files.createDirectories(siblingDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
          new InjectEnv(scope).handle(input);
        }
        requireThat(Files.exists(envFile), "startupEnvFileExists").isTrue();
        Path siblingEnvFile = siblingDir.resolve("sessionstart-hook-1.sh");
        requireThat(Files.exists(siblingEnvFile), "siblingEnvFileExists").isTrue();
        String siblingContent = Files.readString(siblingEnvFile);
        requireThat(siblingContent, "siblingContent").contains("CLAUDE_SESSION_ID=\"" + sessionId + "\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that writeToAllSessionDirs skips non-UUID-named directories.
   */
  @Test
  public void injectEnvSkipsNonUuidNamedDirs() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String sessionId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Path nonUuidDir = sessionEnvBase.resolve("not-a-uuid");
      Files.createDirectories(startupDir);
      Files.createDirectories(nonUuidDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
          new InjectEnv(scope).handle(input);
        }
        // Non-UUID directory should not have an env file written
        Path nonUuidEnvFile = nonUuidDir.resolve("sessionstart-hook-1.sh");
        requireThat(Files.exists(nonUuidEnvFile), "nonUuidEnvFileExists").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that writeToAllSessionDirs skips the directory that envPath already points to.
   */
  @Test
  public void injectEnvSkipsAlreadyWrittenDir() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      // When session_id matches startupId, only one write should happen (no duplicate writes)
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          new InjectEnv(scope).handle(input);
        }
        // File should exist but should not be double-written (appended twice)
        requireThat(Files.exists(envFile), "envFileExists").isTrue();
        String content = Files.readString(envFile);
        // Count occurrences of CLAUDE_SESSION_ID - should appear exactly once
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf("CLAUDE_SESSION_ID", idx)) != -1)
        {
          ++count;
          ++idx;
        }
        requireThat(count, "sessionIdOccurrences").isEqualTo(1);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that writeToAllSessionDirs handles empty sessionEnvBase gracefully (no sibling dirs).
   */
  @Test
  public void injectEnvHandlesEmptySessionEnvBase() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      // sessionEnvBase exists but has no sibling UUID dirs
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String sessionId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
          SessionStartHandler.Result result = new InjectEnv(scope).handle(input);
          // Should succeed with no warnings (only the startup dir and the resumed session dir)
          requireThat(result.stderr(), "stderr").isEmpty();
        }
        requireThat(Files.exists(envFile), "envFileExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that writeToAllSessionDirs handles non-existent sessionEnvBase gracefully.
   */
  @Test
  public void injectEnvHandlesNonExistentSessionEnvBase() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      // sessionEnvBase is only 1 level deep - so getParent().getParent() doesn't exist
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String sessionId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
      // envFile is directly in tempBase (no sessionEnvBase subdirectory)
      Path startupDir = tempBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
          // sessionEnvBase = envFile.getParent().getParent() = tempBase
          // tempBase exists and has startupId in it, so this will iterate without crashing
          SessionStartHandler.Result result = new InjectEnv(scope).handle(input);
          requireThat(result.stderr(), "stderr").isEmpty();
        }
        requireThat(Files.exists(envFile), "envFileExists").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that writeToAllSessionDirs skips symlink directories.
   */
  @Test
  public void injectEnvSkipsSymlinkDirs() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String symlinkId = "dddddddd-dddd-dddd-dddd-dddddddddddd";
      String sessionId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Path realTarget = tempBase.resolve("real-target-dir");
      Files.createDirectories(startupDir);
      Files.createDirectories(realTarget);
      Path symlinkDir = sessionEnvBase.resolve(symlinkId);
      Files.createSymbolicLink(symlinkDir, realTarget);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
          new InjectEnv(scope).handle(input);
        }
        // Symlink directory should not have an env file written inside it
        Path symlinkEnvFile = symlinkDir.resolve("sessionstart-hook-1.sh");
        requireThat(Files.exists(symlinkEnvFile), "symlinkEnvFileExists").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() throws IllegalArgumentException when the projectDir path contains
   * a dangerous shell character such as {@code $}.
   * <p>
   * Since validateEnvValue is private, this test exercises it indirectly by injecting a path containing
   * {@code $} via TestJvmScope. Note: real filesystem paths cannot contain {@code $} on most systems, so
   * this validation is defense-in-depth for injected values; the test uses Path.of() to bypass filesystem
   * restrictions.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void injectEnvRejectsDangerousShellCharacterInProjectDir() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Inject a projectDir path that contains '$' - a dangerous shell character
      // Path.of() allows this without touching the filesystem
      Path dangerousProjectDir = Path.of("/tmp/test-$INJECTED");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(dangerousProjectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          // validateEnvValue is called with projectDir.toString() which contains '$'
          new InjectEnv(scope).handle(input);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() throws IllegalArgumentException when the projectDir path contains
   * a double quote character.
   * <p>
   * Since validateEnvValue is private, this test exercises it indirectly by injecting a path containing
   * {@code "} via TestJvmScope. The test uses Path.of() to bypass filesystem restrictions.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void injectEnvRejectsDoubleQuoteInProjectDir() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Inject a projectDir path that contains '"' - a dangerous shell character
      // Path.of() allows this without touching the filesystem
      Path dangerousProjectDir = Path.of("/tmp/test-\"INJECTED");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(dangerousProjectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          new InjectEnv(scope).handle(input);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() throws IllegalArgumentException when the projectDir path contains
   * a backtick character.
   * <p>
   * Since validateEnvValue is private, this test exercises it indirectly by injecting a path containing
   * a backtick via TestJvmScope. The test uses Path.of() to bypass filesystem restrictions.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void injectEnvRejectsBacktickInProjectDir() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Inject a projectDir path that contains '`' - a dangerous shell character
      // Path.of() allows this without touching the filesystem
      Path dangerousProjectDir = Path.of("/tmp/test-`INJECTED");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(dangerousProjectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          new InjectEnv(scope).handle(input);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() throws IllegalArgumentException when the projectDir path contains
   * a newline character.
   * <p>
   * Since validateEnvValue is private, this test exercises it indirectly by injecting a path containing
   * a newline via TestJvmScope. The test uses Path.of() to bypass filesystem restrictions.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void injectEnvRejectsNewlineInProjectDir() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Inject a projectDir path that contains '\n' - a dangerous shell character
      // Path.of() allows this without touching the filesystem
      Path dangerousProjectDir = Path.of("/tmp/test-\nINJECTED");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(dangerousProjectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          new InjectEnv(scope).handle(input);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv returns a warning when the env file in the resumed session directory is a symlink.
   * <p>
   * When the session_id differs from the startup session ID, InjectEnv writes to the resumed session
   * directory. If the env file at that location is already a symlink, it should return a warning and
   * skip the write for security.
   */
  @Test
  public void injectEnvHandlesSymlinkInResumedSessionDir() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String sessionId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Create the resumed session directory and place a symlink at the env file location
      Path resumedSessionDir = sessionEnvBase.resolve(sessionId);
      Files.createDirectories(resumedSessionDir);
      Path realEnvTarget = tempBase.resolve("real-env-target.sh");
      Files.writeString(realEnvTarget, "# real target");
      Path resumedEnvFile = resumedSessionDir.resolve("sessionstart-hook-1.sh");
      Files.createSymbolicLink(resumedEnvFile, realEnvTarget);

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + sessionId + "\"}");
          SessionStartHandler.Result result = new InjectEnv(scope).handle(input);
          // The env file in the resumed session dir is a symlink - should return a warning
          requireThat(result.additionalContext(), "additionalContext").contains("symlink");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() returns early with a context message when CLAUDE_ENV_FILE is a symlink.
   * <p>
   * When the env file itself is a symlink, InjectEnv skips all writes for security and returns a
   * message containing "symlink".
   */
  @Test
  public void injectEnvSkipsWhenEnvFileIsSymlink() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);

      // Create a real target file and then create envFile as a symlink pointing to it
      Path realTarget = tempBase.resolve("real-target.sh");
      Files.writeString(realTarget, "# real target");
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");
      Files.createSymbolicLink(envFile, realTarget);

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          SessionStartHandler.Result result = new InjectEnv(scope).handle(input);
          requireThat(result.additionalContext(), "additionalContext").contains("symlink");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() throws IllegalArgumentException when the pluginRoot path contains
   * a dangerous shell character such as {@code $}.
   * <p>
   * Since validateEnvValue is private, this test exercises it indirectly by injecting a path containing
   * {@code $} via TestJvmScope. The test uses Path.of() to bypass filesystem restrictions.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void injectEnvRejectsDangerousShellCharacterInPluginRoot() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Inject a pluginRoot path that contains '$' - a dangerous shell character
      // Path.of() allows this without touching the filesystem
      Path dangerousPluginRoot = Path.of("/tmp/test-$INJECTED");
      Path projectDir = Files.createTempDirectory("cat-test-project-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, dangerousPluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          new InjectEnv(scope).handle(input);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() throws IllegalArgumentException when the session_id from stdin
   * contains a dangerous shell character such as {@code $}.
   * <p>
   * The session_id value is validated before being written into the env file. Injecting a dangerous
   * character via the JSON input must cause an immediate failure.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void injectEnvRejectsDangerousShellCharacterInSessionId() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          // Inject a session_id that contains '$' - a dangerous shell character
          HookInput input = createInput(mapper, "{\"session_id\": \"test-$INJECTED\"}");
          new InjectEnv(scope).handle(input);
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  /**
   * Verifies that InjectEnv.handle() returns a warning when a sibling session directory contains an env file
   * that is a symlink.
   * <p>
   * When writing to sibling session directories, InjectEnv skips symlinked env files for security and
   * returns a warning message containing "symlink".
   */
  @Test
  public void injectEnvWarnsWhenSiblingEnvFileIsSymlink() throws IOException
  {
    Path tempBase = Files.createTempDirectory("cat-test-inject-env-");
    try
    {
      String startupId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      String siblingId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
      Path sessionEnvBase = tempBase.resolve("session-env");
      Path startupDir = sessionEnvBase.resolve(startupId);
      Files.createDirectories(startupDir);
      Path envFile = startupDir.resolve("sessionstart-hook-1.sh");

      // Create sibling directory with env file as a symlink
      Path siblingDir = sessionEnvBase.resolve(siblingId);
      Files.createDirectories(siblingDir);
      Path realTarget = tempBase.resolve("real-sibling-target.sh");
      Files.writeString(realTarget, "# real sibling target");
      Path siblingEnvFile = siblingDir.resolve("sessionstart-hook-1.sh");
      Files.createSymbolicLink(siblingEnvFile, realTarget);

      Path projectDir = Files.createTempDirectory("cat-test-project-");
      Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
      try
      {
        try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session",
          envFile, TerminalType.WINDOWS_TERMINAL))
        {
          JsonMapper mapper = scope.getJsonMapper();
          // Use startupId as session_id so the resumed session dir equals startupDir (no resumed write)
          HookInput input = createInput(mapper, "{\"session_id\": \"" + startupId + "\"}");
          SessionStartHandler.Result result = new InjectEnv(scope).handle(input);
          requireThat(result.additionalContext(), "additionalContext").contains("symlink");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempBase);
    }
  }

  // --- CheckUpdateAvailable tests ---

  /**
   * Verifies that CheckUpdateAvailable runs without error and returns empty when no update is available.
   */
  @Test
  public void checkUpdateAvailableRunsWithEnvironment() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-update-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      Path clientDir = pluginRoot.resolve("client");
      Files.createDirectories(clientDir);
      Files.writeString(clientDir.resolve("VERSION"), "99.0.0\n");
      try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot))
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookInput input = createInput(mapper, "{}");
        SessionStartHandler.Result result = new CheckUpdateAvailable(scope).handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // --- CheckUpgrade tests ---

  /**
   * Verifies that CheckUpgrade runs without error and returns empty when no config file exists.
   */
  @Test
  public void checkUpgradeRunsWithEnvironment() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-upgrade-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      // No cat-config.json in projectDir → handler returns empty
      try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot))
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookInput input = createInput(mapper, "{}");
        SessionStartHandler.Result result = new CheckUpgrade(scope).handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // --- CheckRetrospectiveDue tests ---

  /**
   * Verifies that CheckRetrospectiveDue runs without error and returns empty for a non-CAT project.
   */
  @Test
  public void checkRetrospectiveDueRunsWithEnvironment() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-retro-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      // No .planning directory → handler returns empty (not a CAT project)
      try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot))
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookInput input = createInput(mapper, "{}");
        SessionStartHandler.Result result = new CheckRetrospectiveDue(scope).handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // --- SessionStartHook dispatcher tests (normal mode) ---

  /**
   * Verifies that SessionStartHook returns empty JSON when all handlers return empty.
   */
  @Test
  public void dispatcherReturnsEmptyWhenAllHandlersReturnEmpty() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler emptyHandler = input -> SessionStartHandler.Result.empty();
      SessionStartHook dispatcher = new SessionStartHook(List.of(emptyHandler));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.output(), "output").isEqualTo("{}");
    }
  }

  /**
   * Verifies that SessionStartHook combines context from multiple handlers.
   */
  @Test
  public void dispatcherCombinesContextFromMultipleHandlers() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("context from handler 1");
      SessionStartHandler handler2 = input -> SessionStartHandler.Result.context("context from handler 2");
      SessionStartHook dispatcher = new SessionStartHook(List.of(handler1, handler2));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.output(), "output").contains("context from handler 1");
      requireThat(result.output(), "output").contains("context from handler 2");
      requireThat(result.output(), "output").contains("hookSpecificOutput");
      requireThat(result.output(), "output").contains("SessionStart");
    }
  }

  /**
   * Verifies that SessionStartHook returns warnings from handlers.
   */
  @Test
  public void dispatcherReturnsWarningsFromHandlers() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler handler = input -> SessionStartHandler.Result.stderr("stderr message");
      SessionStartHook dispatcher = new SessionStartHook(List.of(handler));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.warnings(), "warnings").contains("stderr message");

      // No context -> empty output
      requireThat(result.output(), "output").isEqualTo("{}");
    }
  }

  /**
   * Verifies that SessionStartHook handles handler exceptions gracefully.
   */
  @Test
  public void dispatcherHandlesHandlerExceptionsGracefully() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler failingHandler = input ->
      {
        throw new IllegalStateException("test error");
      };
      SessionStartHandler goodHandler = input -> SessionStartHandler.Result.context("good context");
      SessionStartHook dispatcher = new SessionStartHook(List.of(failingHandler, goodHandler));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.output(), "output").contains("good context");
      requireThat(result.output(), "output").contains("SessionStart Handler Errors");
      requireThat(result.warnings(), "warnings").isNotEmpty();
      requireThat(result.warnings().getFirst(), "firstWarning").contains("test error");
    }
  }

  /**
   * Verifies that failing handler error appears in additionalContext.
   */
  @Test
  public void dispatcherIncludesErrorInAdditionalContext() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler failingHandler = input ->
      {
        throw new AssertionError("CLAUDE_SESSION_ID is not set");
      };
      SessionStartHook dispatcher = new SessionStartHook(List.of(failingHandler));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.output(), "output").contains("SessionStart Handler Errors");
      requireThat(result.output(), "output").contains("CLAUDE_SESSION_ID is not set");
      requireThat(result.warnings(), "warnings").isNotEmpty();
    }
  }

  /**
   * Verifies that other handlers still produce output when one fails.
   */
  @Test
  public void dispatcherContinuesAfterHandlerFailure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("handler 1 output");
      SessionStartHandler failingHandler = input ->
      {
        throw new IllegalStateException("handler 2 failed");
      };
      SessionStartHandler handler3 = input -> SessionStartHandler.Result.context("handler 3 output");
      SessionStartHook dispatcher = new SessionStartHook(List.of(handler1, failingHandler, handler3));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.output(), "output").contains("handler 1 output");
      requireThat(result.output(), "output").contains("handler 3 output");
      requireThat(result.output(), "output").contains("SessionStart Handler Errors");
      requireThat(result.output(), "output").contains("handler 2 failed");
      requireThat(result.warnings(), "warnings").isNotEmpty();
    }
  }

  /**
   * Verifies that all handlers succeeding produces no error section.
   */
  @Test
  public void dispatcherReturnsSuccessWhenAllHandlersSucceed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("output 1");
      SessionStartHandler handler2 = input -> SessionStartHandler.Result.context("output 2");
      SessionStartHook dispatcher = new SessionStartHook(List.of(handler1, handler2));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      // No error section in output
      requireThat(result.output(), "output").doesNotContain("SessionStart Handler Errors");

      // No warnings
      requireThat(result.warnings(), "warnings").isEmpty();
    }
  }

  /**
   * Verifies that SessionStartHook produces valid JSON with hookSpecificOutput.
   */
  @Test
  public void dispatcherProducesValidJsonWithHookSpecificOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler handler = input -> SessionStartHandler.Result.context("test context");
      SessionStartHook dispatcher = new SessionStartHook(List.of(handler));

      HookInput input = createInput(mapper, "{\"session_id\": \"test\"}");
      HookOutput output = new HookOutput(scope);

      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      // Parse as JSON to verify it's valid
      JsonNode json = mapper.readTree(result.output());
      requireThat(json.has("hookSpecificOutput"), "hasHookSpecificOutput").isTrue();

      JsonNode hookOutput = json.get("hookSpecificOutput");
      requireThat(hookOutput.get("hookEventName").asString(), "hookEventName").isEqualTo("SessionStart");
      requireThat(hookOutput.get("additionalContext").asString(), "additionalContext").contains("test context");
    }
  }

  /**
   * Verifies that SessionStartHook with both context and warnings works correctly.
   */
  @Test
  public void dispatcherHandlesBothContextAndWarnings() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionStartHandler handler = input -> SessionStartHandler.Result.both("context msg", "stderr msg");
      SessionStartHook dispatcher = new SessionStartHook(List.of(handler));

      HookInput input = createInput(mapper, "{}");
      HookOutput output = new HookOutput(scope);
      io.github.cowwoc.cat.hooks.HookResult result = dispatcher.run(input, output);

      requireThat(result.warnings(), "warnings").contains("stderr msg");
      requireThat(result.output(), "output").contains("context msg");
    }
  }

  // --- SessionStartHandler.Result factory tests ---

  /**
   * Verifies that Result.empty() creates a result with empty strings.
   */
  @Test
  public void resultEmptyCreatesEmptyResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.empty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that Result.context() creates a result with context only.
   */
  @Test
  public void resultContextCreatesContextOnlyResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.context("test");
    requireThat(result.additionalContext(), "additionalContext").isEqualTo("test");
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that Result.stderr() creates a result with stderr only.
   */
  @Test
  public void resultStderrCreatesStderrOnlyResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.stderr("error");
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
    requireThat(result.stderr(), "stderr").isEqualTo("error");
  }

  /**
   * Verifies that Result.both() creates a result with both fields.
   */
  @Test
  public void resultBothCreatesBothResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.both("ctx", "err");
    requireThat(result.additionalContext(), "additionalContext").isEqualTo("ctx");
    requireThat(result.stderr(), "stderr").isEqualTo("err");
  }

  /**
   * Verifies that Result constructor rejects null additionalContext.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void resultRejectsNullContext()
  {
    new SessionStartHandler.Result(null, "");
  }

  /**
   * Verifies that Result constructor rejects null stderr.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void resultRejectsNullStderr()
  {
    new SessionStartHandler.Result("", null);
  }

  // --- VersionUtils tests ---

  /**
   * Verifies that version comparison detects equal versions.
   */
  @Test
  public void versionCompareDetectsEqualVersions()
  {
    int result = VersionUtils.compareVersions("2.1.0", "2.1.0");
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that version comparison detects older version.
   */
  @Test
  public void versionCompareDetectsOlderVersion()
  {
    int result = VersionUtils.compareVersions("1.0.0", "2.0.0");
    requireThat(result < 0, "isLessThan").isTrue();
  }

  /**
   * Verifies that version comparison detects newer version.
   */
  @Test
  public void versionCompareDetectsNewerVersion()
  {
    int result = VersionUtils.compareVersions("2.1.0", "1.0.0");
    requireThat(result > 0, "isGreaterThan").isTrue();
  }

  /**
   * Verifies that version comparison handles different length versions.
   */
  @Test
  public void versionCompareHandlesDifferentLengths()
  {
    int result = VersionUtils.compareVersions("2.1", "2.1.0");
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that version comparison handles empty versions.
   */
  @Test
  public void versionCompareHandlesEmptyVersions()
  {
    int result = VersionUtils.compareVersions("", "1.0");
    requireThat(result < 0, "isLessThan").isTrue();
  }

  /**
   * Verifies that version comparison handles null versions.
   */
  @Test
  public void versionCompareHandlesNullVersions()
  {
    int result = VersionUtils.compareVersions(null, null);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that version comparison handles non-numeric parts.
   */
  @Test
  public void versionCompareHandlesNonNumericParts()
  {
    int result = VersionUtils.compareVersions("1.0.alpha", "1.0.0");
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that getPluginVersion throws when client/VERSION is not found.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void getPluginVersionThrowsForMissingVersionFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      VersionUtils.getPluginVersion(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion reads from client/VERSION.
   */
  @Test
  public void getPluginVersionReadsFromClientVersion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Path clientDir = tempDir.resolve("client");
      Files.createDirectories(clientDir);
      Files.writeString(clientDir.resolve("VERSION"), "3.1\n");
      String version = VersionUtils.getPluginVersion(tempDir);
      requireThat(version, "version").isEqualTo("3.1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion accepts a three-part version.
   */
  @Test
  public void getPluginVersionAcceptsThreePartVersion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Path clientDir = tempDir.resolve("client");
      Files.createDirectories(clientDir);
      Files.writeString(clientDir.resolve("VERSION"), "3.2.1\n");
      String version = VersionUtils.getPluginVersion(tempDir);
      requireThat(version, "version").isEqualTo("3.2.1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion throws for an invalid version format.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void getPluginVersionThrowsForInvalidFormat() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Path clientDir = tempDir.resolve("client");
      Files.createDirectories(clientDir);
      Files.writeString(clientDir.resolve("VERSION"), "not-a-version\n");
      VersionUtils.getPluginVersion(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion throws for an empty VERSION file.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void getPluginVersionThrowsForEmptyFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Path clientDir = tempDir.resolve("client");
      Files.createDirectories(clientDir);
      Files.writeString(clientDir.resolve("VERSION"), "\n");
      VersionUtils.getPluginVersion(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
