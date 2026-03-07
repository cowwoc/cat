/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import io.github.cowwoc.cat.hooks.session.WarnUnknownTerminal;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for WarnUnknownTerminal session start handler.
 * <p>
 * Tests verify that the handler correctly detects when fallback emoji widths are in use
 * and emits warnings appropriately, with deduplication via marker files.
 */
public class WarnUnknownTerminalTest
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

  /**
   * Copies emoji-widths.json into the given plugin root directory if it is not already present.
   *
   * @param pluginRoot the plugin root directory to copy the file into
   * @throws IOException if the file cannot be found or copied
   */
  private void ensureEmojiWidthsFile(Path pluginRoot) throws IOException
  {
    Path sourceEmojiFile = Path.of(System.getProperty("user.dir")).resolve("plugin/emoji-widths.json");
    if (!Files.exists(sourceEmojiFile))
      sourceEmojiFile = Path.of(System.getProperty("user.dir")).resolve("../plugin/emoji-widths.json");
    if (!Files.exists(sourceEmojiFile))
      sourceEmojiFile = Path.of(System.getProperty("user.dir")).resolve("../../plugin/emoji-widths.json");
    if (Files.exists(sourceEmojiFile))
      Files.copy(sourceEmojiFile, pluginRoot.resolve("emoji-widths.json"));
  }

  /**
   * Verifies that DisplayUtils does not use fallback widths for a known terminal type.
   */
  @Test
  public void knownTerminalDoesNotUseFallback() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-known-term-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session-1", envFile,
      TerminalType.WINDOWS_TERMINAL))
    {
      ensureEmojiWidthsFile(pluginRoot);

      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"test-session-1\"}");
      SessionStartHandler.Result result = new WarnUnknownTerminal(scope).handle(input);

      // Should not emit warning when terminal is known (not using fallback)
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }

  /**
   * Verifies that DisplayUtils uses fallback widths for an unknown terminal type and that the warning
   * includes the 'WARNING:' prefix, the terminal key, and the expected message format.
   */
  @Test
  public void unknownTerminalUsesFallback() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-unknown-term-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session-2", envFile,
      TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"test-session-2\"}");
      SessionStartHandler.Result result = new WarnUnknownTerminal(scope).handle(input);

      // Should emit warning when terminal is unknown (using fallback)
      requireThat(result.stderr(), "stderr").isNotEmpty();
      requireThat(result.stderr(), "stderr").startsWith("WARNING:");
      requireThat(result.stderr(), "stderr").contains("not found in emoji-widths.json");
      requireThat(result.stderr(), "stderr").contains("unknown");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal emits a warning when fallback is active and no marker file exists.
   */
  @Test
  public void emitsWarningWhenFallbackActiveAndNoMarker() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-warn-fallback-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    String sessionId = "test-session-" + System.nanoTime();
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, sessionId, envFile, TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"" + sessionId + "\"}");
      SessionStartHandler.Result result = new WarnUnknownTerminal(scope).handle(input);

      // Should emit warning
      requireThat(result.stderr(), "stderr").isNotEmpty();
      requireThat(result.stderr(), "stderr").contains("WARNING");

      // Verify marker file was created
      Path markerFile = scope.getSessionDirectory().resolve("terminal-warning-emitted");
      requireThat(Files.exists(markerFile), "markerFileExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal does not emit a warning when marker file already exists (deduplication).
   */
  @Test
  public void suppressesWarningWhenMarkerFileExists() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-suppress-warning-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    String sessionId = "test-session-" + System.nanoTime();
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, sessionId, envFile, TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      // Create the marker file in advance
      Path sessionDir = scope.getSessionDirectory();
      Files.createDirectories(sessionDir);
      Path markerFile = sessionDir.resolve("terminal-warning-emitted");
      Files.writeString(markerFile, "");

      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"" + sessionId + "\"}");
      SessionStartHandler.Result result = new WarnUnknownTerminal(scope).handle(input);

      // Should not emit warning because marker file exists
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal does not emit a warning when terminal is known (not using fallback).
   */
  @Test
  public void suppressesWarningWhenTerminalIsKnown() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-no-warn-known-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session-known", envFile,
      TerminalType.ITERM))
    {
      ensureEmojiWidthsFile(pluginRoot);

      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"test-session-known\"}");
      SessionStartHandler.Result result = new WarnUnknownTerminal(scope).handle(input);

      // Should not emit warning when terminal is known (not using fallback)
      requireThat(result.stderr(), "stderr").isEmpty();

      // Marker file should not be created
      Path markerFile = scope.getSessionDirectory().resolve("terminal-warning-emitted");
      requireThat(Files.exists(markerFile), "markerFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }

  /**
   * Verifies that an empty session ID throws IllegalStateException (fail-fast).
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void emptySessionIdThrows() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-empty-session-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, "test-session-empty", envFile,
      TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"\"}");
      new WarnUnknownTerminal(scope).handle(input);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal still emits the warning even when the marker file write fails
   * due to the session directory being read-only.
   */
  @Test
  public void markerFileWriteFailureStillEmitsWarning() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-readonly-session-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    Path envFile = Files.createTempFile("test-env", ".sh");
    String sessionId = "test-session-" + System.nanoTime();
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot, sessionId, envFile, TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      // Create session directory and make it read-only so marker file write fails
      Path sessionDir = scope.getSessionDirectory();
      Files.createDirectories(sessionDir);
      Set<PosixFilePermission> readOnly = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_EXECUTE);
      Files.setPosixFilePermissions(sessionDir, readOnly);

      try
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookInput input = createInput(mapper, "{\"source\": \"startup\", \"session_id\": \"" + sessionId + "\"}");
        SessionStartHandler.Result result = new WarnUnknownTerminal(scope).handle(input);

        // Should still emit warning even though marker file write fails
        requireThat(result.stderr(), "stderr").isNotEmpty();
        requireThat(result.stderr(), "stderr").contains("WARNING:");
      }
      finally
      {
        // Restore permissions so cleanup can delete the directory
        Files.setPosixFilePermissions(sessionDir, Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      Files.deleteIfExists(envFile);
    }
  }
}
