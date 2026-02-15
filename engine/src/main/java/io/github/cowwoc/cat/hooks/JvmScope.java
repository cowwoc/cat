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
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

/**
 * JVM-wide scope providing lazy-loaded singletons and environment configuration.
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
   * Returns the shared JSON mapper configured with pretty print output.
   *
   * @return the JSON mapper singleton
   * @throws IllegalStateException if this scope is closed
   */
  JsonMapper getJsonMapper();

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
  default void ensureOpen()
  {
    if (isClosed())
      throw new IllegalStateException("this scope is closed");
  }

  /**
   * Closes this scope and releases any resources.
   * <p>
   * Subsequent calls have no effect.
   */
  @Override
  void close();
}
