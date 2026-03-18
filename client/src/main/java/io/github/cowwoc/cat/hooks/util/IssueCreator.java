/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGit;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Creates CAT issue directory structure with index.json, plan.md, and git commit.
 * <p>
 * This class consolidates multiple operations into a single atomic call:
 * - Creating issue directory
 * - Writing index.json and plan.md files
 * - Git add and commit
 */
public final class IssueCreator
{
  private final JsonMapper mapper;

  /**
   * Creates a new IssueCreator instance.
   *
   * @param scope the JVM scope providing configuration and services
   * @throws NullPointerException if {@code scope} is null
   */
  public IssueCreator(JvmScope scope)
  {
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Creates an issue with directory structure and git commit.
   *
   * @param jsonInput JSON string containing issue data
   * @return JSON string with operation result
   * @throws IOException if the operation fails or if the input is not a JSON object
   */
  public String execute(String jsonInput) throws IOException
  {
    return execute(jsonInput, Paths.get(System.getProperty("user.dir")));
  }

  /**
   * Creates an issue with directory structure and git commit.
   *
   * @param jsonInput JSON string containing issue data
   * @param workingDirectory the working directory to use as the project root
   * @return JSON string with operation result
   * @throws NullPointerException if {@code jsonInput} or {@code workingDirectory} are null
   * @throws IllegalArgumentException if {@code jsonInput} is blank
   * @throws IOException if the operation fails or if the input is not a JSON object
   */
  public String execute(String jsonInput, Path workingDirectory) throws IOException
  {
    requireThat(jsonInput, "jsonInput").isNotBlank();
    requireThat(workingDirectory, "workingDirectory").isNotNull();

    JsonNode parsedNode = mapper.readTree(jsonInput);
    if (!(parsedNode instanceof ObjectNode))
      throw new IOException("Input must be a JSON object, got: " + parsedNode.getNodeType());
    ObjectNode data = (ObjectNode) parsedNode;

    String[] required = {"major", "minor", "issueName", "indexContent"};
    for (String field : required)
    {
      if (!data.has(field))
        throw new IOException("Missing required field: " + field);
    }
    if (!data.has("planContent") && !data.has("planFile"))
      throw new IOException("Missing required field: planContent or planFile (provide one)");

    int major = data.get("major").asInt();
    int minor = data.get("minor").asInt();
    String issueName = data.get("issueName").asString();
    String indexContent = data.get("indexContent").asString();
    String planContent;
    if (data.has("planFile"))
    {
      Path planSourceFile = Path.of(data.get("planFile").asString());
      planContent = Files.readString(planSourceFile, StandardCharsets.UTF_8);
    }
    else
      planContent = data.get("planContent").asString();
    String commitDesc;
    if (data.has("commitDescription"))
      commitDesc = data.get("commitDescription").asString();
    else
      commitDesc = "Add issue";

    String issueDirPath = Config.CAT_DIR_NAME + "/issues/v" + major + "/v" + major + "." + minor + "/" + issueName;
    Path issuePath = workingDirectory.resolve(issueDirPath);

    if (!Files.exists(issuePath.getParent()))
    {
      ObjectNode error = mapper.createObjectNode();
      error.put("success", false);
      error.put("error", "Parent version directory does not exist: " + issuePath.getParent());
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(error);
    }

    Files.createDirectories(issuePath);

    Path indexFile = issuePath.resolve("index.json");
    Files.writeString(indexFile, indexContent, StandardCharsets.UTF_8);

    Path planFile = issuePath.resolve("plan.md");
    Files.writeString(planFile, planContent, StandardCharsets.UTF_8);

    String issueRelPath = workingDirectory.relativize(issuePath).toString();
    runGit(workingDirectory, "add", issueRelPath);

    String commitMessage = "planning: add issue " + issueName + " to " + major + "." + minor +
      "\n\n" + commitDesc;
    runGit(workingDirectory, "commit", "-m", commitMessage);

    ObjectNode result = mapper.createObjectNode();
    result.put("success", true);
    result.put("path", issuePath.toString());
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Provides CLI entry point to replace the original create-issue script.
   * Invoked as: java -cp hooks.jar io.github.cowwoc.cat.hooks.util.IssueCreator [--json json-string]
   *
   * @param args command-line arguments (expects --json with JSON string, or reads from stdin)
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      HookOutput hookOutput = new HookOutput(scope);
      String jsonInput;
      if (args.length == 2 && args[0].equals("--json"))
      {
        jsonInput = args[1];
      }
      else if (args.length == 0)
      {
        jsonInput = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
      }
      else
      {
        System.out.println(hookOutput.block(
          "Usage: create-issue [--json <json-string>] (or read from stdin)"));
        return;
      }

      IssueCreator creator = new IssueCreator(scope);
      try
      {
        String result = creator.execute(jsonInput);
        System.out.println(result);
      }
      catch (IOException e)
      {
        System.out.println(hookOutput.block(Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }
}
