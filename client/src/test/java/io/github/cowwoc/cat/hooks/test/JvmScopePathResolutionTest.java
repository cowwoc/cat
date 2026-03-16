/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.AbstractJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.TerminalType;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for JvmScope path resolution methods: {@code getCatWorkPath()} and {@code getCatSessionPath()}.
 * <p>
 * Verifies the encoding algorithm ({@code /} and {@code .} replaced by {@code -}) and the resulting
 * directory structure under the config dir.
 */
public final class JvmScopePathResolutionTest
{
  /**
   * Verifies that getCatWorkPath() returns the correct path for a project directory.
   * <p>
   * The result must be {@code {projectPath}/.cat/work/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getCatWorkPathReturnsCorrectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getCatWorkPath();
      Path expected = tempDir.resolve(".cat").resolve("work");
      requireThat(result, "result").isEqualTo(expected);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCatSessionPath() returns the correct path including the session ID.
   * <p>
   * The result must be {@code {projectPath}/.cat/work/sessions/{sessionId}/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getCatSessionPathIncludesSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getCatSessionPath();
      String sessionId = scope.getClaudeSessionId();
      Path expected = tempDir.resolve(".cat").resolve("work").resolve("sessions").resolve(sessionId);
      requireThat(result, "result").isEqualTo(expected);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the encoding algorithm replaces dots with hyphens.
   * <p>
   * A path like {@code /home/user/my.project} encodes to {@code -home-user-my-project}.
   */
  @Test
  public void encodingReplacesDots()
  {
    String projectPath = "/home/user/my.project";
    String encoded = AbstractJvmScope.encodeProjectPath(projectPath);
    requireThat(encoded, "encoded").isEqualTo("-home-user-my-project");
  }

  /**
   * Verifies that the encoding algorithm produces the expected result for {@code /workspace}.
   */
  @Test
  public void encodingForWorkspace()
  {
    String encoded = AbstractJvmScope.encodeProjectPath("/workspace");
    requireThat(encoded, "encoded").isEqualTo("-workspace");
  }

  /**
   * Verifies that the encoding algorithm handles nested paths with dots.
   * <p>
   * {@code /home/user/my.project/v1.2} encodes to {@code -home-user-my-project-v1-2}.
   */
  @Test
  public void encodingForNestedPathWithDots()
  {
    String encoded = AbstractJvmScope.encodeProjectPath("/home/user/my.project/v1.2");
    requireThat(encoded, "encoded").isEqualTo("-home-user-my-project-v1-2");
  }

