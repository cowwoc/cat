/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AbstractAgentPluginScope;
import io.github.cowwoc.cat.agent.CheckDataMigration;
import io.github.cowwoc.cat.agent.InjectCriticalThinking;
import io.github.cowwoc.cat.agent.MainAgentRules;
import io.github.cowwoc.cat.agent.SessionStartDispatcher;
import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.agent.TerminalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;

/**
 * SessionStart hook for Codex.
 * <p>
 * Codex does not need Claude's environment-file bootstrap, but it does need CAT migrations and the
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
    if (args.length < 7)
      throw new IllegalArgumentException(
        "Expected arguments: <project-root> <plugin-data> <codex-home> <marketplace-name> " +
          "<plugin-name> <version> <timezone>");

    Path projectRoot = Path.of(args[0]);
    Path pluginData = Path.of(args[1]);
    Path codexHome = Path.of(args[2]);
    Path pluginRoot = CodexPluginCache.resolvePluginRoot(codexHome, args[3], args[4], args[5]);
    String timezone = args[6];
    try (CodexHookScope scope = new CodexHookScope(projectRoot, pluginRoot, pluginData, timezone))
    {
      return run(scope);
    }
  }

  /**
   * Runs the Codex SessionStart handlers for an initialized scope.
   *
   * @param scope the Codex hook scope
   * @return the hook output and warnings
   */
  private static HookResult run(CodexHookScope scope)
  {
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
        String content = MainAgentRules.load(scope, scope.getYamlMapper());
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
        "Codex before using CAT custom subagents so the tool registry sees the changes.");
    }
    return new HookResult(scope.getJsonMapper().writeValueAsString(output), result.warnings());
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

  private static final class CodexHookScope extends AbstractAgentPluginScope
  {
    private final String timezone;

    private CodexHookScope(Path projectPath, Path pluginRoot, Path pluginData, String timezone)
    {
      super(projectPath, pluginRoot, pluginData,
        Path.of(".codex-plugin/plugin.json"),
        List.of(
          pluginRoot.resolve("rules/common"),
          pluginRoot.resolve("rules/codex"),
          projectPath.resolve(".cat/rules/common"),
          projectPath.resolve(".cat/rules/codex")),
        Path.of(".codex-plugin/plugin.json"));
      this.timezone = timezone;
    }

    @Override
    public Path getWorkDir()
    {
      ensureOpen();
      return Path.of(System.getProperty("user.dir"));
    }

    @Override
    public TerminalType getTerminalType()
    {
      ensureOpen();
      return TerminalType.detect();
    }

    @Override
    public String getTimezone()
    {
      ensureOpen();
      if (timezone.isBlank())
        return "UTC";
      return timezone;
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
