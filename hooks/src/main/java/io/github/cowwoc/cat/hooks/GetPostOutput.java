package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.tool.post.AutoLearnMistakes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * get-posttool-output - Unified PostToolUse hook for all tools
 *
 * TRIGGER: PostToolUse (no matcher - runs for all tools)
 *
 * Consolidates general PostToolUse hooks into a single Java dispatcher.
 * For Bash-specific PostToolUse hooks, see GetBashPostOutput.
 *
 * Handlers can:
 * - Warn about tool results (return warning string)
 * - Inject additional context (return additionalContext)
 * - Allow silently (return null)
 */
public final class GetPostOutput implements HookHandler
{
  private static final List<PosttoolHandler> HANDLERS = List.of(
      new AutoLearnMistakes());

  /**
   * Creates a new GetPostOutput instance.
   */
  public GetPostOutput()
  {
  }

  /**
   * Entry point for the general posttool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(mapper, System.out);
      new GetPostOutput().run(input, output);
    }
  }

  /**
   * Processes hook input and writes the result.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @throws NullPointerException if input or output is null
   */
  @Override
  public void run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String toolName = input.getToolName();
    if (toolName.isEmpty())
    {
      output.empty();
      return;
    }

    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();

    List<String> warnings = new ArrayList<>();
    List<String> additionalContexts = new ArrayList<>();

    // Run all general posttool handlers
    for (PosttoolHandler handler : HANDLERS)
    {
      try
      {
        PosttoolHandler.Result result = handler.check(toolName, toolResult, sessionId, input.getRaw());
        if (!result.warning().isEmpty())
          warnings.add(result.warning());
        if (!result.additionalContext().isEmpty())
          additionalContexts.add(result.additionalContext());
      }
      catch (Exception e)
      {
        System.err.println("get-posttool-output: handler error: " + e.getMessage());
      }
    }

    // Output warnings to stderr
    for (String warning : warnings)
    {
      System.err.println(warning);
    }

    // Build response with additionalContext if present
    if (!additionalContexts.isEmpty())
    {
      String combined = String.join("\n\n", additionalContexts);
      output.additionalContext("PostToolUse", combined);
    }
    else
    {
      output.empty();
    }
  }
}