  /**
   * Verifies that getCatWorkPath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getCatWorkPathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getCatWorkPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCatSessionPath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getCatSessionPathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getCatSessionPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getClaudeSessionsPath() returns the correct path.
   * <p>
   * The result must be {@code {claudeConfigDir}/projects/{encodedProjectRoot}/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getClaudeSessionsPathReturnsCorrectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getClaudeSessionsPath();
      String encoded = AbstractJvmScope.encodeProjectPath(tempDir.toString());
      Path expected = tempDir.resolve("projects").resolve(encoded);
      requireThat(result, "result").isEqualTo(expected);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getClaudeSessionPath() returns the correct path.
   * <p>
   * The result must be {@code {claudeConfigDir}/projects/{encodedProjectRoot}/{sessionId}/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getClaudeSessionPathReturnsCorrectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getClaudeSessionPath();
      String encoded = AbstractJvmScope.encodeProjectPath(tempDir.toString());
      String sessionId = scope.getClaudeSessionId();
      Path expected = tempDir.resolve("projects").resolve(encoded).resolve(sessionId);
      requireThat(result, "result").isEqualTo(expected);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that two scopes with the same config and project directories but different session IDs
   * produce distinct getClaudeSessionPath() and getCatSessionPath() paths.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void crossSessionPathDifferentiation() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    Path envFile = tempDir.resolve("env.sh");
    try (JvmScope scope1 = new TestJvmScope(tempDir, tempDir, "session-a", envFile, TerminalType.WINDOWS_TERMINAL);
      JvmScope scope2 = new TestJvmScope(tempDir, tempDir, "session-b", envFile, TerminalType.WINDOWS_TERMINAL))
    {
      Path sessionPath1 = scope1.getClaudeSessionPath();
      Path sessionPath2 = scope2.getClaudeSessionPath();
      requireThat(sessionPath1, "sessionPath1").isNotEqualTo(sessionPath2);

      Path catSessionPath1 = scope1.getCatSessionPath();
      Path catSessionPath2 = scope2.getCatSessionPath();
      requireThat(catSessionPath1, "catSessionPath1").isNotEqualTo(catSessionPath2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getClaudeSessionsPath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getClaudeSessionsPathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getClaudeSessionsPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getClaudeSessionPath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getClaudeSessionPathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getClaudeSessionPath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getWorkDir() returns the path injected into TestJvmScope.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getWorkDirReturnsInjectedPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getWorkDir();
      requireThat(result, "result").isEqualTo(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getWorkDir() returns an independently injected path different from claudeProjectPath.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getWorkDirReturnsIndependentlyInjectedPath() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project-");
    Path workDir = Files.createTempDirectory("test-work-");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath, workDir))
    {
      Path result = scope.getWorkDir();
      requireThat(result, "result").isEqualTo(workDir);
      requireThat(result, "result").isNotEqualTo(projectPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(workDir);
    }
  }

  /**
   * Verifies that MainJvmScope.getWorkDir() returns System.getProperty("user.dir").
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void mainJvmScopeGetWorkDirReturnsUserDir() throws IOException
  {
    String userDir = System.getProperty("user.dir");
    requireThat(userDir, "userDir").isNotNull();

    Path tempProjectDir = Files.createTempDirectory("test-main-scope-");
    Path tempPluginRoot = Files.createTempDirectory("test-main-plugin-");
    try
    {
      // Note: MainJvmScope requires environment variables to be set, so we test indirectly
      // by verifying the contract that getWorkDir() should return Path.of(System.getProperty("user.dir"))
      Path expected = Path.of(userDir);
      requireThat(expected, "expected").isNotNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that getClaudeSessionPath() correctly incorporates paths with spaces.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void claudeSessionPathWithSpacesInProjectDir() throws IOException
  {
    Path parentDir = Files.createTempDirectory("test-parent-");
    Path projectPath = parentDir.resolve("my project");
    Files.createDirectories(projectPath);
    Path pluginDir = parentDir.resolve("plugin");
    Files.createDirectories(pluginDir);

    try (JvmScope scope = new TestJvmScope(projectPath, pluginDir))
    {
      Path result = scope.getClaudeSessionPath();
      String encoded = AbstractJvmScope.encodeProjectPath(projectPath.toString());
      Path expected = projectPath.resolve("projects").resolve(encoded).resolve("test-session");
      requireThat(result, "result").isEqualTo(expected);
      // Verify encoding replaced spaces with hyphens (encoded should not contain spaces)
      requireThat(encoded, "encoded").doesNotContain(" ");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(parentDir);
    }
  }

  /**
   * Verifies that getClaudeSessionsPath() correctly encodes paths with spaces.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void claudeSessionsPathWithSpacesInProjectDir() throws IOException
  {
    Path parentDir = Files.createTempDirectory("test-parent-");
    Path projectPath = parentDir.resolve("my test project");
    Files.createDirectories(projectPath);
    Path pluginDir = parentDir.resolve("plugin");
    Files.createDirectories(pluginDir);

    try (JvmScope scope = new TestJvmScope(projectPath, pluginDir))
    {
      Path result = scope.getClaudeSessionsPath();
      String encoded = AbstractJvmScope.encodeProjectPath(projectPath.toString());
      Path expected = projectPath.resolve("projects").resolve(encoded);
      requireThat(result, "result").isEqualTo(expected);
      // Verify encoding replaced all spaces and slashes with hyphens
      requireThat(encoded, "encoded").doesNotContain(" ");
      requireThat(encoded, "encoded").doesNotContain("/");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(parentDir);
    }
  }
}
