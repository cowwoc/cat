/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AbstractEngineScope;
import io.github.cowwoc.cat.agent.AgentEngine;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Shared base implementation for CLI scopes.
 * <p>
 * Engine-specific subclasses provide resolved plugin descriptor and rule directory values.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractCliTool extends AbstractEngineScope implements CliTool
{
  /**
   * Resolved session ID derived by this scope.
   */
  private final String sessionId;
  /**
   * Resolved engine config directory derived by this scope.
   */
  private final Path configPath;
  /**
   * Resolved plugin.json URL derived by this scope.
   */
  private final String pluginJsonUrl;
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<DisplayUtils> displayUtils = ConcurrentLazyReference.create(() ->
  {
    try
    {
      return new DisplayUtils(this);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  });

  /**
   * Creates a new CLI scope by deriving values from the environment and JVM context.
   *
   * @param environment resolves environment variable names to values
   * @param systemProperty resolves system property names to values
   * @param workDir the process working directory
   */
  protected AbstractCliTool(Function<String, String> environment,
    Function<String, String> systemProperty, Path workDir)
  {
    this(new CliScopeResolver(environment, systemProperty, workDir));
  }

  /**
   * Creates a new CLI scope by deriving values for a specific engine.
   *
   * @param engine the engine to derive values for
   * @param environment resolves environment variable names to values
   * @param systemProperty resolves system property names to values
   * @param workDir the process working directory
   */
  protected AbstractCliTool(AgentEngine engine, Function<String, String> environment,
    Function<String, String> systemProperty, Path workDir)
  {
    this(new CliScopeResolver(engine, environment, systemProperty, workDir));
  }

  /**
   * Creates a new CLI scope from a value resolver.
   *
   * @param resolver the CLI scope value resolver
   */
  private AbstractCliTool(CliScopeResolver resolver)
  {
    this(resolver.sessionId(), resolver.projectPath(), resolver.pluginRoot(), resolver.pluginData(),
      resolver.configPath(), resolver.pluginDescriptor(), resolver.ruleDirectories(),
      resolver.pluginCacheDescriptor(), resolver.workDir(), resolver.timezone(),
      resolver.pluginJsonUrl());
  }

  /**
   * Creates a new CLI scope from resolved values.
   *
   * @param sessionId the session ID
   * @param projectPath the project directory
   * @param pluginRoot the plugin root directory
   * @param pluginData the plugin data directory
   * @param configPath the active engine config directory
   * @param pluginDescriptor the plugin descriptor path relative to the plugin root
   * @param ruleDirectories the ordered rule directories
   * @param pluginCacheDescriptor the plugin cache descriptor path relative to the plugin root, or {@code null}
   * @param workDir the process working directory
   * @param timezone the timezone
   * @param pluginJsonUrl the plugin.json URL
   */
  protected AbstractCliTool(String sessionId, Path projectPath, Path pluginRoot, Path pluginData,
    Path configPath, Path pluginDescriptor, List<Path> ruleDirectories, Path pluginCacheDescriptor,
    Path workDir, String timezone, String pluginJsonUrl)
  {
    super(projectPath, pluginRoot, pluginData, pluginDescriptor, ruleDirectories, pluginCacheDescriptor,
      workDir.toAbsolutePath(), timezone);
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(configPath, "configPath").isNotNull();
    requireThat(pluginJsonUrl, "pluginJsonUrl").isNotNull();
    this.sessionId = sessionId;
    this.configPath = configPath;
    this.pluginJsonUrl = pluginJsonUrl;
  }

  @Override
  public String getSessionId()
  {
    ensureOpen();
    return sessionId;
  }

  @Override
  public Path getConfigPath()
  {
    ensureOpen();
    return configPath;
  }

  @Override
  public Path getSessionsPath()
  {
    ensureOpen();
    return configPath.resolve("projects").
      resolve(encodeProjectPath(getProjectPath().toString()));
  }

  @Override
  public Path getSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getSessionsPath().resolve(sessionId);
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    ensureOpen();
    return displayUtils.getValue();
  }

  @Override
  public String getPluginJsonUrl()
  {
    ensureOpen();
    return pluginJsonUrl;
  }

  /**
   * Assembles resolved CLI scope values from focused engine helpers.
   */
  private static final class CliScopeResolver
  {
    private final AgentEngine engine;
    private final ExplicitValues values;
    private final EngineValueResolver engineValues;
    private final Path workDir;

    /**
     * Creates a new resolver.
     *
     * @param environment resolves environment variable names to values
     * @param systemProperty resolves system property names to values
     * @param workDir the process working directory
     */
    private CliScopeResolver(Function<String, String> environment,
      Function<String, String> systemProperty, Path workDir)
    {
      this(null, environment, systemProperty, workDir);
    }

    /**
     * Creates a new resolver for a specific engine.
     *
     * @param engine the engine to derive values for, or {@code null} to infer from context
     * @param environment resolves environment variable names to values
     * @param systemProperty resolves system property names to values
     * @param workDir the process working directory
     */
    private CliScopeResolver(AgentEngine engine, Function<String, String> environment,
      Function<String, String> systemProperty, Path workDir)
    {
      requireThat(workDir, "workDir").isNotNull();
      this.values = new ExplicitValues(environment, systemProperty);
      this.workDir = workDir;
      EngineDetector engineDetector = new EngineDetector(values);
      if (engine == null)
        this.engine = engineDetector.resolveEngine();
      else
        this.engine = engine;
      this.engineValues = new EngineValueResolver(this.engine, values, workDir);
    }

    /**
     * Returns the resolved session ID.
     *
     * @return the session ID
     */
    private String sessionId()
    {
      return engineValues.sessionId();
    }

    /**
     * Returns the resolved project path.
     *
     * @return the project path
     */
    private Path projectPath()
    {
      return engineValues.projectPath();
    }

    /**
     * Returns the resolved plugin root.
     *
     * @return the plugin root
     */
    private Path pluginRoot()
    {
      return engineValues.pluginRoot();
    }

    /**
     * Returns the resolved plugin data directory.
     *
     * @return the plugin data directory
     */
    private Path pluginData()
    {
      return engineValues.pluginData();
    }

    /**
     * Returns the resolved engine config directory.
     *
     * @return the engine config directory
     */
    private Path configPath()
    {
      return engineValues.configPath();
    }

    /**
     * Returns the plugin descriptor path.
     *
     * @return the plugin descriptor path
     */
    private Path pluginDescriptor()
    {
      return engine.pluginDescriptor();
    }

    /**
     * Returns the ordered rule directories.
     *
     * @return the ordered rule directories
     */
    private List<Path> ruleDirectories()
    {
      return engine.ruleDirectories(projectPath(), pluginRoot());
    }

    /**
     * Returns the plugin cache descriptor path.
     *
     * @return the plugin cache descriptor path
     */
    private Path pluginCacheDescriptor()
    {
      return engine.pluginCacheDescriptor();
    }

    /**
     * Returns the process working directory.
     *
     * @return the process working directory
     */
    private Path workDir()
    {
      return workDir;
    }

    /**
     * Returns the timezone.
     *
     * @return the timezone
     */
    private String timezone()
    {
      return values.optionalEnvironmentValue("TZ", "UTC");
    }

    /**
     * Returns the plugin.json URL.
     *
     * @return the plugin.json URL
     */
    private String pluginJsonUrl()
    {
      return values.optionalEnvironmentValue("CAT_PLUGIN_JSON_URL", "");
    }
  }

  /**
   * Detects the active engine from engine harness and CAT fallback values.
   */
  private static final class EngineDetector
  {
    private final ExplicitValues values;

    /**
     * Creates a new engine detector.
     *
     * @param values the explicit value reader
     */
    private EngineDetector(ExplicitValues values)
    {
      this.values = values;
    }

    /**
     * Resolves the active engine.
     *
     * @return the active engine
     */
    private AgentEngine resolveEngine()
    {
      boolean hasClaudeHarness = values.hasEnvironmentValue("CLAUDE_SESSION_ID", "CLAUDE_PROJECT_DIR",
        "CLAUDE_PLUGIN_ROOT", "CLAUDE_PLUGIN_DATA", "CLAUDE_CONFIG_DIR");
      boolean hasCodexHarness = values.hasEnvironmentValue("CODEX_THREAD_ID", "CODEX_HOME") ||
        values.looksLikeCodexLauncher();
      String catEngine = values.environmentValue("CAT_ENGINE");
      if (hasClaudeHarness && hasCodexHarness)
      {
        if (catEngine != null)
          return AgentEngine.fromId(catEngine);
        throw new AssertionError("Engine harness is ambiguous: both Claude and Codex values are present; " +
          "CAT_ENGINE is required and must not be blank");
      }
      if (hasClaudeHarness)
        return AgentEngine.CLAUDE;
      if (hasCodexHarness)
        return AgentEngine.CODEX;
      if (catEngine != null)
        return AgentEngine.fromId(catEngine);
      throw new AssertionError("CAT_ENGINE is required and must not be blank");
    }
  }

  /**
   * Derives engine-dependent CLI values from explicit engine inputs.
   */
  private static final class EngineValueResolver
  {
    private final AgentEngine engine;
    private final ExplicitValues values;
    private final Path workDir;

    /**
     * Creates a new engine value resolver.
     *
     * @param engine the active engine
     * @param values the explicit value reader
     * @param workDir the process working directory
     */
    private EngineValueResolver(AgentEngine engine, ExplicitValues values, Path workDir)
    {
      this.engine = engine;
      this.values = values;
      this.workDir = workDir;
    }

    /**
     * Resolves the session ID.
     *
     * @return the session ID
     */
    private String sessionId()
    {
      String derivedSessionId = switch (engine)
      {
        case CLAUDE -> values.environmentValue("CLAUDE_SESSION_ID");
        case CODEX -> values.environmentValue("CODEX_THREAD_ID");
      };
      if (derivedSessionId != null)
        return derivedSessionId;
      return values.requiredEnvironmentValue("CAT_SESSION_ID");
    }

    /**
     * Resolves the project path.
     *
     * @return the project path
     */
    private Path projectPath()
    {
      String claudeProjectDir = values.environmentValue("CLAUDE_PROJECT_DIR");
      if (engine == AgentEngine.CLAUDE && claudeProjectDir != null)
        return Path.of(claudeProjectDir);
      if (engine == AgentEngine.CODEX && values.hasEnvironmentValue("CODEX_THREAD_ID", "CODEX_HOME"))
        return workDir.toAbsolutePath().normalize();
      return Path.of(values.requiredEnvironmentValue("CAT_PROJECT_DIR"));
    }

    /**
     * Resolves the plugin root.
     *
     * @return the plugin root
     */
    private Path pluginRoot()
    {
      String claudePluginRoot = values.environmentValue("CLAUDE_PLUGIN_ROOT");
      if (engine == AgentEngine.CLAUDE && claudePluginRoot != null)
        return Path.of(claudePluginRoot);
      String explicitProperty = values.systemProperty("cat.plugin.root");
      if (explicitProperty != null)
        return Path.of(explicitProperty);
      Path launcherDir = values.launcherDir();
      if (launcherDir.getParent() != null && launcherDir.getParent().getParent() != null)
        return launcherDir.getParent().getParent().toAbsolutePath().normalize();
      String catPluginRoot = values.environmentValue("CAT_PLUGIN_ROOT");
      if (catPluginRoot != null)
        return Path.of(catPluginRoot);
      throw new AssertionError("CAT_PLUGIN_ROOT is required and must not be blank");
    }

    /**
     * Resolves the plugin data directory.
     *
     * @return the plugin data directory
     */
    private Path pluginData()
    {
      String claudePluginData = values.environmentValue("CLAUDE_PLUGIN_DATA");
      if (engine == AgentEngine.CLAUDE && claudePluginData != null)
        return Path.of(claudePluginData);
      if (engine == AgentEngine.CODEX)
      {
        String codexHome = values.environmentValue("CODEX_HOME");
        Path base;
        if (codexHome != null)
          base = Path.of(codexHome);
        else
          base = userHome().resolve(".codex");
        return base.resolve("plugins/data/cat-cat");
      }
      return Path.of(values.requiredEnvironmentValue("CAT_PLUGIN_DATA"));
    }

    /**
     * Resolves the engine config directory.
     *
     * @return the config directory
     */
    private Path configPath()
    {
      String engineConfig = switch (engine)
      {
        case CLAUDE -> values.environmentValue("CLAUDE_CONFIG_DIR");
        case CODEX -> values.environmentValue("CODEX_HOME");
      };
      if (engineConfig != null)
        return Path.of(engineConfig);
      String catConfigDir = values.environmentValue("CAT_CONFIG_DIR");
      if (catConfigDir != null)
        return Path.of(catConfigDir);
      return switch (engine)
      {
        case CLAUDE -> userHome().resolve(".claude");
        case CODEX -> userHome().resolve(".codex");
      };
    }

    /**
     * Resolves the current user's home directory.
     *
     * @return the current user's home directory
     */
    private Path userHome()
    {
      String value = values.systemProperty("user.home");
      if (value != null)
        return Path.of(value);
      return Path.of(System.getProperty("user.home"));
    }
  }

  /**
   * Reads explicit environment and system-property values while enforcing blank-value rules.
   */
  private static final class ExplicitValues
  {
    private final Function<String, String> environment;
    private final Function<String, String> systemProperty;

    /**
     * Creates a new explicit value reader.
     *
     * @param environment resolves environment variable names to values
     * @param systemProperty resolves system property names to values
     */
    private ExplicitValues(Function<String, String> environment, Function<String, String> systemProperty)
    {
      requireThat(environment, "environment").isNotNull();
      requireThat(systemProperty, "systemProperty").isNotNull();
      validateEnvironmentValues(environment, "CAT_ENGINE", "CAT_SESSION_ID", "CAT_PROJECT_DIR",
        "CAT_PLUGIN_ROOT", "CAT_PLUGIN_DATA", "CAT_CONFIG_DIR");
      this.environment = environment;
      this.systemProperty = systemProperty;
    }

    /**
     * Fails fast when any named environment variable is present but blank.
     *
     * @param environment resolves environment variable names to values
     * @param names environment variable names
     */
    private static void validateEnvironmentValues(Function<String, String> environment, String... names)
    {
      for (String name : names)
        environmentValue(environment, name);
    }

    /**
     * Returns {@code true} if any named environment variable is present and non-blank.
     *
     * @param names environment variable names
     * @return {@code true} if any value is present and non-blank
     */
    private boolean hasEnvironmentValue(String... names)
    {
      for (String name : names)
      {
        String value = environmentValue(name);
        if (value != null)
          return true;
      }
      return false;
    }

    /**
     * Reads an optional explicit environment value, failing fast when the variable is present but blank.
     *
     * @param name the environment variable name
     * @return the environment value, or {@code null} when unset
     */
    private String environmentValue(String name)
    {
      return environmentValue(environment, name);
    }

    /**
     * Reads an optional explicit environment value, failing fast when the variable is present but blank.
     *
     * @param environment resolves environment variable names to values
     * @param name the environment variable name
     * @return the environment value, or {@code null} when unset
     */
    private static String environmentValue(Function<String, String> environment, String name)
    {
      String value = environment.apply(name);
      if (value == null)
        return null;
      if (value.isBlank())
        throw new AssertionError(name + " is required and must not be blank");
      return value;
    }

    /**
     * Reads a required environment value.
     *
     * @param name the environment variable name
     * @return the environment value
     */
    private String requiredEnvironmentValue(String name)
    {
      return CliEnvironment.required(environment, name);
    }

    /**
     * Reads an optional environment value.
     *
     * @param name the environment variable name
     * @param defaultValue the value to return when unset
     * @return the environment value, or {@code defaultValue} when unset
     */
    private String optionalEnvironmentValue(String name, String defaultValue)
    {
      return CliEnvironment.optional(environment, name, defaultValue);
    }

    /**
     * Reads an optional explicit system property, failing fast when the property is present but blank.
     *
     * @param name the system property name
     * @return the system property value, or {@code null} when unset
     */
    private String systemProperty(String name)
    {
      String value = systemProperty.apply(name);
      if (value == null)
        return null;
      if (value.isBlank())
        throw new AssertionError(name + " is required and must not be blank");
      return value;
    }

    /**
     * Resolves the launcher directory from system properties.
     *
     * @return the launcher directory, or an empty relative path when unset
     */
    private Path launcherDir()
    {
      String value = systemProperty("cat.launcher.dir");
      if (value == null)
        return Path.of("");
      return Path.of(value);
    }

    /**
     * Returns {@code true} if the launcher path indicates the Codex engine.
     *
     * @return {@code true} if the launcher is Codex-specific
     */
    private boolean looksLikeCodexLauncher()
    {
      return launcherDir().toString().contains("codex");
    }
  }
}
