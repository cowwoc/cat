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
 * Tests for JvmScope path resolution methods: {@code getProjectCatDir()} and {@code getSessionCatDir()}.
 * <p>
 * Verifies the encoding algorithm ({@code /} and {@code .} replaced by {@code -}) and the resulting
 * directory structure under the config dir.
 */
public final class JvmScopePathResolutionTest
{
  /**
   * Verifies that getProjectCatDir() returns the correct path for a project directory.
   * <p>
   * The result must be {@code {claudeProjectDir}/.cat/work/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getProjectCatDirReturnsCorrectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getProjectCatDir();
      Path expected = tempDir.resolve(".cat").resolve("work");
      requireThat(result, "result").isEqualTo(expected);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getSessionCatDir() returns the correct path including the session ID.
   * <p>
   * The result must be {@code {claudeProjectDir}/.cat/work/sessions/{sessionId}/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getSessionCatDirIncludesSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getSessionCatDir();
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
   * Verifies that getProjectCatDir() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getProjectCatDirThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getProjectCatDir();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getSessionCatDir() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getSessionCatDirThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getSessionCatDir();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getSessionBasePath() returns the correct path.
   * <p>
   * The result must be {@code {claudeConfigDir}/projects/{encodedProjectDir}/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getSessionBasePathReturnsCorrectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getSessionBasePath();
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
   * Verifies that getSessionDirectory() returns the correct path.
   * <p>
   * The result must be {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/}.
   *
   * @throws IOException if temporary directory creation fails
   */
  @Test
  public void getSessionDirectoryReturnsCorrectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path result = scope.getSessionDirectory();
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
   * produce distinct getSessionDirectory() and getSessionCatDir() paths.
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
      Path sessionDir1 = scope1.getSessionDirectory();
      Path sessionDir2 = scope2.getSessionDirectory();
      requireThat(sessionDir1, "sessionDir1").isNotEqualTo(sessionDir2);

      Path sessionCatDir1 = scope1.getSessionCatDir();
      Path sessionCatDir2 = scope2.getSessionCatDir();
      requireThat(sessionCatDir1, "sessionCatDir1").isNotEqualTo(sessionCatDir2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getSessionBasePath() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getSessionBasePathThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getSessionBasePath();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getSessionDirectory() throws IllegalStateException after scope is closed.
   *
   * @throws IOException if temporary directory creation fails
   */
  @SuppressWarnings("try")
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*closed.*")
  public void getSessionDirectoryThrowsAfterClose() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-scope-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      scope.close();
      scope.getSessionDirectory();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
