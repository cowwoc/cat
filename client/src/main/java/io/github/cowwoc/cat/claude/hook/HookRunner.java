/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Encapsulates hook execution with Claude Code compliance.
 * <p>
 * Ensures that all hooks:
 * - Always exit with code 0 (Claude Code processes JSON output on exit 0)
 * - Write JSON output to stdout (using fields from the Claude Code hooks JSON schema)
 * - Write warnings to stderr (visible in verbose mode via Ctrl+O)
 * - On exception, output JSON with {@code systemMessage} to warn the user
 * <p>
 * Hook implementations use this by delegating their main() method:
 * <pre>
 * public static void main(String[] args) {
 *   HookRunner.execute(MyHook::new, args);
 * }
 * </pre>
 */
public final class HookRunner
{
  private static final Logger LOG = LoggerFactory.getLogger(HookRunner.class);

  private HookRunner()
  {
  }

  /**
   * Execute a hook handler with proper error handling and exit code management.
   * <p>
   * This method:
   * 1. Creates a JVM scope and reads hook input from stdin
   * 2. Instantiates the hook handler using the provided factory
   * 3. Runs the hook and outputs results (output to stdout, warnings to stderr)
   * 4. Catches any exceptions and outputs a JSON error with {@code systemMessage}
   * 5. Returns normally, exiting with code 0 (Claude Code processes JSON on exit 0)
   *
   * @param factory the hook handler factory
   * @param args    command line arguments (currently unused)
   * @throws NullPointerException if {@code factory} is null
   */
  public static void execute(HookHandlerFactory factory, String[] args)
  {
    requireThat(factory, "factory").isNotNull();

    try (ClaudeHook scope = new MainClaudeHook())
    {
      execute(factory, scope, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      // Scope creation failed - cannot use JsonMapper
      LOG.error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
    // Method returns normally → JVM exits with code 0.
    // Claude Code processes JSON output on exit 0.
  }

  /**
   * Execute a hook handler with an existing scope and injectable output stream.
   * <p>
   * This overload enables testing: tests create a {@code TestClaudeHook} scope and capture output
   * via a custom {@code PrintStream}, while {@code main()} delegates here via the no-arg overload.
   *
   * @param factory the hook handler factory
   * @param scope   the hook scope providing hook input, output, and configuration
   * @param out     the output stream for writing JSON response
   * @throws NullPointerException if {@code factory}, {@code scope}, or {@code out} are null
   */
  public static void execute(HookHandlerFactory factory, ClaudeHook scope, PrintStream out)
  {
    requireThat(factory, "factory").isNotNull();
    requireThat(scope, "scope").isNotNull();
    requireThat(out, "out").isNotNull();

    try
    {
      // Create handler via factory and run it
      HookHandler handler = factory.create(scope);
      HookResult result = handler.run(scope);

      // Write warnings to stderr
      for (String warning : result.warnings())
        System.err.println(warning);

      // Write hook output to the provided stream
      out.println(result.output());
    }
    catch (IOException | RuntimeException | AssertionError e)
    {
      LOG.error("Hook execution failed", e);

      // Output error using Claude Code's universal JSON fields.
      // "systemMessage" is a warning shown to the user.
      String message;
      if (e.getMessage() != null)
        message = e.getMessage();
      else
        message = e.getClass().getSimpleName();

      ObjectNode errorJson = scope.getJsonMapper().createObjectNode();
      errorJson.put("systemMessage", "Hook failed: " + message);
      out.println(errorJson.toString());
    }
  }

  /**
   * Factory for creating hook handler instances.
   * <p>
   * Used to defer handler creation until after the JVM scope is initialized,
   * allowing handlers to access scope-provided resources.
   */
  @FunctionalInterface
  public interface HookHandlerFactory
  {
    /**
     * Creates a new hook handler instance.
     *
     * @param scope the hook scope providing hook input, output, and configuration
     * @return a new hook handler
     * @throws IOException if handler creation fails
     */
    HookHandler create(ClaudeHook scope) throws IOException;
  }
}
