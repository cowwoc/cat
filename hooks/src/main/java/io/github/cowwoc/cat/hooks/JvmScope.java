package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.DisplayUtils;

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
   */
  DisplayUtils getDisplayUtils();

  /**
   * Returns the Claude project directory.
   *
   * @return the project directory path
   * @throws AssertionError if the directory is not configured
   */
  Path getClaudeProjectDir();

  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if the directory is not configured
   */
  Path getClaudePluginRoot();

  /**
   * Closes this scope and releases any resources.
   */
  @Override
  void close();
}
