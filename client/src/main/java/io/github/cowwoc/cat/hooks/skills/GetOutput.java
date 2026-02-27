/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Centralized dispatcher for all verbatim output skills.
 * <p>
 * Routes output type arguments to the appropriate {@code SkillOutput} handler implementation.
 * This class enables composition: higher-level skills invoke {@code /cat:get-output} with a
 * dot-notation type (e.g., {@code config.settings}) and receive the output wrapped in an
 * {@code <output type="...">} tag.
 * <p>
 * The type format is {@code skill[.page]}: the part before the first dot selects the handler,
 * and the optional part after the dot is passed as a page argument. Additional arguments beyond
 * the type are passed through to the handler (used by handlers like {@code work-complete} that
 * need caller-provided data).
 */
public final class GetOutput implements SkillOutput
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates output by dispatching to the appropriate skill handler.
   * <p>
   * The first argument is the output type in dot-notation: {@code skill[.page]}.
   * The part before the first dot selects the handler; the part after the dot (if present) is
   * prepended to the handler's arguments as a page selector. Any additional arguments are passed
   * through to the handler.
   * <p>
   * The output is wrapped in {@code <output type="...">} tags.
   *
   * @param args the preprocessor arguments: [type, ...extra-args]
   * @return the generated output wrapped in an output tag
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if no type argument is provided or if the skill is unknown
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();

    if (args.length < 1)
      throw new IllegalArgumentException(
        "get-output requires a type argument. Usage: get-output <type> [extra-args...]\n" +
        "Type format: skill[.page] (e.g., status, config.settings, init.choose-your-partner)");

    String type = args[0];
    int dotIndex = type.indexOf('.');
    String skill;
    String page;
    if (dotIndex >= 0)
    {
      skill = type.substring(0, dotIndex);
      page = type.substring(dotIndex + 1);
    }
    else
    {
      skill = type;
      page = "";
    }

    // Build handler args: [page (if present), ...extra-args]
    String[] extraArgs = new String[args.length - 1];
    System.arraycopy(args, 1, extraArgs, 0, args.length - 1);

    String[] handlerArgs;
    if (page.isEmpty())
      handlerArgs = extraArgs;
    else
    {
      handlerArgs = new String[extraArgs.length + 1];
      handlerArgs[0] = page;
      System.arraycopy(extraArgs, 0, handlerArgs, 1, extraArgs.length);
    }

    String content = switch (skill)
    {
      case "status" -> new GetStatusOutput(scope).getOutput(handlerArgs);
      case "token-report" -> new GetTokenReportOutput(scope).getOutput(handlerArgs);
      case "get-diff" -> new GetDiffOutput(scope).getOutput(handlerArgs);
      case "cleanup" -> new GetCleanupOutput(scope).getOutput(handlerArgs);
      case "run-retrospective" -> new GetRetrospectiveOutput(scope).getOutput(handlerArgs);
      case "config" -> new GetConfigOutput(scope).getOutput(handlerArgs);
      case "init" -> new GetInitOutput(scope).getOutput(handlerArgs);
      case "work-complete" -> new GetIssueCompleteOutput(scope).getOutput(handlerArgs);
      case "statusline" -> new GetStatuslineOutput(scope).getOutput(handlerArgs);
      case "get-subagent-status" -> new GetSubagentStatusOutput(scope).getOutput(handlerArgs);
      default -> throw new IllegalArgumentException("Unknown skill: '" + skill +
        "'. Valid skills: status, token-report, get-diff, cleanup, run-retrospective, " +
        "config, init, work-complete, statusline, get-subagent-status");
    };

    if (content == null)
      return null;
    return "<output type=\"" + type + "\">\n" + content + "\n</output>";
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command line arguments: skill-name [page-args...]
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      String output = new GetOutput(scope).getOutput(args);
      if (output != null)
        System.out.print(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating output: " + e.getMessage());
      System.exit(1);
    }
    catch (RuntimeException | AssertionError e)
    {
      System.err.println("Unexpected error: " + e.getMessage());
      System.exit(1);
    }
  }
}
