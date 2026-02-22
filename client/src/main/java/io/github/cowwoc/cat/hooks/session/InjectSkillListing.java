/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Injects the full skill listing with descriptions into Claude's context after compaction.
 * <p>
 * Claude Code provides skill listings at initial startup, but does NOT re-send them after
 * conversation compaction. This handler checks the SessionStart event source and only injects
 * the skill listing when the source is {@code "compact"}, ensuring descriptions remain available
 * after context is compressed.
 */
public final class InjectSkillListing implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new InjectSkillListing handler.
   *
   * @param scope the JVM scope providing environment paths and configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectSkillListing(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Returns the full skill listing with descriptions as additional context.
   *
   * @param input the hook input
   * @return a result containing the skill listing
   * @throws NullPointerException if {@code input} is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    // Only inject skill listing after compaction. Claude Code provides listings at initial startup,
    // but does not re-send them after conversation compaction.
    String source = input.getString("source");
    if (!source.equals("compact"))
      return Result.empty();
    String listing = SkillDiscovery.getMainAgentSkillListing(scope);
    if (listing.isEmpty())
      return Result.empty();
    return Result.context(listing);
  }
}
