/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentRuntime;
import io.github.cowwoc.cat.tool.MainCliTool;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link MainCliTool} environment resolution.
 */
public final class MainCliToolTest
{
  /**
   * Verifies the Claude runtime metadata used by shared CLI scope construction.
   */
  @Test
  public void claudeRuntimeMetadata()
  {
    Path project = Path.of("/workspace/project");
    Path pluginRoot = Path.of("/workspace/plugin");

    requireThat(AgentRuntime.CLAUDE.pluginDescriptor(), "pluginDescriptor").
      isEqualTo(Path.of(".claude-plugin/plugin.json"));
    requireThat(AgentRuntime.CLAUDE.pluginCacheDescriptor(), "pluginCacheDescriptor").isNull();
    requireThat(AgentRuntime.CLAUDE.ruleDirectories(project, pluginRoot), "ruleDirectories").isEqualTo(List.of(
      pluginRoot.resolve("rules/common"),
      pluginRoot.resolve("rules/claude"),
      project.resolve(".cat/rules/common"),
      project.resolve(".cat/rules/claude"),
      project.resolve(".claude/rules")));
  }

  /**
   * Verifies the Codex runtime metadata used by shared CLI scope construction.
   */
  @Test
  public void codexRuntimeMetadata()
  {
    Path project = Path.of("/workspace/project");
    Path pluginRoot = Path.of("/workspace/plugin");

    requireThat(AgentRuntime.CODEX.pluginDescriptor(), "pluginDescriptor").
      isEqualTo(Path.of(".codex-plugin/plugin.json"));
    requireThat(AgentRuntime.CODEX.pluginCacheDescriptor(), "pluginCacheDescriptor").
      isEqualTo(Path.of(".codex-plugin/plugin.json"));
    requireThat(AgentRuntime.CODEX.ruleDirectories(project, pluginRoot), "ruleDirectories").isEqualTo(List.of(
      pluginRoot.resolve("rules/common"),
      pluginRoot.resolve("rules/codex"),
      project.resolve(".cat/rules/common"),
      project.resolve(".cat/rules/codex")));
  }

  /**
   * Verifies that CAT_* variables are sufficient for shared CLI scope construction.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void catVariablesAreSufficient() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-cat-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", root.resolve("cat-config").toString());
      environment.put("CAT_RUNTIME", "codex");
      environment.put("TZ", "America/New_York");

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("cat-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(project);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(pluginRoot);
        requireThat(scope.getPluginData(), "pluginData").isEqualTo(pluginData);
        requireThat(scope.getConfigPath(), "configPath").
          isEqualTo(root.resolve("cat-config"));
        requireThat(scope.getTimezone(), "timezone").isEqualTo("America/New_York");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Provides mixed-case runtime values.
   *
   * @return mixed-case runtime values and expected descriptors
   */
  @DataProvider
  public Object[][] mixedCaseRuntimes()
  {
    return new Object[][]
    {
      {"ClAuDe", AgentRuntime.CLAUDE},
      {"CoDeX", AgentRuntime.CODEX}
    };
  }

  /**
   * Verifies that CAT_RUNTIME matching is case-insensitive for all supported runtimes.
   *
   * @param runtimeId the mixed-case runtime identifier
   * @param expectedRuntime the expected runtime
   */
  @Test(dataProvider = "mixedCaseRuntimes")
  public void catRuntimeAcceptsMixedCase(String runtimeId, AgentRuntime expectedRuntime)
  {
    Map<String, String> environment = validCodexEnvironment();
    environment.put("CAT_RUNTIME", runtimeId);

    try (MainCliTool scope = new MainCliTool(environment::get, Path.of("/tmp")))
    {
      requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
        isEqualTo(expectedRuntime.pluginDescriptor());
    }
  }

