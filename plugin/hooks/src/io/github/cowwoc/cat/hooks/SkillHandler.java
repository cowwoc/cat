package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;

/**
 * Interface for skill handlers.
 *
 * <p>Skill handlers process CAT commands (e.g., /cat:status, /cat:work) and return
 * additional context to inject into the conversation.</p>
 */
public interface SkillHandler
{
  /**
   * Handle precomputation for a skill.
   *
   * @param context the context containing:
   *   <ul>
   *     <li>userPrompt - Full user prompt string</li>
   *     <li>sessionId - Claude session ID</li>
   *     <li>projectRoot - Path to project root (may be empty)</li>
   *     <li>pluginRoot - Path to plugin root</li>
   *     <li>hookData - Raw hook JSON data</li>
   *   </ul>
   * @return string to inject as additionalContext, or null for no injection
   */
  String handle(SkillContext context);

  /**
   * Context object passed to skill handlers.
   *
   * @param userPrompt the user's prompt text
   * @param sessionId the Claude session ID
   * @param projectRoot path to project root (may be empty)
   * @param pluginRoot path to plugin root
   * @param hookData raw hook JSON data
   */
  record SkillContext(String userPrompt, String sessionId, String projectRoot, String pluginRoot,
                      JsonNode hookData)
  {
  }
}
