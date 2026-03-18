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
   * Verifies that getSessionId() returns the session ID from a supplied environment map.
   */
  @Test
  public void sessionIdIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_SESSION_ID", "test-session-123");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getSessionId(), "sessionId").isEqualTo("test-session-123");
  }

  /**
   * Verifies that getSessionId() throws AssertionError when CLAUDE_SESSION_ID is not set.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = ".*CLAUDE_SESSION_ID.*")
  public void missingSessionIdThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    claudeEnv.getSessionId();
  }

  /**
   * Verifies that getProjectPath() returns the project directory path from a supplied environment map.
   */
  @Test
  public void projectPathIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_PROJECT_DIR", "/workspace");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getProjectPath(), "projectPath").isEqualTo(Path.of("/workspace"));
  }

  /**
   * Verifies that getProjectPath() throws AssertionError when CLAUDE_PROJECT_DIR is not set.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = ".*CLAUDE_PROJECT_DIR.*")
  public void missingProjectDirThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    claudeEnv.getProjectPath();
  }

  /**
   * Verifies that getPluginRoot() returns the plugin root path from a supplied environment map.
   */
  @Test
  public void pluginRootIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_PLUGIN_ROOT", "/plugin");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getPluginRoot(), "pluginRoot").isEqualTo(Path.of("/plugin"));
  }

  /**
   * Verifies that getPluginRoot() throws AssertionError when CLAUDE_PLUGIN_ROOT is not set.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = ".*CLAUDE_PLUGIN_ROOT.*")
  public void missingPluginRootThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    claudeEnv.getPluginRoot();
  }

  /**
   * Verifies that getEnvFile() returns the env file path from a supplied environment map.
   */
  @Test
  public void envFileIsReturnedFromEnvironment()
  {
    Map<String, String> env = Map.of("CLAUDE_ENV_FILE", "/tmp/env.sh");
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    requireThat(claudeEnv.getEnvFile(), "envFile").isEqualTo(Path.of("/tmp/env.sh"));
  }

  /**
   * Verifies that getEnvFile() throws AssertionError when CLAUDE_ENV_FILE is not set.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = ".*CLAUDE_ENV_FILE.*")
  public void missingEnvFileThrowsAssertionError()
  {
    Map<String, String> env = Map.of();
    ClaudeEnv claudeEnv = SharedSecrets.newClaudeEnv(env);
    claudeEnv.getEnvFile();
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
