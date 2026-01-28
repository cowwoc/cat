package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utility class for building hook output JSON.
 */
public final class HookOutput
{
  private HookOutput()
  {
    // Utility class
  }

  /**
   * Output an empty response (allow the operation).
   */
  public static void empty()
  {
    System.out.println("{}");
  }

  /**
   * Output a block decision.
   *
   * @param reason Reason for blocking
   */
  public static void block(String reason)
  {
    block(reason, null);
  }

  /**
   * Output a block decision with additional context.
   *
   * @param reason Reason for blocking
   * @param additionalContext Extra context to provide
   */
  public static void block(String reason, String additionalContext)
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode response = mapper.createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    if (!additionalContext.isEmpty())
    {
      response.put("additionalContext", additionalContext);
    }
    output(mapper, response);
  }

  /**
   * Output a warning to stderr (but allow the operation).
   *
   * @param warning Warning message
   */
  public static void warn(String warning)
  {
    System.err.println(warning);
    empty();
  }

  /**
   * Output additional context via hookSpecificOutput.
   *
   * @param hookEventName The hook event name (e.g., "UserPromptSubmit", "PostToolUse")
   * @param additionalContext The context to inject
   */
  public static void additionalContext(String hookEventName, String additionalContext)
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookSpecific = mapper.createObjectNode();
    hookSpecific.put("hookEventName", hookEventName);
    hookSpecific.put("additionalContext", additionalContext);

    ObjectNode response = mapper.createObjectNode();
    response.set("hookSpecificOutput", hookSpecific);
    output(mapper, response);
  }

  /**
   * Output raw JSON.
   *
   * @param mapper The JsonMapper to use for serialization
   * @param node JSON node to output
   */
  public static void output(JsonMapper mapper, ObjectNode node)
  {
    try
    {
      System.out.println(mapper.writeValueAsString(node));
    }
    catch (Exception e)
    {
      System.out.println("{}");
    }
  }

  /**
   * Wrap a string in system-reminder tags.
   *
   * @param content the content to wrap
   * @return the content wrapped in system-reminder tags
   */
  public static String wrapSystemReminder(String content)
  {
    return "<system-reminder>\n" + content + "\n</system-reminder>";
  }
}
