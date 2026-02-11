package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandInDirectory;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creates CAT issue directory structure with STATE.md, PLAN.md, and git commit.
 * <p>
 * This class consolidates multiple operations into a single atomic call:
 * - Creating issue directory
 * - Writing STATE.md and PLAN.md files
 * - Updating parent version STATE.md
 * - Git add and commit
 */
public final class IssueCreator
{
  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * Creates a new IssueCreator instance.
   */
  public IssueCreator()
  {
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

    String[] required = {"major", "minor", "issue_name", "state_content", "plan_content"};
    for (String field : required)
    {
      if (!data.has(field))
        throw new IOException("Missing required field: " + field);
    }

    int major = data.get("major").asInt();
    int minor = data.get("minor").asInt();
    String issueName = data.get("issue_name").asString();
    String stateContent = data.get("state_content").asString();
    String planContent = data.get("plan_content").asString();
    String commitDesc;
    if (data.has("commit_description"))
      commitDesc = data.get("commit_description").asString();
    else
      commitDesc = "Add issue";

    String issueDirPath = ".claude/cat/issues/v" + major + "/v" + major + "." + minor + "/" + issueName;
    Path issuePath = workingDirectory.resolve(issueDirPath);
    Path parentStatePath = issuePath.getParent().resolve("STATE.md");

    if (!Files.exists(issuePath.getParent()))
    {
      ObjectNode error = mapper.createObjectNode();
      error.put("success", false);
      error.put("error", "Parent version directory does not exist: " + issuePath.getParent());
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(error);
    }

    Files.createDirectories(issuePath);

    Path stateFile = issuePath.resolve("STATE.md");
    Files.writeString(stateFile, stateContent, StandardCharsets.UTF_8);

    Path planFile = issuePath.resolve("PLAN.md");
    Files.writeString(planFile, planContent, StandardCharsets.UTF_8);

    updateParentState(parentStatePath, issueName);

    String issueRelPath = workingDirectory.relativize(issuePath).toString();
    String parentRelPath = workingDirectory.relativize(parentStatePath).toString();
    runGitCommandInDirectory(workingDirectory.toString(), "add", issueRelPath, parentRelPath);

    String commitMessage = "planning: add issue " + issueName + " to " + major + "." + minor +
      "\n\n" + commitDesc;
    runGitCommandInDirectory(workingDirectory.toString(), "commit", "-m", commitMessage);

    ObjectNode result = mapper.createObjectNode();
    result.put("success", true);
    result.put("path", issuePath.toString());
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
  }

  /**
   * Updates parent version STATE.md to add issue to pending list.
   *
   * @param parentStatePath path to parent version STATE.md
   * @param issueName name of the issue to add
   * @throws IOException if the file cannot be read or written
   */
  private void updateParentState(Path parentStatePath, String issueName) throws IOException
  {
    if (!Files.exists(parentStatePath))
      throw new IOException("Parent STATE.md not found: " + parentStatePath);

    String content = Files.readString(parentStatePath, StandardCharsets.UTF_8);

    if (content.contains("## Issues Pending"))
    {
      String[] lines = content.split("\n", -1);
      StringBuilder newContent = new StringBuilder();
      boolean inPendingSection = false;
      int lastIssueIndex = -1;

      for (int i = 0; i < lines.length; ++i)
      {
        if (lines[i].strip().equals("## Issues Pending"))
        {
          inPendingSection = true;
        }
        else if (inPendingSection && lines[i].strip().startsWith("##"))
        {
          inPendingSection = false;
        }
        else if (inPendingSection && lines[i].strip().startsWith("-"))
        {
          lastIssueIndex = i;
        }
      }

      for (int i = 0; i < lines.length; ++i)
      {
        newContent.append(lines[i]).append('\n');
        if (i == lastIssueIndex && lastIssueIndex != -1)
          newContent.append("- ").append(issueName).append('\n');
        else if (lastIssueIndex == -1 && lines[i].strip().equals("## Issues Pending"))
          newContent.append("- ").append(issueName).append('\n');
      }

      content = newContent.toString();
      if (content.endsWith("\n"))
        content = content.substring(0, content.length() - 1);
    }
    else
    {
      content += "\n\n## Issues Pending\n- " + issueName + "\n";
    }

    Files.writeString(parentStatePath, content, StandardCharsets.UTF_8);
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
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: create-issue [--json <json-string>] (or read from stdin)"
        }""");
      System.exit(1);
      return;
    }

    IssueCreator creator = new IssueCreator();
    try
    {
      String result = creator.execute(jsonInput);
      System.out.println(result);
    }
    catch (IOException e)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "%s"
        }""".formatted(e.getMessage().replace("\"", "\\\"")));
      System.exit(1);
    }
  }
}
