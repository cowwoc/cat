/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.ClearAgentMarkers;
import io.github.cowwoc.cat.hooks.util.GetSkill;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ClearAgentMarkers, verifying that the unified loaded/ directory is cleared.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class ClearAgentMarkersTest
{
  /**
   * Verifies that constructor rejects null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorRejectsNullScope()
  {
    new ClearAgentMarkers(null);
  }

  /**
   * Verifies that clearMainAgentMarker rejects a null sessionId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void clearMainAgentMarkerRejectsNullSessionId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      clearer.clearMainAgentMarker(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearMainAgentMarker rejects a blank sessionId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void clearMainAgentMarkerRejectsBlankSessionId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      clearer.clearMainAgentMarker("   ");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearSubagentMarker rejects a null sessionId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void clearSubagentMarkerRejectsNullSessionId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      clearer.clearSubagentMarker(null, "agent123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearSubagentMarker rejects a blank sessionId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId.*")
  public void clearSubagentMarkerRejectsBlankSessionId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      clearer.clearSubagentMarker("   ", "agent123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearSubagentMarker rejects a null agentId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*agentId.*")
  public void clearSubagentMarkerRejectsNullAgentId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      clearer.clearSubagentMarker("session123", null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearSubagentMarker rejects a blank agentId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*agentId.*")
  public void clearSubagentMarkerRejectsBlankAgentId() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      clearer.clearSubagentMarker("session123", "   ");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearMainAgentMarker succeeds silently when no loaded directory exists in the
   * agent directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void clearMainAgentMarkerSucceedsWhenNoMarkersExist() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      String sessionId = "test-session-" + System.nanoTime();
      Path sessionDir = scope.getClaudeSessionsPath().resolve(sessionId);
      Files.createDirectories(sessionDir);
      // No loaded/ directory exists

      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      String warning = clearer.clearMainAgentMarker(sessionId);

      requireThat(warning, "warning").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearSubagentMarker succeeds silently when no loaded directory exists in the
   * subagent directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void clearSubagentMarkerSucceedsWhenNoMarkersExist() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      String sessionId = "test-session-" + System.nanoTime();
      String agentId = "agent-" + System.nanoTime();
      Path agentDir = scope.getClaudeSessionsPath().resolve(sessionId).
        resolve(GetSkill.SUBAGENTS_DIR).resolve(agentId);
      Files.createDirectories(agentDir);
      // No loaded/ directory exists in agent dir

      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      String warning = clearer.clearSubagentMarker(sessionId, agentId);

      requireThat(warning, "warning").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearMainAgentMarker deletes the main agent's loaded directory and its contents.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void clearMainAgentMarkerDeletesLoadedDirectory() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      String sessionId = "test-session-" + System.nanoTime();
      Path sessionDir = scope.getClaudeSessionsPath().resolve(sessionId);
      Path loadedDir = sessionDir.resolve(GetSkill.LOADED_DIR);
      Files.createDirectories(loadedDir);
      Files.writeString(loadedDir.resolve("cat%3Awork"), "", StandardCharsets.UTF_8);
      Files.writeString(loadedDir.resolve("%2Fsome%2Ffile.md"), "", StandardCharsets.UTF_8);

      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      String warning = clearer.clearMainAgentMarker(sessionId);

      requireThat(warning, "warning").isEmpty();
      requireThat(Files.exists(loadedDir), "loadedDirExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that clearSubagentMarker deletes the subagent's loaded directory and its contents.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void clearSubagentMarkerDeletesLoadedDirectory() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-clear-marker-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      String sessionId = "test-session-" + System.nanoTime();
      String agentId = "agent456";
      Path agentDir = scope.getClaudeSessionsPath().resolve(sessionId).
        resolve(GetSkill.SUBAGENTS_DIR).resolve(agentId);
      Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
      Files.createDirectories(loadedDir);
      Files.writeString(loadedDir.resolve("cat%3Awork"), "", StandardCharsets.UTF_8);
      Files.writeString(loadedDir.resolve("%2Fsome%2Ffile.md"), "", StandardCharsets.UTF_8);

      ClearAgentMarkers clearer = new ClearAgentMarkers(scope);
      String warning = clearer.clearSubagentMarker(sessionId, agentId);

      requireThat(warning, "warning").isEmpty();
      requireThat(Files.exists(loadedDir), "loadedDirExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
