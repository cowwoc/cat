/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for statusline installation status check.
 * <p>
 * Checks if a statusLine configuration exists in the project's .claude/settings.json file and outputs
 * the result in a structured format for the statusline skill to consume.
 */
public final class GetStatuslineOutput implements SkillOutput
{
  private final Path projectDir;
  private final JsonMapper mapper;

  /**
   * Creates a GetStatuslineOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetStatuslineOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.projectDir = scope.getClaudeProjectDir();
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Generates the statusline check output showing whether a statusLine configuration exists.
   *
   * @param args the arguments from the preprocessor directive (must be empty)
   * @return the formatted check result in JSON format
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if {@code args} is not empty
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").length().isEqualTo(0);
    Path settingsFile = projectDir.resolve(".claude/settings.json");

    if (!Files.exists(settingsFile))
    {
      return """
        {
          "status": "NONE"
        }""";
    }

    String content;
    try
    {
      content = Files.readString(settingsFile);
    }
    catch (IOException e)
    {
      String errorMsg = "Failed to read settings.json: " + e.getMessage();
      return """
        {
          "status": "ERROR",
          "message": "%s"
        }""".formatted(escapeJson(errorMsg));
    }

    JsonNode root;
    try
    {
      root = mapper.readTree(content);
    }
    catch (JacksonException e)
    {
      String errorMsg = "Invalid JSON in settings.json: " + e.getMessage();
      return """
        {
          "status": "ERROR",
          "message": "%s"
        }""".formatted(escapeJson(errorMsg));
    }

    JsonNode statusLineNode = root.get("statusLine");
    if (statusLineNode == null || statusLineNode.isNull())
    {
      return """
        {
          "status": "NONE"
        }""";
    }

    String currentConfig = mapper.writeValueAsString(statusLineNode);

    return """
      {
        "status": "EXISTING",
        "current_config": %s
      }""".formatted(currentConfig);
  }

  /**
   * Escapes a string for safe inclusion in JSON.
   *
   * @param value the string to escape
   * @return the escaped string
   * @throws NullPointerException if {@code value} is null
   */
  private String escapeJson(String value)
  {
    requireThat(value, "value").isNotNull();
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetStatuslineOutput generator = new GetStatuslineOutput(scope);
      String output = generator.getOutput(args);
      System.out.print(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating statusline output: " + e.getMessage());
      System.exit(1);
    }
  }
}
