/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for TestClaudeHook.
 */
public final class TestClaudeHookTest
{
  /**
   * Verifies that TestClaudeHook can be created and read/write operations work before close().
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void precloseReadWriteWorks() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-hook-project");
    Path pluginRoot = Files.createTempDirectory("test-hook-plugin");
    Path configDir = Files.createTempDirectory("test-hook-config");
    try (TestClaudeHook hook = new TestClaudeHook(projectPath, pluginRoot, configDir))
    {
      // Verify pre-close behavior: reading hook data works
      String sessionId = hook.getSessionId();
      requireThat(sessionId, "sessionId").isNotBlank();

      Path projPath = hook.getProjectPath();
      requireThat(projPath, "projectPath").isNotNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(configDir);
    }
  }

  /**
   * Verifies that TestClaudeHook getPluginPrefix() returns the expected value before close().
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void pluginPrefixReturnsExpectedValuePreclose() throws IOException
  {
    try (TestClaudeHook hook = new TestClaudeHook())
    {
      String prefix = hook.getPluginPrefix();
      requireThat(prefix, "prefix").isEqualTo("cat");
    }
  }

  /**
   * Verifies that TestClaudeHook getWorkDir() returns expected value before close().
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void workDirReturnsExpectedValuePreclose() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-hook-project");
    Path pluginRoot = Files.createTempDirectory("test-hook-plugin");
    Path configDir = Files.createTempDirectory("test-hook-config");
    try (TestClaudeHook hook = new TestClaudeHook(projectPath, pluginRoot, configDir))
    {
      Path workDir = hook.getWorkDir();
      requireThat(workDir, "workDir").isEqualTo(projectPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(configDir);
    }
  }

  /**
   * Verifies that TestClaudeHook getTimezone() returns expected value before close().
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void timeZoneReturnsExpectedValuePreclose() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-hook-project");
    Path pluginRoot = Files.createTempDirectory("test-hook-plugin");
    Path configDir = Files.createTempDirectory("test-hook-config");
    try (TestClaudeHook hook = new TestClaudeHook(projectPath, pluginRoot, configDir))
    {
      String tz = hook.getTimezone();
      requireThat(tz, "tz").isEqualTo("UTC");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(configDir);
    }
  }

  /**
   * Verifies that getSessionId() throws IllegalStateException after close().
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getSessionIdThrowsAfterClose() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-hook-project");
    Path pluginRoot = Files.createTempDirectory("test-hook-plugin");
    Path configDir = Files.createTempDirectory("test-hook-config");
    try (TestClaudeHook hook = new TestClaudeHook(projectPath, pluginRoot, configDir))
    {
      hook.close();
      hook.getSessionId();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(configDir);
    }
  }

  /**
   * Verifies that getProjectPath() throws IllegalStateException after close().
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getProjectPathThrowsAfterClose() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-hook-project");
    Path pluginRoot = Files.createTempDirectory("test-hook-plugin");
    Path configDir = Files.createTempDirectory("test-hook-config");
    try (TestClaudeHook hook = new TestClaudeHook(projectPath, pluginRoot, configDir))
    {
      hook.close();
      hook.getProjectPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(configDir);
    }
  }
}
