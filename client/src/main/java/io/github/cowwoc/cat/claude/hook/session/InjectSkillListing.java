/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;

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
  /**
   * Creates a new InjectSkillListing handler.
   */
  public InjectSkillListing()
  {
  }

  /**
   * Returns the full skill listing with descriptions as additional context.
   *
   * @return a result containing the skill listing
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    // Only inject skill listing after compaction. Claude Code provides listings at initial startup,
    // but does not re-send them after conversation compaction.
    String source = scope.getString("source");
    if (!source.equals("compact"))
      return Result.empty();
    String listing = SkillDiscovery.getMainAgentSkillListing(scope);
    if (listing.isEmpty())
      return Result.empty();
    return Result.context(listing);
  }
}
