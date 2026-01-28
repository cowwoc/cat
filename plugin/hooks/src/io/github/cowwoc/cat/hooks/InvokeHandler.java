package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * invoke-handler - Direct handler invocation for testing and CLI use.
 *
 * Usage:
 *   echo '{"handler": "cleanup", "context": {...}}' | java io.github.cowwoc.cat.hooks.InvokeHandler
 *   java io.github.cowwoc.cat.hooks.InvokeHandler '{"handler": "cleanup", "context": {...}}'
 */
public final class InvokeHandler
{
  /**
   * Entry point for direct handler invocation.
   *
   * @param args command line arguments, optionally containing JSON input
   */
  public static void main(String[] args)
  {
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode data;

      // Read input from argument or stdin
      if (args.length > 0)
      {
        data = mapper.readTree(args[0]);
      }
      else if (System.console() == null || System.in.available() > 0)
      {
        HookInput input = HookInput.readFromStdin();
        data = input.getRaw();
      }
      else
      {
        System.err.println("Usage: InvokeHandler '<json>' or pipe JSON to stdin");
        System.exit(1);
        return;
      }

      String handlerName = null;
      if (data.has("handler"))
      {
        handlerName = data.get("handler").asText();
      }
      JsonNode context;
      if (data.has("context"))
      {
        context = data.get("context");
      }
      else
      {
        context = mapper.createObjectNode();
      }

      if (handlerName == null || handlerName.isEmpty())
      {
        System.err.println("Error: 'handler' key required in input");
        System.exit(1);
        return;
      }

      // TODO: Port handler registry from Python
      // Handler handler = HandlerRegistry.get(handlerName);
      // if (handler == null) {
      //     System.err.println("Error: No handler registered for '" + handlerName + "'");
      //     System.exit(1);
      //     return;
      // }
      // String result = handler.handle(context);
      // if (result != null) {
      //     System.out.println(result);
      // }

      System.err.println("Error: No handler registered for '" + handlerName + "'");
      System.exit(1);
    }
    catch (Exception e)
    {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }
}
