package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;

/**
 * Default implementation of JvmScope.
 *
 * Provides lazy-loaded DisplayUtils via ConcurrentLazyReference for thread-safe
 * initialization. Can be used as a singleton for production or instantiated
 * per-test for isolation.
 */
public final class DefaultJvmScope implements JvmScope
{
  private final ConcurrentLazyReference<DisplayUtils> displayUtils = ConcurrentLazyReference.create(() ->
  {
    try
    {
      return new DisplayUtils();
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  });

  /**
   * Creates a new JVM scope.
   */
  public DefaultJvmScope()
  {
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    return displayUtils.getValue();
  }

  @Override
  public void close()
  {
    // DisplayUtils has no resources to dispose
  }
}
