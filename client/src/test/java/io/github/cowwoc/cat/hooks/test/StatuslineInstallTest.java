/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.StatuslineInstall;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Tests for StatuslineInstall.
 */
public final class StatuslineInstallTest
{
  /**
   * Verifies that installing into a project with no settings.json creates the file with a statusLine entry
   * pointing to the Java tool.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void installWithNoSettingsCreatesSettingsFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      StatuslineInstall installer = new StatuslineInstall(scope);
      String result = installer.install(tempDir, pluginRoot);

      requireThat(result, "result").contains("\"status\": \"OK\"");
      Path settingsFile = tempDir.resolve(".claude/settings.json");
      requireThat(Files.exists(settingsFile), "settingsFile.exists").isTrue();
      String content = Files.readString(settingsFile);
      requireThat(content, "content").contains("statusLine");
      requireThat(content, "content").contains("statusline-command");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that installing into a project with an existing settings.json merges the statusLine key
   * without overwriting other settings.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void installWithExistingSettingsMergesStatusLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path claudeDir = tempDir.resolve(".claude");
      Files.createDirectories(claudeDir);
      Path settingsFile = claudeDir.resolve("settings.json");
      Files.writeString(settingsFile, """
        {
          "env": {
            "MY_VAR": "my_value"
          }
        }
        """);

      StatuslineInstall installer = new StatuslineInstall(scope);
      String result = installer.install(tempDir, pluginRoot);

      requireThat(result, "result").contains("\"status\": \"OK\"");
      String content = Files.readString(settingsFile);
      requireThat(content, "content").contains("MY_VAR");
      requireThat(content, "content").contains("statusLine");
      requireThat(content, "content").contains("statusline-command");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the installed statusLine command path uses the absolute plugin root path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void installStatusLineCommandUsesAbsolutePluginRootPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      StatuslineInstall installer = new StatuslineInstall(scope);
      installer.install(tempDir, pluginRoot);

      Path settingsFile = tempDir.resolve(".claude/settings.json");
      String content = Files.readString(settingsFile);
      requireThat(content, "content").contains(pluginRoot.resolve("client/bin/statusline-command").toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the result JSON contains the settings path and the command path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void installResultContainsSettingsPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      StatuslineInstall installer = new StatuslineInstall(scope);
      String result = installer.install(tempDir, pluginRoot);

      requireThat(result, "result").contains("settings_path");
      requireThat(result, "result").contains("script_path");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that corrupted (invalid) JSON in an existing settings.json returns an error JSON response.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidJsonInExistingSettingsReturnsError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path claudeDir = tempDir.resolve(".claude");
      Files.createDirectories(claudeDir);
      Path settingsFile = claudeDir.resolve("settings.json");
      Files.writeString(settingsFile, "{ this is not valid json }");

      StatuslineInstall installer = new StatuslineInstall(scope);
      String result = installer.install(tempDir, pluginRoot);

      requireThat(result, "result").contains("\"status\": \"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a non-object JSON value (array) in settings.json returns an error JSON response,
   * because the ClassCastException from casting to ObjectNode is handled gracefully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void arrayJsonInExistingSettingsReturnsError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path claudeDir = tempDir.resolve(".claude");
      Files.createDirectories(claudeDir);
      Path settingsFile = claudeDir.resolve("settings.json");
      Files.writeString(settingsFile, "[\"not\", \"an\", \"object\"]");

      StatuslineInstall installer = new StatuslineInstall(scope);
      String result = installer.install(tempDir, pluginRoot);

      requireThat(result, "result").contains("\"status\": \"ERROR\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that installing when a statusLine configuration already exists replaces it with the new one.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void installOverwritesExistingStatusLineConfig() throws IOException
  {
    Path tempDir = Files.createTempDirectory("statusline-install-test-");
    Path pluginRoot = Files.createTempDirectory("statusline-plugin-root-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path claudeDir = tempDir.resolve(".claude");
      Files.createDirectories(claudeDir);
      Path settingsFile = claudeDir.resolve("settings.json");
      Files.writeString(settingsFile, """
        {
          "statusLine": {
            "type": "command",
            "command": "/old/path/to/statusline"
          }
        }
        """);

      StatuslineInstall installer = new StatuslineInstall(scope);
      String result = installer.install(tempDir, pluginRoot);

      requireThat(result, "result").contains("\"status\": \"OK\"");
      String content = Files.readString(settingsFile);
      requireThat(content, "content").contains(pluginRoot.resolve("client/bin/statusline-command").toString());
      requireThat(content, "content").doesNotContain("/old/path/to/statusline");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
