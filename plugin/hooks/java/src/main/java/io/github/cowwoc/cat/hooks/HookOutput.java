package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.PrintStream;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utility class for building hook output JSON.
 */
public final class HookOutput
{
  private final PrintStream out;

  /**
   * Creates a new HookOutput that writes to the specified stream.
   *
   * @param out the output stream to write to
   * @throws NullPointerException if out is null
   */
  public HookOutput(PrintStream out)
  {
    requireThat(out, "out").isNotNull();
    this.out = out;
  }

  /**
   * Output an empty response (allow the operation).
   */
  public void empty()
  {
    out.println("{}");
  }

  /**
   * Output a block decision.
   *
   * @param reason Reason for blocking
   * @throws IllegalArgumentException if reason is null or blank
   */
  public void block(String reason)
  {
    requireThat(reason, "reason").isNotBlank();
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode response = mapper.createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    output(mapper, response);
  }

  /**
   * Output a block decision with additional context.
   *
   * @param reason Reason for blocking
   * @param additionalContext Extra context to provide
   * @throws IllegalArgumentException if reason or additionalContext is null or blank
   */
  public void block(String reason, String additionalContext)
  {
    requireThat(reason, "reason").isNotBlank();
    requireThat(additionalContext, "additionalContext").isNotBlank();
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode response = mapper.createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    response.put("additionalContext", additionalContext);
    output(mapper, response);
  }

  /**
   * Output a warning to stderr (but allow the operation).
   *
   * @param warning Warning message
   * @throws IllegalArgumentException if warning is null or blank
   */
  public void warn(String warning)
  {
    requireThat(warning, "warning").isNotBlank();
    System.err.println(warning);
    empty();
  }

  /**
   * Output additional context via hookSpecificOutput.
   *
   * @param hookEventName The hook event name (e.g., "UserPromptSubmit", "PostToolUse")
   * @param additionalContext The context to inject
   * @throws IllegalArgumentException if hookEventName or additionalContext is null or blank
   */
  public void additionalContext(String hookEventName, String additionalContext)
  {
    requireThat(hookEventName, "hookEventName").isNotBlank();
    requireThat(additionalContext, "additionalContext").isNotBlank();
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
  public void output(JsonMapper mapper, ObjectNode node)
  {
    try
    {
      out.println(mapper.writeValueAsString(node));
    }
    catch (Exception _)
    {
      out.println("{}");
    }
  }

  /**
   * Wrap a string in system-reminder tags.
   *
   * @param content the content to wrap
   * @return the content wrapped in system-reminder tags
   * @throws IllegalArgumentException if content is null or blank
   */
  public static String wrapSystemReminder(String content)
  {
    requireThat(content, "content").isNotBlank();
    return "<system-reminder>\n" + content + "\n</system-reminder>";
  }
}
