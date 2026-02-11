package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Production implementation of JvmScope.
 * <p>
 * Provides lazy-loaded DisplayUtils and environment paths via ConcurrentLazyReference
 * for thread-safe initialization. Reads {@code CLAUDE_PROJECT_DIR} and
 * {@code CLAUDE_PLUGIN_ROOT} from the process environment.
 */
public final class MainJvmScope implements JvmScope
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
  private final ConcurrentLazyReference<Path> claudeProjectDir = ConcurrentLazyReference.create(() ->
  {
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");
    return Path.of(projectDir);
  });
  private final ConcurrentLazyReference<Path> claudePluginRoot = ConcurrentLazyReference.create(() ->
  {
    String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
    if (pluginRoot == null || pluginRoot.isEmpty())
      throw new AssertionError("CLAUDE_PLUGIN_ROOT is not set");
    return Path.of(pluginRoot);
  });

  /**
   * Creates a new production JVM scope.
   */
  public MainJvmScope()
  {
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    return displayUtils.getValue();
  }

  @Override
  public Path getClaudeProjectDir()
  {
    return claudeProjectDir.getValue();
  }

  @Override
  public Path getClaudePluginRoot()
  {
    return claudePluginRoot.getValue();
  }

  @Override
  public void close()
  {
    // No resources to dispose
  }
}
