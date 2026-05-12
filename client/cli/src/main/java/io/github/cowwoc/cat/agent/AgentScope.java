/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;

/**
 * Runtime-neutral JVM scope providing lazy-loaded singletons and project paths.
 * <p>
 * Runtime-specific scope interfaces may extend this contract with additional methods that only
 * make sense for one agent runtime.
 */
public interface AgentScope extends AutoCloseable
{
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
   * Returns the project's root directory.
   *
   * @return the project directory path
   * @throws AssertionError if the project directory is not configured
   * @throws IllegalStateException if this scope is closed
   */
  Path getProjectPath();

  /**
   * Returns the {@code .cat} directory under the project's root directory.
   *
   * @return the path to the {@code .cat} directory
   * @throws AssertionError if the project directory is not configured
   * @throws IllegalStateException if this scope is closed
   */
  Path getCatDir();

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
