/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.cat.tool.CliEnvironment;
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
   * Verifies that optional environment values preserve blanks so callers can decide whether blanks are legal.
   */
  @Test
  public void optionalEnvironmentValueReturnsBlank()
  {
    Map<String, String> environment = Map.of("OPTIONAL_VALUE", " \t");

    String result = CliEnvironment.optional(environment::get, "OPTIONAL_VALUE", "default");

    requireThat(result, "result").isEqualTo(" \t");
  }

  /**
   * Verifies the Claude engine metadata used by shared CLI scope construction.
   */
  @Test
  public void claudeEngineMetadata()
  {
    Path project = Path.of("/workspace/project");
    Path pluginRoot = Path.of("/workspace/plugin");

    requireThat(AgentEngine.CLAUDE.pluginDescriptor(), "pluginDescriptor").
      isEqualTo(Path.of(".claude-plugin/plugin.json"));
    requireThat(AgentEngine.CLAUDE.pluginCacheDescriptor(), "pluginCacheDescriptor").isNull();
    requireThat(AgentEngine.CLAUDE.ruleDirectories(project, pluginRoot), "ruleDirectories").isEqualTo(List.of(
      pluginRoot.resolve("rules/common"),
      pluginRoot.resolve("rules/claude"),
      project.resolve(".cat/rules/common"),
      project.resolve(".cat/rules/claude"),
      project.resolve(".claude/rules")));
  }

  /**
   * Verifies the Codex engine metadata used by shared CLI scope construction.
   */
  @Test
  public void codexEngineMetadata()
  {
    Path project = Path.of("/workspace/project");
    Path pluginRoot = Path.of("/workspace/plugin");

    requireThat(AgentEngine.CODEX.pluginDescriptor(), "pluginDescriptor").
      isEqualTo(Path.of(".codex-plugin/plugin.json"));
    requireThat(AgentEngine.CODEX.pluginCacheDescriptor(), "pluginCacheDescriptor").
      isEqualTo(Path.of(".codex-plugin/plugin.json"));
    requireThat(AgentEngine.CODEX.ruleDirectories(project, pluginRoot), "ruleDirectories").isEqualTo(List.of(
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
      Path userHome = root.resolve("home");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(userHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", root.resolve("cat-config").toString());
      environment.put("CAT_ENGINE", "codex");
      environment.put("TZ", "America/New_York");
      Map<String, String> properties = Map.of("user.home", userHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, properties::get, root))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("cat-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(project);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(pluginRoot);
        requireThat(scope.getPluginData(), "pluginData").
          isEqualTo(userHome.resolve(".codex/plugins/data/cat-cat"));
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
   * Provides mixed-case engine values.
   *
   * @return mixed-case engine values and expected descriptors
   */
  @DataProvider
  public Object[][] mixedCaseEngines()
  {
    return new Object[][]
    {
      {"ClAuDe", AgentEngine.CLAUDE},
      {"CoDeX", AgentEngine.CODEX}
    };
  }

  /**
   * Verifies that CAT_ENGINE matching is case-insensitive for all supported engines.
   *
   * @param engineId the mixed-case engine identifier
   * @param expectedEngine the expected engine
   */
  @Test(dataProvider = "mixedCaseEngines")
  public void catEngineAcceptsMixedCase(String engineId, AgentEngine expectedEngine)
  {
    Map<String, String> environment = validCodexEnvironment();
    environment.put("CAT_ENGINE", engineId);

    try (MainCliTool scope = new MainCliTool(environment::get, Path.of("/tmp")))
    {
      requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
        isEqualTo(expectedEngine.pluginDescriptor());
    }
  }

  /**
   * Verifies that engine harness values take precedence when CAT_* aliases are also present.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void claudeHarnessVariablesTakePrecedenceOverCatAliases() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-precedence-");
    try
    {
      Path catProject = root.resolve("cat-project");
      Path catPluginRoot = root.resolve("client-plugin");
      Path catPluginData = root.resolve("client-plugin-data");
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
      environment.put("CAT_ENGINE", "codex");

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("claude-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(claudeProject);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(claudePluginRoot);
        requireThat(scope.getPluginData(), "pluginData").isEqualTo(claudePluginData);
        requireThat(scope.getConfigPath(), "configPath").
          isEqualTo(claudePluginData.resolve("claude-config"));
        requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
          isEqualTo(AgentEngine.CLAUDE.pluginDescriptor());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that shared CLI scopes derive values from Claude harness variables when CAT aliases are absent.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void claudeHarnessVariablesSatisfySharedScope() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-claude-harness-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path claudeConfig = root.resolve("claude-config");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(claudeConfig);

      Map<String, String> environment = new HashMap<>();
      environment.put("CLAUDE_SESSION_ID", "claude-session");
      environment.put("CLAUDE_PROJECT_DIR", project.toString());
      environment.put("CLAUDE_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CLAUDE_PLUGIN_DATA", pluginData.toString());
      environment.put("CLAUDE_CONFIG_DIR", claudeConfig.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, root))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("claude-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(project);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(pluginRoot);
        requireThat(scope.getPluginData(), "pluginData").isEqualTo(pluginData);
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(claudeConfig);
        requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
          isEqualTo(AgentEngine.CLAUDE.pluginDescriptor());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that shared CLI scopes derive values from Codex harness variables and launcher context.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexHarnessVariablesAndLauncherContextSatisfySharedScope() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-harness-");
    try
    {
      Path project = root.resolve("project");
      Path codexHome = root.resolve("codex-home");
      Path pluginRoot = root.resolve("plugin");
      Path launcherDir = pluginRoot.resolve("client/bin");
      Files.createDirectories(project);
      Files.createDirectories(codexHome);
      Files.createDirectories(launcherDir);

      Map<String, String> environment = new HashMap<>();
      environment.put("CODEX_THREAD_ID", "codex-session");
      environment.put("CODEX_HOME", codexHome.toString());
      Map<String, String> properties = Map.of("cat.launcher.dir", launcherDir.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, properties::get, project))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("codex-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(project);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(pluginRoot);
        requireThat(scope.getPluginData(), "pluginData").
          isEqualTo(codexHome.resolve("plugins/data/cat-cat"));
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(codexHome);
        requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
          isEqualTo(AgentEngine.CODEX.pluginDescriptor());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that the Codex launcher path can identify the engine when engine environment values are absent.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexLauncherContextSatisfiesEngineSelection() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-launcher-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("cat/codex-plugin");
      Path launcherDir = pluginRoot.resolve("client/bin");
      Path pluginData = root.resolve("plugin-data");
      Path userHome = root.resolve("home");
      Files.createDirectories(project);
      Files.createDirectories(launcherDir);
      Files.createDirectories(pluginData);
      Files.createDirectories(userHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      Map<String, String> properties = Map.of(
        "cat.launcher.dir", launcherDir.toString(),
        "user.home", userHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, properties::get, root))
      {
        requireThat(scope.getSessionId(), "sessionId").isEqualTo("cat-session");
        requireThat(scope.getProjectPath(), "projectPath").isEqualTo(project);
        requireThat(scope.getPluginRoot(), "pluginRoot").isEqualTo(pluginRoot);
        requireThat(scope.getPluginData(), "pluginData").
          isEqualTo(userHome.resolve(".codex/plugins/data/cat-cat"));
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(userHome.resolve(".codex"));
        requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
          isEqualTo(AgentEngine.CODEX.pluginDescriptor());
        requireThat(scope.getRuleDirectories(), "ruleDirectories").
          isEqualTo(AgentEngine.CODEX.ruleDirectories(project, pluginRoot));
        requireThat(scope.getPluginCacheDescriptor(), "pluginCacheDescriptor").
          isEqualTo(AgentEngine.CODEX.pluginCacheDescriptor());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that mixed engine harness values fail fast unless the engine is explicitly disambiguated.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*Engine harness is ambiguous.*CAT_ENGINE is required.*")
  public void mixedEngineHarnessValuesRequireExplicitEngine()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CLAUDE_SESSION_ID", "claude-session");
    environment.put("CLAUDE_PROJECT_DIR", "/tmp/claude-project");
    environment.put("CLAUDE_PLUGIN_ROOT", "/tmp/claude-plugin");
    environment.put("CLAUDE_PLUGIN_DATA", "/tmp/claude-plugin-data");
    environment.put("CLAUDE_CONFIG_DIR", "/tmp/claude-config");
    environment.put("CODEX_THREAD_ID", "codex-session");
    environment.put("CODEX_HOME", "/tmp/codex-home");
    new MainCliTool(environment::get, Path.of("/tmp/project"));
  }

  /**
   * Provides explicit engine disambiguation cases.
   *
   * @return engine IDs and expected engines
   */
  @DataProvider
  public Object[][] explicitEngineDisambiguation()
  {
    return new Object[][]
    {
      {"claude", AgentEngine.CLAUDE, "claude-session"},
      {"codex", AgentEngine.CODEX, "codex-session"}
    };
  }

  /**
   * Verifies that {@code CAT_ENGINE} disambiguates mixed engine harness values.
   *
   * @param engineId the explicit engine ID
   * @param expectedEngine the expected engine
   * @param expectedSessionId the expected session ID
   */
  @Test(dataProvider = "explicitEngineDisambiguation")
  public void catEngineDisambiguatesMixedEngineHarnessValues(String engineId,
    AgentEngine expectedEngine, String expectedSessionId)
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_ENGINE", engineId);
    environment.put("CLAUDE_SESSION_ID", "claude-session");
    environment.put("CLAUDE_PROJECT_DIR", "/tmp/claude-project");
    environment.put("CLAUDE_PLUGIN_ROOT", "/tmp/claude-plugin");
    environment.put("CLAUDE_PLUGIN_DATA", "/tmp/claude-plugin-data");
    environment.put("CLAUDE_CONFIG_DIR", "/tmp/claude-config");
    environment.put("CODEX_THREAD_ID", "codex-session");
    environment.put("CODEX_HOME", "/tmp/codex-home");
    Map<String, String> properties = Map.of("cat.launcher.dir", "/tmp/plugin/client/bin");

    try (MainCliTool scope = new MainCliTool(environment::get, properties::get, Path.of("/tmp/project")))
    {
      requireThat(scope.getSessionId(), "sessionId").isEqualTo(expectedSessionId);
      requireThat(scope.getPluginDescriptor(), "pluginDescriptor").
        isEqualTo(expectedEngine.pluginDescriptor());
    }
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
    environment.put("CAT_ENGINE", "codex");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that Codex engine derives its config directory from CODEX_HOME when CAT_CONFIG_DIR is absent.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexEngineDerivesConfigDirFromCodexHome() throws Exception
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
      environment.put("CAT_ENGINE", "codex");
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
   * Verifies that Codex engine falls back to {@code CAT_CONFIG_DIR} when {@code CODEX_HOME} is absent.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexEngineUsesCatConfigDirWhenCodexHomeIsAbsent() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-cat-config-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path catConfig = root.resolve("cat-config");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(catConfig);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_CONFIG_DIR", catConfig.toString());
      environment.put("CAT_ENGINE", "codex");

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
   * Verifies that Claude engine derives its config directory from CLAUDE_CONFIG_DIR when CAT_CONFIG_DIR is absent.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void claudeEngineDerivesConfigDirFromClaudeConfigDir() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-claude-config-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path claudeConfig = root.resolve("claude-config");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(claudeConfig);

      Map<String, String> environment = new HashMap<>();
      environment.put("CAT_SESSION_ID", "cat-session");
      environment.put("CAT_PROJECT_DIR", project.toString());
      environment.put("CAT_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_ENGINE", "claude");
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
   * Verifies that Claude engine defaults its config directory to {@code user.home/.claude}.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void claudeEngineDefaultsConfigDirToUserHome() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-claude-home-config-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path pluginData = root.resolve("plugin-data");
      Path userHome = root.resolve("home");
      Files.createDirectories(project);
      Files.createDirectories(pluginRoot);
      Files.createDirectories(pluginData);
      Files.createDirectories(userHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CLAUDE_SESSION_ID", "claude-session");
      environment.put("CLAUDE_PROJECT_DIR", project.toString());
      environment.put("CLAUDE_PLUGIN_ROOT", pluginRoot.toString());
      environment.put("CLAUDE_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_ENGINE", "claude");
      Map<String, String> properties = Map.of("user.home", userHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, properties::get, root))
      {
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(userHome.resolve(".claude"));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that Codex engine defaults its config directory to {@code user.home/.codex}.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexEngineDefaultsConfigDirToUserHome() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-home-config-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path launcherDir = pluginRoot.resolve("client/bin");
      Path pluginData = root.resolve("plugin-data");
      Path userHome = root.resolve("home");
      Files.createDirectories(project);
      Files.createDirectories(launcherDir);
      Files.createDirectories(pluginData);
      Files.createDirectories(userHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CODEX_THREAD_ID", "codex-session");
      environment.put("CAT_PLUGIN_DATA", pluginData.toString());
      environment.put("CAT_ENGINE", "codex");
      Map<String, String> properties = Map.of(
        "cat.launcher.dir", launcherDir.toString(),
        "user.home", userHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, properties::get, project))
      {
        requireThat(scope.getConfigPath(), "configPath").isEqualTo(userHome.resolve(".codex"));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that Codex engine defaults its plugin data directory to {@code user.home/.codex/plugins/data/cat-cat}.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexEngineDefaultsPluginDataDirToUserHome() throws Exception
  {
    Path root = Files.createTempDirectory("main-cli-tool-codex-home-plugin-data-");
    try
    {
      Path project = root.resolve("project");
      Path pluginRoot = root.resolve("plugin");
      Path launcherDir = pluginRoot.resolve("client/bin");
      Path userHome = root.resolve("home");
      Files.createDirectories(project);
      Files.createDirectories(launcherDir);
      Files.createDirectories(userHome);

      Map<String, String> environment = new HashMap<>();
      environment.put("CODEX_THREAD_ID", "codex-session");
      Map<String, String> properties = Map.of(
        "cat.launcher.dir", launcherDir.toString(),
        "user.home", userHome.toString());

      try (MainCliTool scope = new MainCliTool(environment::get, properties::get, project))
      {
        requireThat(scope.getPluginData(), "pluginData").
          isEqualTo(userHome.resolve(".codex/plugins/data/cat-cat"));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(root);
    }
  }

  /**
   * Verifies that shared CLI scopes fail fast when {@code CAT_ENGINE} is absent.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_ENGINE is required and must not be blank.*")
  public void missingEngineFailsFast()
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
   * Provides blank CAT aliases.
   *
   * @return blank CAT aliases
   */
  @DataProvider
  public Object[][] blankCatAliases()
  {
    return new Object[][]
    {
      {"CAT_SESSION_ID"},
      {"CAT_PROJECT_DIR"},
      {"CAT_PLUGIN_ROOT"},
      {"CAT_PLUGIN_DATA"},
      {"CAT_CONFIG_DIR"},
      {"CAT_ENGINE"}
    };
  }

  /**
   * Verifies that blank CAT aliases fail fast even when harness values can derive scope values.
   *
   * @param name the blank CAT alias
   */
  @Test(dataProvider = "blankCatAliases", expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_.* is required and must not be blank.*")
  public void blankCatAliasesFailFast(String name)
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CODEX_THREAD_ID", "codex-session");
    environment.put("CODEX_HOME", "/tmp/codex-home");
    environment.put(name, " \t");

    Map<String, String> properties = Map.of("cat.launcher.dir", "/tmp/plugin/client/bin");
    new MainCliTool(environment::get, properties::get, Path.of("/tmp/project"));
  }

  /**
   * Verifies that unsupported engine values fail fast.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp =
      "(?s).*Unsupported CAT_ENGINE: unsupported.*must be one of: claude, codex.*case-insensitive.*")
  public void unsupportedEngineFailsFast()
  {
    Map<String, String> environment = new HashMap<>();
    environment.put("CAT_SESSION_ID", "cat-session");
    environment.put("CAT_PROJECT_DIR", "/tmp/project");
    environment.put("CAT_PLUGIN_ROOT", "/tmp/plugin");
    environment.put("CAT_PLUGIN_DATA", "/tmp/plugin-data");
    environment.put("CAT_CONFIG_DIR", "/tmp/cat-config");
    environment.put("CAT_ENGINE", "unsupported");
    new MainCliTool(environment::get, Path.of("/tmp"));
  }

  /**
   * Verifies that blank engine config variables fail fast.
   *
   * @throws Exception if file operations fail
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CAT_CONFIG_DIR is required and must not be blank.*")
  public void blankCatConfigDirFailsFast()
  {
    Map<String, String> environment = validCodexEnvironment();
    environment.put("CODEX_HOME", "/tmp/codex-home");
    environment.put("CAT_CONFIG_DIR", " ");

    new MainCliTool(environment::get, Path.of("/tmp/project"));
  }

  /**
   * Verifies that blank Codex harness config variables fail fast.
   */
  @Test(expectedExceptions = AssertionError.class,
    expectedExceptionsMessageRegExp = "(?s).*CODEX_HOME is required and must not be blank.*")
  public void blankCodexHomeFailsFast()
  {
    Map<String, String> environment = validCodexEnvironment();
    environment.remove("CAT_CONFIG_DIR");
    environment.put("CODEX_HOME", "\t");

    new MainCliTool(environment::get, Path.of("/tmp/project"));
  }

  /**
   * Verifies that CODEX_HOME is used even when CAT_CONFIG_DIR is present.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexHomeIsUsedWhenCatConfigDirIsPresent() throws Exception
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
      environment.put("CAT_ENGINE", "codex");
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
   * Verifies that Claude engine ignores CODEX_HOME while deriving shared CLI scope configuration.
   *
   * @throws Exception if file operations fail
   */
  @Test
  public void codexHomeIsIgnoredByClaudeSharedCliScope() throws Exception
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
      environment.put("CAT_ENGINE", "claude");
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
    environment.put("CAT_ENGINE", "codex");
    return environment;
  }
}