  /**
   * Verifies that CAT_* values take precedence when CAT_* and CLAUDE_* are both present.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void catVariablesTakePrecedenceOverClaudeCompatibilityVariables() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-precedence-");
    try
    {
      Path catProject = root.resolve("cat-project");
      Path catPluginRoot = root.resolve("cat-plugin");
      Path catPluginData = root.resolve("cat-plugin-data");
      Path claudeProject = root.resolve("claude-project");
      Path claudePluginRoot = root.resolve("claude-plugin");
      Path claudePluginData = root.resolve("claude-plugin-data");
      Files.createDirectories(catProject);
      Files.createDirectories(catPluginRoot);
      Files.createDirectories(catPluginData);
      Files.createDirectories(claudeProject);
      Files.createDirectories(claudePluginRoot);
      Files.createDirectories(claudePluginData);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", catProject.toString());
      environment.put("CAT_PLUGIN_ROOT", catPluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", catPluginData.toString());
      environment.put("CLAUDE_SESSION_ID", "claude-session");
      environment.put("CLAUDE_PROJECT_DIR", claudeProject.toString());
      environment.put("CLAUDE_PLUGIN_ROOT", claudePluginRoot.toString());
      environment.put("CLAUDE_PLUGIN_DATA", claudePluginData.toString());
      environment.put("CLAUDE_CONFIG_DIR", claudePluginData.resolve("claude-config").toString());
      environment.put("CAT_CONFIG_DIR", catPluginData.resolve("cat-config").toString());
      environment.put("CAT_RUNTIME", "codex");

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("cat-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(catProject);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(catPluginRoot);
        requireThat(scope.getPluginData(), "pluginData").isEqualTo(catPluginData);
        requireThat(scope.getConfigPath(), "configPath").
          isEqualTo(catPluginData.resolve("cat-config"));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that shared CLI scopes fail fast instead of falling back to CLAUDE_* variables.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_SESSION_ID is required and must not be blank.*")
  public void claudeCompatibilityVariablesDoNotSatisfySharedScope()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CLAUDE_SESSION_ID", "claude-session");
    environment.put("CLAUDE_PROJECT_DIR", "/tmp/project");
    environment.put("CLAUDE_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CLAUDE_PLUGIN_DATA", "/tmp/plugin-data");
    environment.put("CLAUDE_CONFIG_DIR", "/tmp/claude-config");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that missing session variables fail with a CAT-specific error.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_SESSION_ID is required and must not be blank.*")
  public void missingSessionVariablesFailFast()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_PROJECT_DIR", "/tmp/project");
    environment.put("CAT_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CAT_PLUGIN_DATA", "/tmp/plugin-data");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that Codex runtime reads CAT_CONFIG_DIR instead of Claude's config directory.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexRuntimeUsesCatConfigDir() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-config-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path codexHome = root.resolve("codex-home");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(codexHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", codexHome.toString());
      environment.put("CAT_RUNTIME", "codex");
      environment.put("CODEX_HOME", codexHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(codexHome);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that shared CLI scopes fail fast when {@code CAT_CONFIG_DIR} is absent.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_CONFIG_DIR is required and must not be blank.*")
  public void missingConfigDirFailsFast()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_SESSION_ID", "cat-session");
    environment.put("CAT_PROJECT_DIR", "/tmp/project");
    environment.put("CAT_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CAT_PLUGIN_DATA", "/tmp/plugin-data");
    environment.put("CAT_RUNTIME", "codex");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that shared CLI scopes fail fast when {@code CAT_RUNTIME} is absent.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_RUNTIME is required and must not be blank.*")
  public void missingRuntimeFailsFast()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_SESSION_ID", "cat-session");
    environment.put("CAT_PROJECT_DIR", "/tmp/project");
    environment.put("CAT_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CAT_PLUGIN_DATA", "/tmp/plugin-data");
    environment.put("CAT_CONFIG_DIR", "/tmp/cat-config");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Provides required CAT variables for blank-value fail-fast tests.
   *
   * @return the required CAT variables
   */
  @DataProvider
  public Object[][] requiredCatVariables()
  {
    return new Object[][]
    {
      {"CAT_SESSION_ID"},
      {"CAT_PROJECT_DIR"},
      {"CAT_PLUGIN_ROOT"},
      {"CAT_PLUGIN_DATA"},
      {"CAT_CONFIG_DIR"},
      {"CAT_RUNTIME"}
    };
  }

  /**
   * Verifies that required CAT variables reject blank values.
   *
   * @param variableName the variable to blank out
   */
  @Test(dataProvider = "requiredCatVariables", expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_.* is required and must not be blank.*")
  public void blankRequiredCatVariablesFailFast(String variableName)
  {
    Map<String, String> environment = validCodexEnvironment();
    environment.put(variableName, " \t");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that unsupported runtime values fail fast.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp =
      "(?s).*Unsupported CAT_RUNTIME: unsupported.*must be one of: claude, codex.*case-insensitive.*")
  public void unsupportedRuntimeFailsFast()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_SESSION_ID", "cat-session");
    environment.put("CAT_PROJECT_DIR", "/tmp/project");
    environment.put("CAT_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CAT_PLUGIN_DATA", "/tmp/plugin-data");
    environment.put("CAT_CONFIG_DIR", "/tmp/cat-config");
    environment.put("CAT_RUNTIME", "unsupported");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that blank optional config variables are ignored.
   *
   * @throws Exception if file operations fail
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_CONFIG_DIR is required and must not be blank.*")
  public void blankConfigVariablesAreIgnored() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-blank-config-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", " ");
      environment.put("CLAUDE_CONFIG_DIR", "\t");
      environment.put("CAT_RUNTIME", "codex");
      environment.put("CODEX_HOME", "");

      new MainCliTool(environment::get, root);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that CAT_CONFIG_DIR is used even when CODEX_HOME is present.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void catConfigDirIsUsedWhenCodexHomeIsPresent() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-cat-config-over-codex-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path catConfig = root.resolve("cat-config");
      Path codexHome = root.resolve("codex-home");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(catConfig);
      Files.createDirectories(codexHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", catConfig.toString());
      environment.put("CAT_RUNTIME", "codex");
      environment.put("CODEX_HOME", codexHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(catConfig);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that CODEX_HOME is ignored by shared CLI scope construction.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexHomeIsIgnoredBySharedCliScope() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-home-ignored-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path codexHome = root.resolve("codex-home");
      Path claudeConfig = root.resolve("claude-config");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(codexHome);
      Files.createDirectories(claudeConfig);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", claudeConfig.toString());
      environment.put("CAT_RUNTIME", "claude");
      environment.put("CODEX_HOME", codexHome.toString());
      environment.put("CLAUDE_CONFIG_DIR", claudeConfig.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(claudeConfig);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Returns a complete valid Codex environment.
   *
   * @return the environment
   */
  private static Map<String, String> validCodexEnvironment()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_SESSION_ID", "cat-session");
    environment.put("CAT_PROJECT_DIR", "/tmp/project");
    environment.put("CAT_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CAT_PLUGIN_DATA", "/tmp/plugin-data");
    environment.put("CAT_CONFIG_DIR", "/tmp/cat-config");
    environment.put("CAT_RUNTIME", "codex");
    return environment;
  }
}
