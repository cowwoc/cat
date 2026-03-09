/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import java.util.regex.Pattern;

/**
 * Shared patterns for matching CAT agent IDs.
 * <p>
 * Main agents are identified by a standard UUID. Subagents are identified by a path of the form
 * {@code {uuid}/subagents/{identifier}}. These patterns are used to detect and skip agent ID prefixes
 * when parsing skill arguments passed via {@code $ARGUMENTS}.
 */
public final class AgentIdPatterns
{
  /**
   * Matches a standard session ID (UUID): xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (case-insensitive, full-string match).
   */
  public static final Pattern SESSION_ID_PATTERN = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    Pattern.CASE_INSENSITIVE);
  /**
   * Matches a subagent ID: {uuid}/subagents/{alphanumeric, hyphens, underscores} (case-insensitive,
   * full-string match).
   */
  public static final Pattern SUBAGENT_ID_PATTERN = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/subagents/[A-Za-z0-9_-]+",
    Pattern.CASE_INSENSITIVE);

  private AgentIdPatterns()
  {
  }
}
