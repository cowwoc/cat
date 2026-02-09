package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.DisplayUtils;

/**
 * JVM-wide scope providing lazy-loaded singletons.
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
   * Closes this scope and releases any resources.
   */
  @Override
  void close();
}
