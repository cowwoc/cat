/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.CheckDataMigration;
import io.github.cowwoc.cat.agent.InjectCriticalThinking;
import io.github.cowwoc.cat.agent.SessionStartDispatcher;
import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.hook.HookJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Shared implementation for Codex hooks that inject context.
 */
public abstract class AbstractCodexContextHook
{
  /**
   * Prevents construction except by hook subclasses.
   */
  protected AbstractCodexContextHook()
  {
  }

  /**
   * Creates a production Codex hook scope from native launcher context.
   *
   * @param in standard input containing the native Codex hook payload
   * @param environment process environment values
   * @param workingDirectory the process working directory
   * @return the hook scope
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if required paths cannot be resolved
   */
  public CodexHookScope createScope(InputStream in, Map<String, String> environment,
    Path workingDirectory)
  {
    requireThat(in, "in").isNotNull();
    requireThat(environment, "environment").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    Path resolvedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
    JsonNode nativeInput = CodexHookInput.read(in);
    Path projectRoot = resolveProjectRoot(nativeInput, environment, resolvedWorkingDirectory);
    Path pluginRoot = resolvePluginRoot(nativeInput, environment, resolvedWorkingDirectory);
    Path pluginData = resolvePluginData(nativeInput, environment);
    String timezone = getEnvironment(environment, "TZ");
    return new ProductionCodexHookScope(projectRoot, pluginRoot, pluginData,
      resolvedWorkingDirectory, timezone, nativeInput.toString());
  }

  /**
   * Runs Codex context-injection handlers for an initialized scope.
   *
   * @param scope the Codex hook scope
   * @param hookEventName the event name to report in the Codex hook response
   * @param includeSessionStartHandlers true to run SessionStart-only handlers such as migrations
   * @return the hook output and warnings
   * @throws NullPointerException if any parameter is null
   */
  protected HookResult runContextHook(CodexHookScope scope, String hookEventName,
    boolean includeSessionStartHandlers)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(hookEventName, "hookEventName").isNotBlank();
    JsonNode nativeInput = CodexHookInput.read(scope.getHookInput());
    MigrationNotice migrationNotice = new MigrationNotice(new CheckDataMigration(scope));
    List<SessionStartHandler> handlers;
    SessionStartHandler ruleLoader = () ->
    {
      String content = CodexSessionRules.load(scope, nativeInput);
      if (content.isBlank())
        return SessionStartHandler.Result.empty();
      return SessionStartHandler.Result.context(content);
    };
    if (includeSessionStartHandlers)
    {
      handlers = List.of(
        migrationNotice,
        ruleLoader,
        new InjectCriticalThinking());
    }
    else
    {
      handlers = List.of(ruleLoader);
    }
    SessionStartDispatcher.Result result = SessionStartDispatcher.run(handlers);

    ObjectNode hookSpecificOutput = scope.getJsonMapper().createObjectNode();
    hookSpecificOutput.put("hookEventName", hookEventName);
    hookSpecificOutput.put("additionalContext", result.additionalContext());

