/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.time.Duration;

/**
 * A {@link JvmScope} that provides the infrastructure needed to render the Claude Code statusline.
 */
public interface ClaudeStatusline extends JvmScope
{
  /**
   * Returns the model display name parsed from the statusline JSON.
   *
   * @return the model display name, or {@code "unknown"} if not yet parsed or absent
   */
  String getModelDisplayName();

  /**
   * Returns the session ID parsed from the statusline JSON.
   *
   * @return the session ID, or {@code "unknown"} if not yet parsed or absent
   */
  String getSessionId();

  /**
   * Returns the total session duration parsed from the statusline JSON.
   *
   * @return the total duration, or {@link Duration#ZERO} if not yet parsed or absent
   */
  Duration getTotalDuration();

  /**
   * Returns the number of tokens used in the context window, as parsed from the statusline JSON.
   *
   * @return the number of used tokens, or {@code 0} if not present in the input
   */
  int getUsedTokens();
}
