/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.Strings;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for statusline installation status check.
 * <p>
 * Checks if a statusLine configuration exists in the project's .claude/settings.json file and outputs
 * the result in a structured format for the statusline skill to consume.
 */
public final class GetStatuslineOutput implements SkillOutput
{
  private final Path projectPath;
  private final JsonMapper mapper;

  /**
   * Creates a GetStatuslineOutput instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   */
  public GetStatuslineOutput(ClaudeTool scope)
  {
    this.projectPath = scope.getProjectPath();
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
    Path settingsFile = projectPath.resolve(".claude/settings.json");

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
        }""".formatted(Strings.escapeJson(errorMsg));
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
        }""".formatted(Strings.escapeJson(errorMsg));
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
   * Main entry point.
   *
   * @param args command line arguments (unused)
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
        Logger log = LoggerFactory.getLogger(GetStatuslineOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the statusline output logic with a caller-provided output stream.
   *
   * @param scope the ClaudeTool
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException if {@code scope}, {@code args} or {@code out} are null
   * @throws IOException          if an I/O error occurs
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    GetStatuslineOutput generator = new GetStatuslineOutput(scope);
    String output = generator.getOutput(args);
    out.print(output);
  }
}
