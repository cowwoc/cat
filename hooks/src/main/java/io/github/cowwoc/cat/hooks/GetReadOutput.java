package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * get-read-pretool-output - Unified PreToolUse hook for Read/Glob/Grep
 *
 * TRIGGER: PreToolUse (matcher: Read|Glob|Grep)
 *
 * Consolidates read operation validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Warn about patterns (return warning)
 * - Block operations (return block=true with message)
 * - Allow silently (return null)
 */
public final class GetReadOutput implements HookHandler
{
  private static final Set<String> SUPPORTED_TOOLS = Set.of("Read", "Glob", "Grep");

  private final List<ReadHandler> handlers;

  /**
   * Creates a new GetReadOutput instance.
   *
   * @param scope the JVM scope providing singleton handlers
   * @throws NullPointerException if scope is null
   */
  public GetReadOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(scope.getPredictBatchOpportunity());
  }

  /**
   * Entry point for the Read pretool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      HookInput input = HookInput.readFromStdin(scope.getJsonMapper());
      HookOutput output = new HookOutput(scope.getJsonMapper());
      HookResult result = new GetReadOutput(scope).run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
  catch (RuntimeException | Error e)
  {
    
      Logger log = LoggerFactory.getLogger(GetReadOutput.class);
      log.error("Unexpected error", e);
    throw e;
  }
  }

  /**
   * Processes hook input and returns the result with any warnings.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String toolName = input.getToolName();
    if (!SUPPORTED_TOOLS.contains(toolName))
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolInput = input.getToolInput();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();
    List<String> errorWarnings = new ArrayList<>();

    // Run all read pretool handlers
    for (ReadHandler handler : handlers)
    {
      try
      {
        ReadHandler.Result result = handler.check(toolName, toolInput, null, sessionId);
        if (result.blocked())
        {
          String jsonOutput;
          if (result.additionalContext().isEmpty())
            jsonOutput = output.block(result.reason());
          else
            jsonOutput = output.block(result.reason(), result.additionalContext());
          return HookResult.withoutWarnings(jsonOutput);
        }
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (RuntimeException e)
      {
        errorWarnings.add("get-read-pretool-output: handler error: " + e.getMessage());
      }
    }

    // Combine all warnings
    List<String> allWarnings = new ArrayList<>();
    allWarnings.addAll(warnings);
    allWarnings.addAll(errorWarnings);

    // Allow the operation
    return new HookResult(output.empty(), allWarnings);
  }
}
