/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;

/**
 * Injects the full skill listing with descriptions into Claude's context at session start and after
 * a {@code /clear}.
 * <p>
 * Injects on {@code source="startup"} (new session) and {@code source="clear"} (conversation cleared).
 * Returns empty for {@code source="compact"} and {@code source="resume"} — agents must explicitly
 * re-invoke needed skills after context is compressed or when resuming a session.
 */
public final class InjectSkillListing implements SessionStartHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new InjectSkillListing handler.
   *
   * @param scope the hook scope
   * @throws NullPointerException if scope is null
   */
  public InjectSkillListing(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Returns the full skill listing with descriptions as additional context.
   *
   * @return a result containing the skill listing for {@code source="startup"} or {@code source="clear"},
   *   or empty otherwise
   */
  @Override
  public Result handle()
  {
    String source = scope.getStringInput("source");
    if (!source.equals("startup") && !source.equals("clear"))
      return Result.empty();
    String listing = SkillDiscovery.getMainAgentSkillListing(scope);
    if (listing.isEmpty())
      return Result.empty();
    return Result.context(listing);
  }
}
