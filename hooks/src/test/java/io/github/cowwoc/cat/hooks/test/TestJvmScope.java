package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Test implementation of JvmScope with injectable environment paths.
 * <p>
 * Accepts {@code claudeProjectDir} and {@code claudePluginRoot} as constructor parameters
 * so tests can point to temporary directories populated with test data.
 */
public final class TestJvmScope implements JvmScope
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
  private final Path claudeProjectDir;
  private final Path claudePluginRoot;

  /**
   * Creates a new test JVM scope.
   *
   * @param claudeProjectDir the project directory path
   * @param claudePluginRoot the plugin root directory path
   * @throws NullPointerException if any parameter is null
   */
  public TestJvmScope(Path claudeProjectDir, Path claudePluginRoot)
  {
    requireThat(claudeProjectDir, "claudeProjectDir").isNotNull();
    requireThat(claudePluginRoot, "claudePluginRoot").isNotNull();
    this.claudeProjectDir = claudeProjectDir;
    this.claudePluginRoot = claudePluginRoot;
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    return displayUtils.getValue();
  }

  @Override
  public Path getClaudeProjectDir()
  {
    return claudeProjectDir;
  }

  @Override
  public Path getClaudePluginRoot()
  {
    return claudePluginRoot;
  }

  @Override
  public void close()
  {
    // No resources to dispose
  }
}
