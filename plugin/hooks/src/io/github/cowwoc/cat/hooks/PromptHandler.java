package io.github.cowwoc.cat.hooks;

/**
 * Interface for prompt pattern handlers.
 *
 * <p>Prompt handlers analyze user prompts for patterns that require injected context
 * (reminders, warnings, etc.) regardless of whether the prompt is a skill command.</p>
 */
@FunctionalInterface
public interface PromptHandler
{
  /**
   * Check prompt for patterns and return context to inject.
   *
   * @param prompt the user's prompt text
   * @param sessionId the current session ID
   * @return string to inject as context, or empty string if no match
   * @throws NullPointerException if prompt or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  String check(String prompt, String sessionId);
}
