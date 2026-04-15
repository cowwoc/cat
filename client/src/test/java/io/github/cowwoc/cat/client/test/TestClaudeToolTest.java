/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.tool.AbstractClaudeTool;
import io.github.cowwoc.cat.claude.hook.AbstractJvmScope;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.skills.TerminalType;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for TestClaudeTool implementation.
 */
public final class TestClaudeToolTest
{
  /**
   * Verifies that getJsonMapper() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getJsonMapperThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getJsonMapper();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getDisplayUtils() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getDisplayUtilsThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getDisplayUtils();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getProjectPath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getProjectPathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getProjectPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getYamlMapper() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getYamlMapperThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getYamlMapper();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getDetectSequentialTools() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getDetectSequentialToolsThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getDetectSequentialTools();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPredictBatchOpportunity() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getPredictBatchOpportunityThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getPredictBatchOpportunity();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getUserIssues() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getUserIssuesThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      scope.close();

      scope.getUserIssues();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects null projectPath.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = "(?si).*projectPath.*")
  public void constructorRejectsNullProjectPath()
  {
    Path validPath = Path.of("/tmp");
    new TestClaudeTool(null, validPath);
  }

  /**
   * Verifies that constructor rejects null pluginRoot.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = "(?si).*pluginRoot.*")
  public void constructorRejectsNullClaudePluginRoot()
  {
    Path validPath = Path.of("/tmp");
    new TestClaudeTool(validPath, null);
  }

  /**
   * Verifies that AbstractClaudeTool constructor rejects a null sessionId.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = "(?si).*sessionId.*")
  public void constructorRejectsNullSessionId()
  {
    Path validPath = Path.of("/tmp");
    new MinimalClaudeTool(null, validPath, validPath, validPath);
  }

  /**
   * Verifies that AbstractClaudeTool constructor rejects a blank sessionId.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?si).*sessionId.*")
  public void constructorRejectsBlankSessionId()
  {
    Path validPath = Path.of("/tmp");
    new MinimalClaudeTool("   ", validPath, validPath, validPath);
  }

  /**
   * Verifies that getSessionId() returns the expected session ID value.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getSessionIdReturnsConstructedValue() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project-");
    Path pluginRoot = Files.createTempDirectory("test-plugin-");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      String sessionId = scope.getSessionId();
      requireThat(sessionId, "sessionId").isEqualTo("00000000-0000-0000-0000-000000000000");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that getProjectPath() returns the injected value.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getProjectPathReturnsInjectedValue() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project-");
    Path pluginRoot = Files.createTempDirectory("test-plugin-");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      requireThat(scope.getProjectPath(), "projectPath").isEqualTo(projectPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that getProjectPath() and getPluginRoot() return the injected values directly.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void injectedValuesReturnedDirectly() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project-");
    Path pluginRoot = Files.createTempDirectory("test-plugin-");
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, pluginRoot))
    {
      requireThat(scope.getProjectPath(), "projectPath").isEqualTo(projectPath);
      requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(pluginRoot);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that derived path methods use the injected project path.
   * <p>
   * getCatDir(), getCatWorkPath(), and getCatSessionPath() must return exactly the paths
   * computed from the injected project path.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void derivedPathMethodsUseProjectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path projectPath = scope.getProjectPath();

      Path catDir = scope.getCatDir();
      requireThat(catDir, "catDir").isEqualTo(projectPath.resolve(".cat"));

      Path catWorkPath = scope.getCatWorkPath();
      requireThat(catWorkPath, "catWorkPath").isEqualTo(projectPath.resolve(".cat").resolve("work"));

      Path catSessionPath = scope.getCatSessionPath("test-session");
      requireThat(catSessionPath, "catSessionPath").isEqualTo(
        projectPath.resolve(".cat").resolve("work").resolve("sessions").resolve("test-session"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that encodeProjectPath() replaces forward slashes with hyphens.
   */
  @Test
  public void encodeProjectPathReplacesSlashesWithHyphens()
  {
    String encoded = AbstractJvmScope.encodeProjectPath("/workspace");
    requireThat(encoded, "encoded").isEqualTo("-workspace");
  }

  /**
   * Verifies that encodeProjectPath() replaces dots with hyphens.
   */
  @Test
  public void encodeProjectPathReplacesDotsWithHyphens()
  {
    String encoded = AbstractJvmScope.encodeProjectPath("/home/user/my.project");
    requireThat(encoded, "encoded").isEqualTo("-home-user-my-project");
  }

  /**
   * Verifies that encodeProjectPath() replaces spaces with hyphens.
   */
  @Test
  public void encodeProjectPathReplacesSpacesWithHyphens()
  {
    String encoded = AbstractJvmScope.encodeProjectPath("/home/user/my project");
    requireThat(encoded, "encoded").isEqualTo("-home-user-my-project");
  }

  // MainClaudeTool cannot be unit-tested directly: its constructor reads environment variables
  // (CLAUDE_PROJECT_DIR, CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID) that are not set in Maven test
  // contexts. Equivalent coverage is provided via integration tests that run with the full hook
  // environment, and by the TestClaudeTool tests above which exercise all AbstractJvmScope logic
  // with injectable paths.

  /**
   * Minimal concrete subclass of AbstractClaudeTool for testing constructor parameter validation.
   * <p>
   * The sole purpose of this class is to expose the protected {@link AbstractClaudeTool} constructor
   * so that validation of {@code sessionId}, {@code projectPath}, and {@code pluginRoot} can be
   * verified directly.
   */
  private static final class MinimalClaudeTool extends AbstractClaudeTool
  {
    /**
     * Creates a new minimal Claude tool for constructor-validation tests.
     *
     * @param sessionId       the session ID (validated by AbstractClaudeTool)
     * @param projectPath     the project path (validated by AbstractClaudeTool)
     * @param pluginRoot      the plugin root (validated by AbstractClaudeTool)
     * @param claudeConfigPath the Claude config directory path
     */
    MinimalClaudeTool(String sessionId, Path projectPath, Path pluginRoot, Path claudeConfigPath)
    {
      super(sessionId, projectPath, pluginRoot, claudeConfigPath);
    }

    @Override
    public Path getWorkDir()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getPluginPrefix()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public TerminalType getTerminalType()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getTimezone()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getAnthropicBaseUrl()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed()
    {
      return false;
    }

    @Override
    public void ensureOpen()
    {
    }

    @Override
    public void close()
    {
    }
  }
}