    ObjectNode output = scope.getJsonMapper().createObjectNode();
    output.set("hookSpecificOutput", hookSpecificOutput);
    if (includeSessionStartHandlers && migrationNotice.ran())
    {
      output.put("systemMessage", "CAT plugin migration updated installed assets. Restart " +
        "Codex before using CAT custom agents so the tool registry sees the changes.");
    }
    return new HookResult(scope.getJsonMapper().writeValueAsString(output), result.warnings());
  }

  /**
   * Resolves the project root from native Codex input, then environment, then the process directory.
   *
   * @param nativeInput the native Codex hook payload
   * @param environment process environment values
   * @param workingDirectory the fallback working directory
   * @return the resolved project root
   */
  private static Path resolveProjectRoot(JsonNode nativeInput, Map<String, String> environment,
    Path workingDirectory)
  {
    String value = HookJson.firstString(nativeInput.get("cwd"), nativeInput.get("workdir"),
      nativeInput.get("project_dir"), nativeInput.get("projectRoot"),
      nativeInput.get("workspace_root"));
    if (value.isBlank())
      value = getEnvironment(environment, "CAT_PROJECT_DIR");
    if (value.isBlank())
      return workingDirectory;
    return toAbsolutePath(value, "projectRoot");
  }

  /**
   * Resolves the installed Codex plugin root.
   *
   * @param nativeInput the native Codex hook payload
   * @param environment process environment values
   * @param workingDirectory the directory to search from when no explicit path is provided
   * @return the resolved plugin root
   */
  private static Path resolvePluginRoot(JsonNode nativeInput, Map<String, String> environment,
    Path workingDirectory)
  {
    String value = HookJson.firstString(nativeInput.get("plugin_root"), nativeInput.get("pluginRoot"),
      nativeInput.get("cat_plugin_root"));
    if (value.isBlank())
      value = getEnvironment(environment, "CAT_PLUGIN_ROOT");
    if (value.isBlank())
      value = getEnvironment(environment, "PLUGIN_ROOT");
    if (!value.isBlank())
      return toAbsolutePath(value, "pluginRoot");
    return findPluginRootNearLauncher(workingDirectory);
  }

  /**
   * Resolves the Codex plugin data directory.
   *
   * @param nativeInput the native Codex hook payload
   * @param environment process environment values
   * @return the resolved plugin data directory
   */
  private static Path resolvePluginData(JsonNode nativeInput, Map<String, String> environment)
  {
    String value = HookJson.firstString(nativeInput.get("plugin_data"), nativeInput.get("pluginData"),
      nativeInput.get("cat_plugin_data"));
    if (value.isBlank())
      value = getEnvironment(environment, "CAT_PLUGIN_DATA");
    if (!value.isBlank())
      return toAbsolutePath(value, "pluginData");
    String codexHome = getEnvironment(environment, "CODEX_HOME");
    if (codexHome.isBlank())
      codexHome = Path.of(System.getProperty("user.home"), ".codex").toString();
    return toAbsolutePath(codexHome, "codexHome").resolve("plugins").resolve("data").
      resolve("cat-cat");
  }

  /**
   * Finds a source or installed plugin root near the process working directory.
   *
   * @param workingDirectory the directory to search from
   * @return the discovered plugin root
   * @throws IllegalArgumentException if no plugin root can be found
   */
  private static Path findPluginRootNearLauncher(Path workingDirectory)
  {
    String launcherDir = System.getProperty("cat.launcher.dir", "").strip();
    if (!launcherDir.isEmpty())
    {
      Path launcherPath = Path.of(launcherDir);
      for (Path candidate = launcherPath; candidate != null; candidate = candidate.getParent())
      {
        if (candidate.resolve(".codex-plugin/plugin.json").toFile().isFile())
          return candidate;
      }
    }
    for (Path candidate = workingDirectory; candidate != null; candidate = candidate.getParent())
    {
      if (candidate.resolve(".codex-plugin/plugin.json").toFile().isFile())
        return candidate;
      Path sourcePlugin = candidate.resolve("plugin");
      if (sourcePlugin.resolve(".codex-plugin/plugin.json").toFile().isFile())
        return sourcePlugin;
    }
    throw new IllegalArgumentException("CAT_PLUGIN_ROOT or Codex PLUGIN_ROOT is required when the " +
      "plugin root cannot be discovered from the launcher path or working directory.");
  }

  /**
   * Reads an environment value.
   *
   * @param environment process environment values
   * @param name the environment variable name
   * @return the environment value, or an empty string when unset
   */
  private static String getEnvironment(Map<String, String> environment, String name)
  {
    String value = environment.get(name);
    if (value == null)
      return "";
    return value.strip();
  }

  /**
   * Normalizes blank timezone values to UTC.
   *
   * @param value raw timezone value
   * @return stripped timezone, or {@code UTC} if blank
   */
  private static String normalizeTimezone(String value)
  {
    if (value == null || value.isBlank())
      return "UTC";
    return value.strip();
  }

  /**
   * Converts a path string into an absolute normalized path.
   *
   * @param value the path string
   * @param name the logical path name
   * @return the absolute normalized path
   */
  private static Path toAbsolutePath(String value, String name)
  {
    requireThat(value, name).isNotBlank();
    return Path.of(value).toAbsolutePath().normalize();
  }

  /**
   * Codex context hook output.
   *
   * @param output the JSON output to print to stdout
   * @param warnings warning messages to print to stderr
   */
  public record HookResult(String output, List<String> warnings)
  {
    /**
     * Creates a hook result.
     *
     * @param output the JSON output to print to stdout
     * @param warnings warning messages to print to stderr
     */
    public HookResult
    {
      requireThat(output, "output").isNotNull();
      requireThat(warnings, "warnings").isNotNull();
      warnings = List.copyOf(warnings);
    }
  }

  private static final class ProductionCodexHookScope extends AbstractCodexHook
  {
    /**
     * Creates a production Codex hook scope.
     *
     * @param projectPath the project directory path
     * @param pluginRoot the plugin root directory path
     * @param pluginData the plugin data directory path
     * @param workingDirectory the process working directory
     * @param timezone the timezone identifier
     * @param nativeInput the native Codex hook payload
     */
    private ProductionCodexHookScope(Path projectPath, Path pluginRoot, Path pluginData,
      Path workingDirectory, String timezone, String nativeInput)
    {
      super(projectPath, pluginRoot, pluginData, workingDirectory, normalizeTimezone(timezone),
        new ByteArrayInputStream(nativeInput.getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static final class MigrationNotice implements SessionStartHandler
  {
    private final SessionStartHandler delegate;
    private boolean ran;

    private MigrationNotice(SessionStartHandler delegate)
    {
      requireThat(delegate, "delegate").isNotNull();
      this.delegate = delegate;
    }

    @Override
    public Result handle()
    {
      Result result = delegate.handle();
      ran = !result.additionalContext().isBlank() || !result.stderr().isBlank();
      return result;
    }

    /**
     * Indicates whether delegate emitted any migration notice content.
     *
     * @return {@code true} if delegate produced stdout or stderr content
     */
    private boolean ran()
    {
      return ran;
    }
  }
}
