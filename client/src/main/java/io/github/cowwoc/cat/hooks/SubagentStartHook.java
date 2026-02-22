/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.SkillDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SubagentStart hook handler.
 * <p>
 * Injects the full model-invocable skill listing into subagent context when a subagent starts.
 * This ensures subagents know which skills are available for activation without relying on
 * static frontmatter preloading.
 * <p>
 * Each entry uses the format {@code "- name: description"}, matching Claude Code's native skill listing.
 * The header instructs subagents to use the Skill tool for invoking skills and includes behavioral
 * instructions about when to invoke them.
 */
public final class SubagentStartHook implements HookHandler
{
  private final JvmScope scope;

  /**
   * Creates a new SubagentStartHook.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SubagentStartHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the SubagentStart hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      tools.jackson.databind.json.JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(scope);
      SubagentStartHook hook = new SubagentStartHook(scope);
      HookResult result = hook.run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(SubagentStartHook.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Processes the SubagentStart hook by injecting the skill listing as additional context.
   *
   * @param input  the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output with the skill listing
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String listing = SkillDiscovery.getSubagentSkillListing(scope);
    if (listing.isEmpty())
      return HookResult.withoutWarnings(output.empty());
    return HookResult.withoutWarnings(output.additionalContext("SubagentStart", listing));
  }
}
