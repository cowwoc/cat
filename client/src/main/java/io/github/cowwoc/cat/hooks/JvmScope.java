/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.prompt.UserIssues;
import io.github.cowwoc.cat.hooks.read.post.DetectSequentialTools;
import io.github.cowwoc.cat.hooks.read.pre.PredictBatchOpportunity;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;

/**
 * JVM-wide scope providing lazy-loaded singletons and environment configuration.
 * <p>
 * <b>Thread Safety:</b> Implementations are thread-safe.
 */
public interface JvmScope extends AutoCloseable
{
  /**
   * Returns the display utilities singleton.
   *
   * @return the display utilities
   * @throws IllegalStateException if this scope is closed
   */
  DisplayUtils getDisplayUtils();

  /**
   * Returns the Claude project directory.
   *
   * @return the project directory path
   * @throws AssertionError if the directory is not configured
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeProjectDir();

  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if the directory is not configured
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudePluginRoot();

  /**
   * Returns the plugin prefix (e.g., {@code "cat"}).
   * <p>
   * For production environments, derived from the plugin root path structure
   * ({@code .../{prefix}/{slug}/{version}/}). The prefix is the directory component
   * two levels above the version directory.
   *
   * @return the plugin prefix, never blank
   * @throws AssertionError if the prefix cannot be derived from the plugin root path
   * @throws IllegalStateException if this scope is closed
   */
  String getPluginPrefix();

  /**
   * Returns the Claude session ID.
   *
   * @return the session ID
   * @throws AssertionError if the session ID is not configured
   * @throws IllegalStateException if this scope is closed
   */
  String getClaudeSessionId();

  /**
   * Returns the Claude config directory.
   * <p>
   * Reads the {@code CLAUDE_CONFIG_DIR} environment variable; defaults to {@code ~/.claude} if unset.
   *
   * @return the config directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeConfigDir();

  /**
   * Returns the encoded project directory name.
   * <p>
   * Applies the Claude Code project directory encoding: replaces {@code /} and {@code .} with {@code -}.
   * For example, {@code /workspace} encodes to {@code -workspace}.
   *
   * @return the encoded project directory name
   * @throws IllegalStateException if this scope is closed
   */
  String getEncodedProjectDir();

  /**
   * Returns the base directory for session JSONL files.
   * <p>
   * Session files are stored at {@code {sessionBasePath}/{sessionId}.jsonl}.
   *
   * @return the session base directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getSessionBasePath();

  /**
   * Returns the directory for the current session's tracking files.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/}.
   *
   * @return the session directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getSessionDirectory();

  /**
   * Returns the cross-session project CAT directory.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectDir}/cat/}, where {@code {encodedProjectDir}} is the
   * project directory path with {@code /} and {@code .} replaced by {@code -}.
   * <p>
   * This directory stores cross-session files such as {@code locks/} and {@code worktrees/}.
   *
   * @return the project CAT directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getProjectCatDir();

  /**
   * Returns the per-session CAT directory.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/cat/}, where
   * {@code {encodedProjectDir}} is the project directory path with {@code /} and {@code .} replaced by {@code -}.
   * <p>
   * This directory stores session-scoped files such as {@code verify/} and {@code e2e-config-test/}.
   *
   * @return the session CAT directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getSessionCatDir();

  /**
   * Returns the path to the Claude environment file.
   *
   * @return the environment file path
   * @throws AssertionError if the env file is not configured
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeEnvFile();

  /**
   * Returns the detected terminal type.
   *
   * @return the terminal type
   * @throws IllegalStateException if this scope is closed
   */
  TerminalType getTerminalType();

  /**
   * Returns the shared JSON mapper configured with pretty print output.
   *
   * @return the JSON mapper singleton
   * @throws IllegalStateException if this scope is closed
   */
  JsonMapper getJsonMapper();

  /**
   * Returns the shared YAML mapper.
   *
   * @return the YAML mapper singleton
   * @throws IllegalStateException if this scope is closed
   */
  YAMLMapper getYamlMapper();

  /**
   * Returns the system timezone string from the {@code TZ} environment variable.
   * <p>
   * Defaults to {@code "UTC"} if the variable is not set.
   *
   * @return the timezone string (e.g. {@code "UTC"} or {@code "America/New_York"})
   * @throws IllegalStateException if this scope is closed
   */
  String getTimezone();

  /**
   * Returns the sequential tool detection handler.
   *
   * @return the handler
   * @throws IllegalStateException if this scope is closed
   */
  DetectSequentialTools getDetectSequentialTools();

  /**
   * Returns the batch opportunity prediction handler.
   *
   * @return the handler
   * @throws IllegalStateException if this scope is closed
   */
  PredictBatchOpportunity getPredictBatchOpportunity();

  /**
   * Returns the user issues prompt handler.
   *
   * @return the handler
   * @throws IllegalStateException if this scope is closed
   */
  UserIssues getUserIssues();

  /**
   * Returns the value of an environment variable by name.
   * <p>
   * Returns {@code null} if the variable is not set. Used by {@code GetSkill} to resolve
   * {@code ${name}} references inside directive strings via the scope abstraction.
   *
   * @param name the environment variable name
   * @return the value, or {@code null} if not set
   * @throws NullPointerException if {@code name} is null
   * @throws IllegalStateException if this scope is closed
   */
  String getEnvironmentVariable(String name);

  /**
   * Indicates whether this scope has been closed.
   *
   * @return {@code true} if this scope has been closed
   */
  boolean isClosed();

  /**
   * Throws an exception if this scope has been closed.
   *
   * @throws IllegalStateException if this scope is closed
   */
  void ensureOpen();

  /**
   * Closes this scope and releases any resources.
   * <p>
   * Subsequent calls have no effect.
   */
  @Override
  void close();
}
