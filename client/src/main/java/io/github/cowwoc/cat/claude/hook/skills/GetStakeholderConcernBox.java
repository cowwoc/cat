/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import java.io.PrintStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;

import static io.github.cowwoc.cat.claude.hook.Strings.block;

import java.io.IOException;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Generates a concern box for /cat:stakeholder-review-agent skill.
 * <p>
 * Displays a concern raised by a stakeholder during review, at a configurable severity level.
 */
public final class GetStakeholderConcernBox
{
  /**
   * The scope for accessing shared services.
   */
  private final ClaudeTool scope;

  /**
   * Creates a GetStakeholderConcernBox instance.
   *
   * @param scope the scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetStakeholderConcernBox(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Build a concern box.
   *
   * @param severity           the severity level (e.g., "CRITICAL", "HIGH", "MEDIUM", "LOW")
   * @param stakeholder        the stakeholder raising the concern
   * @param concernDescription the description of the concern
   * @param fileLocation       the file location related to the concern
   * @return the formatted concern box
   * @throws NullPointerException     if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getConcernBox(String severity, String stakeholder, String concernDescription,
    String fileLocation)
  {
    requireThat(severity, "severity").isNotBlank();
    requireThat(stakeholder, "stakeholder").isNotBlank();
    requireThat(concernDescription, "concernDescription").isNotBlank();
    requireThat(fileLocation, "fileLocation").isNotBlank();

    List<String> concerns = List.of(
      "[" + stakeholder + "] " + concernDescription,
      "└─ " + fileLocation,
      "");
    return scope.getDisplayUtils().buildConcernBox(severity, concerns);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage:
   * <pre>
   * get-stakeholder-concern-box &lt;severity&gt; &lt;stakeholder&gt; &lt;description&gt; &lt;location&gt;
   * </pre>
   * Where:
   * <ul>
   *   <li>{@code severity} - the severity level (CRITICAL, HIGH, MEDIUM, LOW)</li>
   *   <li>{@code stakeholder} - the stakeholder name</li>
   *   <li>{@code description} - the concern description</li>
   *   <li>{@code location} - the file location related to the concern</li>
   * </ul>
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
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
        Logger log = LoggerFactory.getLogger(GetStakeholderConcernBox.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the stakeholder concern box logic with a caller-provided output stream.
   *
   * @param scope the JVM scope
   * @param args  command line arguments: severity, stakeholder, description, location
   * @param out   the output stream to write to
   * @throws NullPointerException     if {@code scope}, {@code args} or {@code out} are null
   * @throws IllegalArgumentException if the wrong number of arguments is provided
   * @throws IOException              if an I/O error occurs
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length != 4)
      throw new IllegalArgumentException("Expected 4 arguments but got " + args.length);

    String severity = args[0];
    String stakeholder = args[1];
    String description = args[2];
    String location = args[3];

    out.print(new GetStakeholderConcernBox(scope).
      getConcernBox(severity, stakeholder, description, location));
  }
}
