package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for next task box with task discovery.
 *
 * Combines lock release, next task discovery, and box rendering into a single operation.
 */
public final class GetNextTaskOutput
{
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetNextTaskOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetNextTaskOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * CLI entry point for generating next task boxes with discovery.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try
    {
      String completedIssue = "";
      String baseBranch = "";
      String sessionId = "";
      String projectDir = "";
      String excludePattern = "";

      for (int i = 0; i + 1 < args.length; i += 2)
      {
        switch (args[i])
        {
          case "--completed-issue" -> completedIssue = args[i + 1];
          case "--base-branch" -> baseBranch = args[i + 1];
          case "--session-id" -> sessionId = args[i + 1];
          case "--project-dir" -> projectDir = args[i + 1];
          case "--exclude-pattern" -> excludePattern = args[i + 1];
          default ->
          {
          }
        }
      }

      if (completedIssue.isEmpty() || baseBranch.isEmpty() || sessionId.isEmpty() || projectDir.isEmpty())
      {
        System.err.println("Usage: GetNextTaskOutput --completed-issue ID --base-branch BRANCH " +
          "--session-id ID --project-dir DIR [--exclude-pattern GLOB]");
        System.exit(1);
        return;
      }

      try (JvmScope scope = new MainJvmScope())
      {
        GetNextTaskOutput output = new GetNextTaskOutput(scope);
        String box = output.getNextTaskBox(completedIssue, baseBranch, sessionId, projectDir, excludePattern);
        System.out.println(box);
      }
    }
    catch (Exception e)
    {
      System.err.println("ERROR generating next task box: " + e.getMessage());
      System.out.println();
      if (args.length >= 2)
      {
        for (int i = 0; i < args.length; ++i)
        {
          if (args[i].equals("--completed-issue") && i + 1 < args.length)
          {
            String completedIssue = args[i + 1];
            String baseBranch = "main";
            for (int j = 0; j < args.length; ++j)
            {
              if (args[j].equals("--base-branch") && j + 1 < args.length)
                baseBranch = args[j + 1];
            }
            System.out.println(completedIssue + " merged to " + baseBranch + ".");
            break;
          }
        }
      }
      System.out.println();
      System.exit(1);
    }
  }

  /**
   * Generates an issue complete box with next task discovery.
   * <p>
   * Depends on external resources:
   * <ul>
   *   <li>CLAUDE_PLUGIN_ROOT environment variable must be set</li>
   *   <li>issue-lock.sh script must exist at $CLAUDE_PLUGIN_ROOT/scripts/issue-lock.sh</li>
   *   <li>get-available-issues.sh script must exist at $CLAUDE_PLUGIN_ROOT/scripts/get-available-issues.sh</li>
   * </ul>
   * <p>
   * If external scripts are missing or fail, the method gracefully degrades by:
   * <ul>
   *   <li>Skipping lock release if issue-lock.sh is missing</li>
   *   <li>Generating scope complete box if get-available-issues.sh is missing or returns no tasks</li>
   * </ul>
   *
   * @param completedIssue the ID of the completed issue
   * @param baseBranch the base branch that was merged to
   * @param sessionId the current session ID
   * @param projectDir the project root directory
   * @param excludePattern optional glob pattern to exclude issues (may be empty)
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any required parameter is blank
   */
  public String getNextTaskBox(String completedIssue, String baseBranch, String sessionId, String projectDir,
                               String excludePattern)
  {
    requireThat(completedIssue, "completedIssue").isNotBlank();
    requireThat(baseBranch, "baseBranch").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectDir, "projectDir").isNotBlank();
    requireThat(excludePattern, "excludePattern").isNotNull();

    releaseLock(projectDir, completedIssue, sessionId);

    Map<String, Object> nextTask = findNextTask(projectDir, sessionId, excludePattern);

    if (!nextTask.isEmpty())
    {
      String nextIssueId = nextTask.getOrDefault("issue_id", "").toString();
      String nextIssuePath = nextTask.getOrDefault("issue_path", "").toString();

      String goal;
      if (!nextIssuePath.isEmpty())
        goal = readIssueGoal(nextIssuePath);
      else
        goal = "No goal available";

      GetIssueCompleteOutput issueCompleteOutput = new GetIssueCompleteOutput(scope);
      return issueCompleteOutput.getIssueCompleteBox(completedIssue, nextIssueId, goal, baseBranch);
    }
    String scopeName = extractScope(completedIssue);
    GetIssueCompleteOutput issueCompleteOutput = new GetIssueCompleteOutput(scope);
    return issueCompleteOutput.getScopeCompleteBox(scopeName);
  }

  /**
   * Releases the lock for the completed issue.
   *
   * @param projectDir the project root directory
   * @param issueId the issue ID to release
   * @param sessionId the current session ID
   */
  private void releaseLock(String projectDir, String issueId, String sessionId)
  {
    try
    {
      String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
      if (pluginRoot == null || pluginRoot.isEmpty())
        return;

      Path lockScript = Path.of(pluginRoot, "scripts", "issue-lock.sh");
      if (!Files.exists(lockScript))
        return;

      List<String> command = List.of(
        lockScript.toString(),
        "release",
        projectDir,
        issueId,
        sessionId);

      ProcessRunner.runAndCaptureIgnoreExit(command);
    }
    catch (Exception e)
    {
      System.err.println("WARNING: Failed to release lock for " + issueId + ": " + e.getMessage());
    }
  }

  /**
   * Finds the next available task using get-available-issues.sh.
   *
   * @param projectDir the project root directory
   * @param sessionId the current session ID
   * @param excludePattern optional glob pattern to exclude issues (may be empty)
   * @return map with task info if found, empty map otherwise
   */
  private Map<String, Object> findNextTask(String projectDir, String sessionId, String excludePattern)
  {
    try
    {
      String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
      if (pluginRoot == null || pluginRoot.isEmpty())
        return Map.of();

      Path discoveryScript = Path.of(pluginRoot, "scripts", "get-available-issues.sh");
      if (!Files.exists(discoveryScript))
        return Map.of();

      List<String> command = new ArrayList<>();
      command.add(discoveryScript.toString());
      command.add("--scope");
      command.add("all");
      command.add("--session-id");
      command.add(sessionId);

      if (!excludePattern.isEmpty())
      {
        command.add("--exclude-pattern");
        command.add(excludePattern);
      }

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(Path.of(projectDir).toFile());
      pb.redirectErrorStream(true);

      String output = ProcessRunner.runAndCapture(command);
      if (output == null)
        return Map.of();

      JsonMapper mapper = scope.getJsonMapper();
      Map<String, Object> data = mapper.readValue(output, MAP_TYPE);

      String status = data.getOrDefault("status", "").toString();
      if (status.equals("found"))
        return data;
      return Map.of();
    }
    catch (Exception e)
    {
      System.err.println("WARNING: Failed to find next task: " + e.getMessage());
      return Map.of();
    }
  }

  /**
   * Reads the goal from PLAN.md in the issue directory.
   *
   * @param issuePath the path to the issue directory
   * @return the goal text (first paragraph after Goal heading)
   */
  private String readIssueGoal(String issuePath)
  {
    try
    {
      Path planPath = Path.of(issuePath, "PLAN.md");
      if (!Files.exists(planPath))
        return "No goal found";

      List<String> lines = Files.readAllLines(planPath);

      int goalStart = -1;
      for (int i = 0; i < lines.size(); ++i)
      {
        String line = lines.get(i).strip();
        if (line.startsWith("## Goal"))
        {
          goalStart = i + 1;
          break;
        }
      }

      if (goalStart == -1)
        return "No goal found";

      List<String> goalLines = new ArrayList<>();
      for (int i = goalStart; i < lines.size(); ++i)
      {
        String line = lines.get(i);
        if (line.strip().startsWith("##"))
          break;
        goalLines.add(line.stripTrailing());
      }

      String goal = String.join("\n", goalLines).strip();

      String[] paragraphs = goal.split("\n\n");
      if (paragraphs.length > 0)
        return paragraphs[0].strip();
      return goal;
    }
    catch (IOException _)
    {
      return "Goal unavailable";
    }
  }

  /**
   * Extracts the scope from a completed issue ID.
   *
   * @param completedIssue the completed issue ID (e.g., "2.1-xxx")
   * @return the scope name (e.g., "v2.1")
   */
  private String extractScope(String completedIssue)
  {
    String[] parts = completedIssue.split("-");
    if (parts.length > 0)
    {
      String scope = parts[0];
      if (!scope.isEmpty() && Character.isDigit(scope.charAt(0)))
        return "v" + scope;
      return scope;
    }
    return "unknown";
  }

  /**
   * Public test helper for extractScope.
   *
   * @param completedIssue the completed issue ID
   * @return the scope name
   */
  public String extractScopePublic(String completedIssue)
  {
    return extractScope(completedIssue);
  }

  /**
   * Public test helper for readIssueGoal.
   *
   * @param issuePath the path to the issue directory
   * @return the goal text
   */
  public String readIssueGoalPublic(String issuePath)
  {
    return readIssueGoal(issuePath);
  }
}
