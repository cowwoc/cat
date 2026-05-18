/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.agent.CheckDataMigration;
import io.github.cowwoc.cat.agent.InjectCriticalThinking;
import io.github.cowwoc.cat.agent.SessionStartDispatcher;
import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.hook.HookJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * SessionStart hook for Codex.
 * <p>
 * Codex does not need an environment-file bootstrap, but it does need CAT migrations and the
 * same portable and runtime-specific main-agent rules injected as session context.
 */
public final class SessionStartHook
{
  private SessionStartHook()
  {
  }

  /**
   * Entry point for the Codex SessionStart hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try
    {
      HookResult result = run(args);
      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(SessionStartHook.class);
      log.error("Codex SessionStart hook failed", e);
      System.err.println("Hook failed: " + e.getMessage());
      System.out.println("{}");
    }
  }

  /**
   * Runs the Codex SessionStart hook without writing to process streams.
   *
   * @param args command line arguments
   * @return the hook output and warnings
   */
  public static HookResult run(String[] args)
  {
    return run(args, System.in, System.getenv());
  }

  /**
   * Runs the Codex SessionStart hook from native Codex launcher input.
   *
   * @param args command line arguments
   * @param in standard input containing the native Codex hook payload
   * @param environment process environment values
   * @return the hook output and warnings
   * @throws NullPointerException if {@code args}, {@code in}, or {@code environment} is null
   * @throws IllegalArgumentException if launcher arguments are present or required paths cannot be
   *   resolved
   */
  public static HookResult run(String[] args, InputStream in, Map<String, String> environment)
  {
    return run(args, in, environment, Path.of(System.getProperty("user.dir")));
  }

  /**
   * Runs the Codex SessionStart hook from native Codex launcher input.
   *
   * @param args command line arguments
   * @param in standard input containing the native Codex hook payload
   * @param environment process environment values
   * @param workingDirectory the process working directory
   * @return the hook output and warnings
   * @throws NullPointerException if {@code args}, {@code in}, {@code environment}, or
   *   {@code workingDirectory} is null
   * @throws IllegalArgumentException if launcher arguments are present or required paths cannot be
   *   resolved
   */
  public static HookResult run(String[] args, InputStream in, Map<String, String> environment,
    Path workingDirectory)
  {
    CodexHookInput.requireNoArgs(args);
    requireThat(environment, "environment").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    Path resolvedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
    JsonNode nativeInput = CodexHookInput.read(in);
    Path projectRoot = resolveProjectRoot(nativeInput, environment, resolvedWorkingDirectory);
    Path pluginRoot = resolvePluginRoot(nativeInput, environment, resolvedWorkingDirectory);
    Path pluginData = resolvePluginData(nativeInput, environment);
    String timezone = getEnvironment(environment, "TZ");
    try (CodexHookScope scope = new CodexHookScope(projectRoot, pluginRoot, pluginData,
      resolvedWorkingDirectory, timezone))
    {
      return run(scope, nativeInput);
    }
  }

  /**
   * Runs the Codex SessionStart handlers for an initialized scope.
   *
   * @param scope the Codex hook scope
   * @return the hook output and warnings
   * @throws NullPointerException if {@code scope} is null
   */
  public static HookResult run(AgentPluginScope scope)
  {
    return run(scope, HookJson.JSON_MAPPER.createObjectNode());
  }

  /**
   * Runs the Codex SessionStart handlers for an initialized scope and native hook payload.
   *
   * @param scope the Codex hook scope
   * @param nativeInput the native Codex hook payload
   * @return the hook output and warnings
   * @throws NullPointerException if any parameter is null
   */
  private static HookResult run(AgentPluginScope scope, JsonNode nativeInput)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(nativeInput, "nativeInput").isNotNull();
    MigrationNotice migrationNotice = new MigrationNotice(new CheckDataMigration(scope));
    SessionStartDispatcher.Result result = SessionStartDispatcher.run(List.of(
      migrationNotice,
      () ->
      {
        CodexRuleStubGenerator.generate(scope);
        return SessionStartHandler.Result.empty();
      },
      () ->
      {
        String content = CodexSessionRules.load(scope, nativeInput);
        if (content.isBlank())
          return SessionStartHandler.Result.empty();
        return SessionStartHandler.Result.context(content);
      },
      new InjectCriticalThinking()));

    ObjectNode hookSpecificOutput = scope.getJsonMapper().createObjectNode();
    hookSpecificOutput.put("hookEventName", "SessionStart");
    hookSpecificOutput.put("additionalContext", result.additionalContext());

    ObjectNode output = scope.getJsonMapper().createObjectNode();
    output.set("hookSpecificOutput", hookSpecificOutput);
    if (migrationNotice.ran())
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
    if (!value.isBlank())
      return toAbsolutePath(value, "pluginRoot");
    return findPluginRootFromWorkingDirectory(workingDirectory);
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
  private static Path findPluginRootFromWorkingDirectory(Path workingDirectory)
  {
    for (Path candidate = workingDirectory; candidate != null; candidate = candidate.getParent())
    {
      if (candidate.resolve(".codex-plugin/plugin.json").toFile().isFile())
        return candidate;
      Path sourcePlugin = candidate.resolve("plugin");
      if (sourcePlugin.resolve(".codex-plugin/plugin.json").toFile().isFile())
        return sourcePlugin;
    }
    throw new IllegalArgumentException("CAT_PLUGIN_ROOT is required when the plugin root cannot be " +
      "discovered from the working directory.");
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
   * SessionStart hook output.
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

  private static final class CodexHookScope extends AbstractCodexHook
  {
    /**
     * Creates a production Codex hook scope.
     *
     * @param projectPath the project directory path
     * @param pluginRoot the plugin root directory path
     * @param pluginData the plugin data directory path
     * @param workingDirectory the process working directory
     * @param timezone the timezone identifier
     */
    private CodexHookScope(Path projectPath, Path pluginRoot, Path pluginData, Path workingDirectory,
      String timezone)
    {
      super(projectPath, pluginRoot, pluginData, workingDirectory, normalizeTimezone(timezone));
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

    private boolean ran()
    {
      return ran;
    }
  }
}
