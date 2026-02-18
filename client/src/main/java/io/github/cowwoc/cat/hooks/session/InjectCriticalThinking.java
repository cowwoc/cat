/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.HookInput;

/**
 * Injects critical thinking requirements into the session context once at session start
 * and after compaction.
 */
public final class InjectCriticalThinking implements SessionStartHandler
{
  static final String REMINDER = """
## CRITICAL THINKING REQUIREMENTS

**MANDATORY**: Apply evidence-based critical thinking
- FIRST gather evidence through investigation, testing, or research
- THEN identify flaws, edge cases, or counter-examples based on that evidence
- Propose alternative approaches only when evidence shows they're superior
- Avoid challenging assumptions without supporting evidence
- If user approach is sound, state specific technical reasons for agreement

**EXAMPLES OF CRITICAL ANALYSIS:**

Instead of: "That's a good approach"
Use: "That approach addresses the immediate issue. Based on testing X, I can confirm it works. \
However, there's a potential edge case Y that we should consider because Z."

Instead of: "You're absolutely right"
Use: "The core logic is sound. My investigation shows X evidence supporting this. \
However, we should also consider scenario Y where this might need adjustment."

**APPLY TO CURRENT PROMPT**: Gather evidence first, then provide critical analysis based on that evidence.""";

  /**
   * Creates a new InjectCriticalThinking handler.
   */
  public InjectCriticalThinking()
  {
    // Handler class
  }

  /**
   * Returns the critical thinking reminder to inject into session context.
   *
   * @param input the hook input
   * @return a result containing the critical thinking reminder as additional context
   * @throws NullPointerException if {@code input} is null
   */
  @Override
  public Result handle(HookInput input)
  {
    return Result.context(REMINDER);
  }
}
