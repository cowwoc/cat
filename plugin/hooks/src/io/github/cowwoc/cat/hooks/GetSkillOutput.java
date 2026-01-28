package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * get-skill-output - Unified UserPromptSubmit hook for CAT
 *
 * TRIGGER: UserPromptSubmit
 *
 * This dispatcher consolidates all UserPromptSubmit hooks into a single Java
 * entry point, handling both:
 * 1. Skill precomputation (for /cat:* commands)
 * 2. Prompt pattern checking (for all prompts)
 */
public final class GetSkillOutput
{
  // Pattern to match /cat:skill-name or cat:skill-name
  private static final Pattern SKILL_PATTERN = Pattern.compile(
    "^\\s*/?cat:([a-z-]+)(?:\\s|$)",
    Pattern.CASE_INSENSITIVE);

  /**
   * Entry point for the skill output hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookInput input = HookInput.readFromStdin();

    String userPrompt = input.getUserPrompt();
    if (userPrompt == null || userPrompt.isEmpty())
    {
      HookOutput.empty();
      return;
    }

    String sessionId = input.getSessionId();
    List<String> outputs = new ArrayList<>();

    // 1. Run prompt handlers (pattern checking for all prompts)
    // TODO: Port prompt handlers from Python

    // 2. Run skill handler if this is a /cat:* command
    String skillName = extractSkillName(userPrompt);
    if (skillName != null)
    {
      // Determine project root
      String projectRoot = System.getenv("CLAUDE_PROJECT_DIR");
      if (projectRoot == null || projectRoot.isEmpty())
      {
        projectRoot = "";
      }
      if (!projectRoot.isEmpty())
      {
        Path catDir = Path.of(projectRoot, ".claude", "cat");
        if (!catDir.toFile().isDirectory())
        {
          projectRoot = "";
        }
      }
      if (projectRoot.isEmpty())
      {
        Path localCatDir = Path.of(".claude/cat");
        if (localCatDir.toFile().isDirectory())
        {
          projectRoot = System.getProperty("user.dir");
        }
      }

      String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
      if (pluginRoot == null)
      {
        pluginRoot = "";
      }

      // TODO: Port skill handlers from Python
    }

    // Output combined results
    if (!outputs.isEmpty())
    {
      StringBuilder combined = new StringBuilder();
      for (String output : outputs)
      {
        if (!combined.isEmpty())
        {
          combined.append('\n');
        }
        combined.append(HookOutput.wrapSystemReminder(output));
      }
      HookOutput.additionalContext("UserPromptSubmit", combined.toString());
    }
    else
    {
      HookOutput.empty();
    }
  }

  /**
   * Extracts the CAT skill name from a user prompt.
   *
   * <p>Matches patterns like:
   * <ul>
   *   <li>/cat:init</li>
   *   <li>cat:status</li>
   *   <li>/cat:work 1.0</li>
   *   <li>/cat:add make it faster</li>
   * </ul>
   *
   * @param prompt the user prompt to parse
   * @return the skill name (e.g., "init", "status") or null if not a CAT command
   */
  public static String extractSkillName(String prompt)
  {
    if (prompt == null)
    {
      return null;
    }
    Matcher matcher = SKILL_PATTERN.matcher(prompt);
    if (matcher.find())
    {
      return matcher.group(1).toLowerCase(Locale.ROOT);
    }
    return null;
  }
}
