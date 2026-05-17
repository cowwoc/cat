/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.agent.AgentScope;
import io.github.cowwoc.cat.tool.MainCliTool;
import static io.github.cowwoc.cat.tool.Strings.block;
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
   * Ordered skill handlers for dispatch and error messages.
   */
  private static final List<Handler> HANDLERS = List.of(
    new Handler("status", GetStatusOutput::new),
    new Handler("token-report", GetTokenReportOutput::new),
    new Handler("get-diff", GetDiffOutput::new),
    new Handler("cleanup", GetCleanupOutput::new),
    new Handler("retrospective", GetRetrospectiveOutput::new),
    new Handler("config", GetConfigOutput::new),
    new Handler("init", GetInitOutput::new),
    new Handler("work-complete", GetIssueCompleteOutput::new),
    new Handler("statusline", GetStatuslineOutput::new),
    new Handler("instruction-test-aggregator", InstructionTestAggregator::new),
    new Handler("description-optimizer", DescriptionOptimizer::new),
    new Handler("add", GetAddOutput::new));

  /**
   * The JVM scope for accessing shared services.
   */
  private final CliTool scope;

  /**
   * Creates a GetOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetOutput(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates output by dispatching to the appropriate skill handler.
   * <p>
   * The type format is {@code skill[.page]}: the part before the first dot selects the handler,
   * and the optional part after the dot is passed as a page argument. Any additional arguments are
   * passed through to the handler.
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

    SkillOutput handler = handlerFor(skill);
    if (handler == null)
      throw new IllegalArgumentException("Unknown skill: '" + skill + "'. Valid skills: " + validSkills());
    String content = handler.getOutput(handlerArgs);

    if (content == null)
      return null;
    String sanitizedType = escapeXmlAttribute(type);
    return """
      ## Purpose

      Output the pre-rendered %s display exactly as computed by the %s handler.

      ---

      ## Procedure

      ### Step 1: Locate the current output tag

      Scan the conversation from the **end** toward the beginning. Find the **last** (most recently appearing)
      `<output type="%s">` tag. This is the current output injected by the
      preprocessor directive above.

      **CRITICAL:** Prior invocations of this skill may have left earlier `<output type="%s">` tags earlier in the
      conversation. Those are stale. Only the LAST matching-type tag is current. Ignore `<output>` tags with a
      different `type` even if they appear later in the conversation.

      If the matching `<output type="%s">` tag is missing, empty, or contains error content: report "%s display \
      unavailable." and stop. Do not investigate the cause, run commands, or construct/infer/approximate the %s \
      display by any means.

      **Error content** means the tag body consists entirely of: a Java stack trace, a JSON error object \
      (`"status":"ERROR"` or `"error":...`), a plain-text error message starting with `ERROR:`, `FATAL:`, or \
      `FAILURE:`, an HTTP status line, a bare exception class name, or any unrecognized format that is clearly not \
      normal rendered display. Normal output containing the word "error" as display data is NOT error content. \
      Whitespace-only content is treated as empty.

      ### Step 2: Output verbatim

      Print the complete `<output type="%s">` tag content exactly as-is. Do not modify, reformat, reword, truncate, \
      summarize, or selectively output any portion. Do not add commentary, headers, or preambles. Do not read project \
      files or run tools.

      ### Step 3: Stop

      Stop after outputting. Do not ask follow-up questions or offer next steps. The triggering prompt is the %s \
      request itself — not a request for follow-up.

      ---

      ## Verification

      - [ ] The rendered %s display from the `<output type="%s">` tag is printed completely and without modification
      - [ ] The output matches the content of the LAST `<output type="%s">` tag in context, not an earlier one
      - [ ] `<output>` tags with a different `type` were not selected
      - [ ] No additional text, commentary, or formatting was added
      - [ ] No project files were read and no tools or commands were run
      - [ ] The agent stopped after outputting without offering follow-up or next steps

      <output type="%s">
      %s
      </output>""".formatted(skill, skill, sanitizedType, sanitizedType, sanitizedType, skill, skill, sanitizedType,
        skill, skill, sanitizedType, sanitizedType, sanitizedType, content);
  }

  /**
   * Returns the handler for a skill name.
   *
   * @param skill the skill name
   * @return the handler, or null if the skill is unknown
   */
  private SkillOutput handlerFor(String skill)
  {
    for (Handler handler : HANDLERS)
    {
      if (handler.name().equals(skill))
        return handler.outputFactory().create(scope);
    }
    return null;
  }

  /**
   * Returns valid skill names for error messages.
   *
   * @return a comma-separated list of valid skill names
   */
  private static String validSkills()
  {
    return HANDLERS.stream().
      map(Handler::name).
      collect(Collectors.joining(", "));
  }

  /**
   * Creates a skill output handler.
   */
  @FunctionalInterface
  private interface OutputFactory
  {
    /**
     * Creates a skill output handler.
     *
     * @param scope the JVM scope for accessing shared services
     * @return the skill output handler
     */
    SkillOutput create(CliTool scope);
  }

  /**
   * Binds a skill name to its handler factory.
   *
   * @param name the skill name
   * @param outputFactory the handler factory
   */
  private record Handler(String name, OutputFactory outputFactory)
  {
  }

  /**
   * Escapes special XML characters in a string for use in XML attributes.
   *
   * @param value the string to escape
   * @return the escaped string safe for use in XML attributes
   */
  private static String escapeXmlAttribute(String value)
  {
    return value.replace("&", "&amp;").
      replace("<", "&lt;").
      replace(">", "&gt;").
      replace("\"", "&quot;").
      replace("'", "&apos;");
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command line arguments: skill-name [page-args...]
   */
  public static void main(String[] args)
  {
    try (CliTool scope = new MainCliTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the output dispatch logic with a caller-provided output stream.
   *
   * @param scope the JVM scope (must implement {@link CliTool})
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException if {@code scope}, {@code args} or {@code out} are null
   * @throws IOException          if an I/O error occurs
   */
  public static void run(AgentScope scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String output = new GetOutput((CliTool) scope).getOutput(args);
    if (output != null)
      out.print(output);
  }
}
