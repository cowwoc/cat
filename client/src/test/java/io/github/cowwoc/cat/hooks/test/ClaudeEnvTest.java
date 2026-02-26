/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeEnv;
import io.github.cowwoc.cat.hooks.SharedSecrets;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Map;

/**
 * Tests for ClaudeEnv - reads environment variables for CLI commands.
 */
public final class ClaudeEnvTest
{
  /**
   * Verifies that getClaudeSessionId() returns the session ID from a supplied environment map.
   */
  @Test
  public void sessionIdIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_SESSION_ID", "test-session-123");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getClaudeSessionId(), "sessionId").isEqualTo("test-session-123");
  }

  /**
   * Verifies that getClaudeSessionId() throws AssertionError when CLAUDE_SESSION_ID is not set.
   */
  @Test
  public void missingSessionIdThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    try
    {
      claudeEnv.getClaudeSessionId();
    }
    catch (AssertionError e)
    {
      requireThat(e.getMessage(), "message").contains("CLAUDE_SESSION_ID");
    }
  }

  /**
   * Verifies that getClaudeProjectDir() returns the project directory path from a supplied environment map.
   */
  @Test
  public void projectDirIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_PROJECT_DIR", "/workspace");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getClaudeProjectDir(), "projectDir").isEqualTo(Path.of("/workspace"));
  }

  /**
   * Verifies that getClaudeProjectDir() throws AssertionError when CLAUDE_PROJECT_DIR is not set.
   */
  @Test
  public void missingProjectDirThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    try
    {
      claudeEnv.getClaudeProjectDir();
    }
    catch (AssertionError e)
    {
      requireThat(e.getMessage(), "message").contains("CLAUDE_PROJECT_DIR");
    }
  }

  /**
   * Verifies that getClaudePluginRoot() returns the plugin root path from a supplied environment map.
   */
  @Test
  public void pluginRootIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_PLUGIN_ROOT", "/plugin");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getClaudePluginRoot(), "pluginRoot").isEqualTo(Path.of("/plugin"));
  }

  /**
   * Verifies that getClaudePluginRoot() throws AssertionError when CLAUDE_PLUGIN_ROOT is not set.
   */
  @Test
  public void missingPluginRootThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    try
    {
      claudeEnv.getClaudePluginRoot();
    }
    catch (AssertionError e)
    {
      requireThat(e.getMessage(), "message").contains("CLAUDE_PLUGIN_ROOT");
    }
  }

  /**
   * Verifies that getClaudeEnvFile() returns the env file path from a supplied environment map.
   */
  @Test
  public void envFileIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_ENV_FILE", "/tmp/env.sh");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getClaudeEnvFile(), "envFile").isEqualTo(Path.of("/tmp/env.sh"));
  }

  /**
   * Verifies that getClaudeEnvFile() throws AssertionError when CLAUDE_ENV_FILE is not set.
   */
  @Test
  public void missingEnvFileThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    try
    {
      claudeEnv.getClaudeEnvFile();
    }
    catch (AssertionError e)
    {
      requireThat(e.getMessage(), "message").contains("CLAUDE_ENV_FILE");
    }
  }

  /**
   * Verifies that the no-arg constructor constructs without error.
   * <p>
   * This is a smoke test that just verifies construction works using System.getenv().
   */
  @Test
  public void defaultConstructorConstructsWithoutError()
  {
    ClaudeEnv claudeEnv = new ClaudeEnv();
    requireThat(claudeEnv, "claudeEnv").isNotNull();
  }
}
