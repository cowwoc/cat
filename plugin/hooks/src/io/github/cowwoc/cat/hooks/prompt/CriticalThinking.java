package io.github.cowwoc.cat.hooks.prompt;

import io.github.cowwoc.cat.hooks.PromptHandler;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Always injects critical thinking requirements reminder.
 */
public final class CriticalThinking implements PromptHandler
{
  private static final String REMINDER = """
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
   * Creates a new critical thinking handler.
   */
  public CriticalThinking()
  {
    // Handler class
  }

  @Override
  public String check(String prompt, String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return REMINDER;
  }
}
