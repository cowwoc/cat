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
   * Returns the current working directory.
   *
   * @return the current working directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getWorkDir();

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
   * Returns the project's root directory.
   *
   * @return the project directory path
   * @throws AssertionError if {@code CLAUDE_PROJECT_DIR} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  Path getProjectPath();

  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if {@code CLAUDE_PLUGIN_ROOT} is not set in the environment
   * @throws IllegalStateException if this scope is closed
   */
  Path getPluginRoot();

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
   * Returns the plugin prefix (e.g., {@code "cat"}).
   * <p>
   * Derived from the plugin root path structure ({@code .../{prefix}/{slug}/{version}/}).
   *
   * @return the plugin prefix, never blank
   * @throws AssertionError if the prefix cannot be derived from the plugin root path
   * @throws IllegalStateException if this scope is closed
   */
  String getPluginPrefix();

  /**
   * Returns the {@code .cat} directory under the project's root directory.
   *
   * @return the path to the {@code .cat} directory
   * @throws AssertionError if the project directory is not configured
   * @throws IllegalStateException if this scope is closed
   */
  Path getCatDir();

  /**
   * Returns the base directory for session JSONL files.
   * <p>
   * Session files are stored at {@code {claudeSessionsPath}/{sessionId}.jsonl}.
   *
   * @return the session base directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeSessionsPath();

  /**
   * Returns the directory for a session's tracking files.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectRoot}/{sessionId}/}.
   *
   * @param sessionId the session ID
   * @return the session directory path
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  Path getClaudeSessionPath(String sessionId);

  /**
   * Returns the cross-session project CAT directory.
   * <p>
   * Located at {@code {projectPath}/.cat/work/}.
   *
   * @return the project CAT directory path
   * @throws IllegalStateException if this scope is closed
   */
  Path getCatWorkPath();

  /**
   * Returns the per-session CAT directory.
   * <p>
   * Located at {@code {projectPath}/.cat/work/sessions/{sessionId}/}.
   *
   * @param sessionId the session ID
   * @return the session CAT directory path
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  Path getCatSessionPath(String sessionId);

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
