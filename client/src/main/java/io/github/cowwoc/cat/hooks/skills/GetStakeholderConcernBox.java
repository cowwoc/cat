/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;

import java.io.IOException;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Generates a concern box for /cat:stakeholder-review skill.
 * <p>
 * Displays a concern raised by a stakeholder during review, at a configurable severity level.
 */
public final class GetStakeholderConcernBox
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetStakeholderConcernBox instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetStakeholderConcernBox(JvmScope scope)
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
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length != 4)
    {
      System.err.println("Expected 4 arguments but got " + args.length);
      printUsage();
      System.exit(1);
    }

    String severity = args[0];
    String stakeholder = args[1];
    String description = args[2];
    String location = args[3];

    try (JvmScope scope = new MainJvmScope())
    {
      System.out.print(new GetStakeholderConcernBox(scope).
        getConcernBox(severity, stakeholder, description, location));
    }
  }

  /**
   * Prints usage information to stderr.
   */
  private static void printUsage()
  {
    System.err.println("""
      Usage:
        get-stakeholder-concern-box <severity> <stakeholder> <description> <location>

      Arguments:
        severity     The severity level (CRITICAL, HIGH, MEDIUM, LOW)
        stakeholder  The stakeholder name
        description  The concern description
        location     The file location related to the concern (e.g., "file:line")
      """);
  }
}
