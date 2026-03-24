/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import io.github.cowwoc.cat.hooks.session.WarnUnknownTerminal;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import org.testng.annotations.Test;

import java.io.IOException;
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
    {
      try
      {
        Files.copy(sourceEmojiFile, pluginRoot.resolve("emoji-widths.json"));
      }
      catch (java.nio.file.FileAlreadyExistsException _)
      {
        // Already present — the goal is achieved
      }
    }
  }

  /**
   * Verifies that DisplayUtils does not use fallback widths for a known terminal type.
   */
  @Test
  public void knownTerminalDoesNotUseFallback() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-known-term-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session-1\"}", projectPath, pluginRoot, projectPath,
      TerminalType.WINDOWS_TERMINAL))
    {
      ensureEmojiWidthsFile(pluginRoot);

      SessionStartHandler.Result result = new WarnUnknownTerminal().handle(scope);

      // Should not emit warning when terminal is known (not using fallback)
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that DisplayUtils uses fallback widths for an unknown terminal type and that the warning
   * includes the 'WARNING:' prefix, the terminal key, and the expected message format.
   */
  @Test
  public void unknownTerminalUsesFallback() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-unknown-term-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session-2\"}", projectPath, pluginRoot, projectPath,
      TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      SessionStartHandler.Result result = new WarnUnknownTerminal().handle(scope);

      // Should emit warning when terminal is unknown (using fallback)
      requireThat(result.stderr(), "stderr").isNotEmpty();
      requireThat(result.stderr(), "stderr").startsWith("WARNING:");
      requireThat(result.stderr(), "stderr").contains("not found in emoji-widths.json");
      requireThat(result.stderr(), "stderr").contains("unknown");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal emits a warning when fallback is active and no marker file exists.
   */
  @Test
  public void emitsWarningWhenFallbackActiveAndNoMarker() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-warn-fallback-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    String sessionId = "test-session-" + System.nanoTime();
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"" + sessionId + "\"}", projectPath, pluginRoot, projectPath,
      TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      SessionStartHandler.Result result = new WarnUnknownTerminal().handle(scope);

      // Should emit warning
      requireThat(result.stderr(), "stderr").isNotEmpty();
      requireThat(result.stderr(), "stderr").contains("WARNING");

      // Verify marker file was created
      Path markerFile = scope.getClaudeSessionPath(sessionId).resolve("terminal-warning-emitted");
      requireThat(Files.exists(markerFile), "markerFileExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal does not emit a warning when marker file already exists (deduplication).
   */
  @Test
  public void suppressesWarningWhenMarkerFileExists() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-suppress-warning-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    String sessionId = "test-session-" + System.nanoTime();
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"" + sessionId + "\"}", projectPath, pluginRoot, projectPath,
      TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      // Create the marker file in advance
      Path sessionDir = scope.getClaudeSessionPath(sessionId);
      Files.createDirectories(sessionDir);
      Path markerFile = sessionDir.resolve("terminal-warning-emitted");
      Files.writeString(markerFile, "");

      SessionStartHandler.Result result = new WarnUnknownTerminal().handle(scope);

      // Should not emit warning because marker file exists
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal does not emit a warning when terminal is known (not using fallback).
   */
  @Test
  public void suppressesWarningWhenTerminalIsKnown() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-no-warn-known-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session-known\"}", projectPath, pluginRoot, projectPath,
      TerminalType.ITERM))
    {
      ensureEmojiWidthsFile(pluginRoot);

      SessionStartHandler.Result result = new WarnUnknownTerminal().handle(scope);

      // Should not emit warning when terminal is known (not using fallback)
      requireThat(result.stderr(), "stderr").isEmpty();

      // Marker file should not be created
      Path markerFile = scope.getClaudeSessionPath("test-session-known").resolve("terminal-warning-emitted");
      requireThat(Files.exists(markerFile), "markerFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that constructing a TestClaudeHook with an empty session ID throws IllegalArgumentException
   * (fail-fast validation at construction time).
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void emptySessionIdThrows() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-empty-session-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      // Session ID validation happens at construction time in AbstractClaudeHook
      new TestClaudeHook(
        "{\"session_id\": \"\"}", projectPath, pluginRoot, projectPath,
        TerminalType.UNKNOWN);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that WarnUnknownTerminal still emits the warning even when the marker file write fails
   * due to the session directory being read-only.
   */
  @Test
  public void markerFileWriteFailureStillEmitsWarning() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-readonly-session-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    String sessionId = "test-session-" + System.nanoTime();
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"" + sessionId + "\"}", projectPath, pluginRoot, projectPath,
      TerminalType.UNKNOWN))
    {
      ensureEmojiWidthsFile(pluginRoot);

      // Create session directory and make it read-only so marker file write fails
      Path sessionDir = scope.getClaudeSessionPath(sessionId);
      Files.createDirectories(sessionDir);
      Set<PosixFilePermission> readOnly = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_EXECUTE);
      Files.setPosixFilePermissions(sessionDir, readOnly);

      try
      {
        SessionStartHandler.Result result = new WarnUnknownTerminal().handle(scope);

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
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
