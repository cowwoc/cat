/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.ClaudeEnv;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery;
import io.github.cowwoc.cat.hooks.util.IssueGoalReader;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for next issue box with issue discovery.
 * <p>
 * Combines lock release, next issue discovery, and box rendering into a single operation.
 */
public final class GetNextIssueOutput implements SkillOutput
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetNextIssueOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetNextIssueOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Parses CLI-style arguments into a named argument map.
   * <p>
   * Iterates {@code args} as alternating key/value pairs. Recognized keys are
   * {@code --completed-issue}, {@code --target-branch}, {@code --session-id},
   * {@code --project-dir}, and {@code --exclude-pattern}. Unrecognized keys are silently ignored.
   *
   * @param args the arguments to parse (alternating key/value pairs)
   * @return a record holding the parsed argument values; unrecognized or absent keys leave the
   *   corresponding field empty
   * @throws NullPointerException if {@code args} is null
   */
  private static ParsedArgs parseArgs(String[] args)
  {
    requireThat(args, "args").isNotNull();
    String completedIssue = "";
    String targetBranch = "";
    String sessionId = "";
    String projectPath = "";
    String excludePattern = "";

    for (int i = 0; i + 1 < args.length; i += 2)
    {
      switch (args[i])
      {
        case "--completed-issue" -> completedIssue = args[i + 1];
        case "--target-branch" -> targetBranch = args[i + 1];
        case "--session-id" -> sessionId = args[i + 1];
        case "--project-dir" -> projectPath = args[i + 1];
        case "--exclude-pattern" -> excludePattern = args[i + 1];
        default ->
        {
        }
      }
    }
    return new ParsedArgs(completedIssue, targetBranch, sessionId, projectPath, excludePattern);
  }

  /**
   * Holds the parsed CLI argument values for a work-complete invocation.
   *
   * @param completedIssue the value of {@code --completed-issue}, or empty if absent
   * @param targetBranch the value of {@code --target-branch}, or empty if absent
   * @param sessionId the value of {@code --session-id}, or empty if absent
   * @param projectPath the value of {@code --project-dir}, or empty if absent
   * @param excludePattern the value of {@code --exclude-pattern}, or empty if absent
   */
  private record ParsedArgs(String completedIssue, String targetBranch, String sessionId, String projectPath,
    String excludePattern)
  {
  }

  /**
   * Generates the output for the work-complete skill by parsing CLI-style arguments.
   * <p>
   * Accepts {@code --completed-issue}, {@code --target-branch}, {@code --session-id}, and
   * {@code --project-dir} arguments. Falls back to the scope's session ID and the
   * {@code CLAUDE_PROJECT_DIR} environment variable when the corresponding arguments are absent.
   *
   * @param args the arguments from the preprocessor directive (alternating key/value pairs)
   * @return the formatted Issue Complete or Scope Complete box
   * @throws NullPointerException if {@code args} is null
   * @throws IOException if required arguments are missing or an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();

    ParsedArgs parsed = parseArgs(args);
    String completedIssue = parsed.completedIssue();
    String targetBranch = parsed.targetBranch();
    String sessionId = parsed.sessionId();
    String projectPath = parsed.projectPath();
    String excludePattern = parsed.excludePattern();

    if (sessionId.isBlank())
    {
      sessionId = new ClaudeEnv().getSessionId();
    }
    if (projectPath.isEmpty())
      projectPath = scope.getProjectPath().toString();

    if (completedIssue.isEmpty() || targetBranch.isEmpty() || projectPath.isEmpty())
    {
      throw new IOException("GetNextIssueOutput.getOutput() requires --completed-issue, --target-branch, " +
        "--session-id, and --project-dir arguments. Got: " + String.join(" ", args));
    }

    return getNextIssueBox(completedIssue, targetBranch, sessionId, projectPath, excludePattern);
  }

  /**
   * CLI entry point for generating next issue boxes with discovery.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    ParsedArgs parsed = parseArgs(args);
    try
    {
      String completedIssue = parsed.completedIssue();
      String targetBranch = parsed.targetBranch();
      String sessionId = parsed.sessionId();
      String projectPath = parsed.projectPath();
      String excludePattern = parsed.excludePattern();

      if (completedIssue.isEmpty() || targetBranch.isEmpty() || sessionId.isEmpty() || projectPath.isEmpty())
      {
        System.err.println("Usage: GetNextIssueOutput --completed-issue ID --target-branch BRANCH " +
          "--session-id ID --project-dir DIR [--exclude-pattern GLOB]");
        System.exit(1);
        return;
      }

      try (JvmScope scope = new MainJvmScope())
      {
        GetNextIssueOutput output = new GetNextIssueOutput(scope);
        String box = output.getNextIssueBox(completedIssue, targetBranch, sessionId, projectPath, excludePattern);
        System.out.println(box);
      }
    }
    catch (Exception e)
    {
      Logger log = LoggerFactory.getLogger(GetNextIssueOutput.class);
      log.error("ERROR generating next issue box", e);
      System.out.println();
      String completedIssue = parsed.completedIssue();
      String targetBranch = parsed.targetBranch();
      if (!completedIssue.isEmpty())
      {
        if (targetBranch.isEmpty())
          System.out.println(completedIssue + " merged.");
        else
          System.out.println(completedIssue + " merged to " + targetBranch + ".");
      }
      System.out.println();
      System.exit(1);
    }
  }

  /**
   * Generates an issue complete box with next issue discovery.
   * <p>
   * If lock release or issue discovery fails, the method gracefully degrades by returning a scope
   * complete box with warnings.
   *
   * @param completedIssue the ID of the completed issue
   * @param targetBranch the target branch that was merged to
   * @param sessionId the current session ID
   * @param projectPath the project root directory
   * @param excludePattern optional glob pattern to exclude issues (may be empty)
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any required parameter is blank
   */
  public String getNextIssueBox(String completedIssue, String targetBranch, String sessionId, String projectPath,
                                String excludePattern)
  {
    requireThat(completedIssue, "completedIssue").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectPath, "projectPath").isNotBlank();
    requireThat(excludePattern, "excludePattern").isNotNull();

    List<String> warnings = new ArrayList<>();
    releaseLock(completedIssue, sessionId, warnings);

    String effectiveExcludePattern = combineExcludePatterns(completedIssue, excludePattern);
    Map<String, Object> nextIssue = findNextIssue(sessionId, effectiveExcludePattern, warnings);

    if (!nextIssue.isEmpty())
    {
      String nextIssueId = nextIssue.getOrDefault("issue_id", "").toString();
      String nextIssuePath = nextIssue.getOrDefault("issue_path", "").toString();

      String goal;
      if (!nextIssuePath.isEmpty())
        goal = IssueGoalReader.readGoalFromPlan(Path.of(nextIssuePath, "PLAN.md"));
      else
        goal = "No goal available";

      GetIssueCompleteOutput issueCompleteOutput = new GetIssueCompleteOutput(scope);
      return issueCompleteOutput.getIssueCompleteBox(completedIssue, nextIssueId, goal, targetBranch);
    }
    String scopeName = extractScope(completedIssue);
    GetIssueCompleteOutput issueCompleteOutput = new GetIssueCompleteOutput(scope);
    return issueCompleteOutput.getScopeCompleteBox(scopeName);
  }

  /**
   * Releases the lock for the completed issue.
   *
   * @param issueId the issue ID to release
   * @param sessionId the current session ID
   * @param warnings list to collect warning messages
   */
  private void releaseLock(String issueId, String sessionId, List<String> warnings)
  {
    try
    {
      new IssueLock(scope).release(issueId, sessionId);
    }
    catch (Exception e)
    {
      warnings.add("WARNING: Failed to release lock for " + issueId + ": " + e.getMessage());
    }
  }

  /**
   * Finds the next available issue using IssueDiscovery with {@link IssueDiscovery.Scope#ALL}.
   * <p>
   * Uses {@code Scope.ALL} intentionally: the banner display must show the globally next issue
   * across all versions, not just the current minor version. Compare with
   * {@link GetIssueCompleteOutput#discoverAndRender}, which uses {@code Scope.VERSION} to find the
   * next issue within the current minor version for the issue-complete box.
   *
   * @param sessionId the current session ID
   * @param excludePattern optional glob pattern to exclude issues (may be empty)
   * @param warnings list to collect warning messages
   * @return map with issue info if found, empty map otherwise
   */
  private Map<String, Object> findNextIssue(String sessionId, String excludePattern,
    List<String> warnings)
  {
    try
    {
      IssueDiscovery discovery = new IssueDiscovery(scope);
      IssueDiscovery.SearchOptions options = new IssueDiscovery.SearchOptions(
        IssueDiscovery.Scope.ALL, "", sessionId, excludePattern, false);
      IssueDiscovery.DiscoveryResult result = discovery.findNextIssue(options);
      if (result instanceof IssueDiscovery.DiscoveryResult.Found found)
      {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "found");
        data.put("issue_id", found.issueId());
        data.put("issue_path", found.issuePath());
        return data;
      }
      return Map.of();
    }
    catch (Exception e)
    {
      warnings.add("WARNING: Failed to find next issue: " + e.getMessage());
      return Map.of();
    }
  }

  /**
   * Extracts the scope from a completed issue ID.
   * <p>
   * Handles all version formats: major ({@code 2-xxx} -> {@code v2}),
   * major.minor ({@code 2.1-xxx} -> {@code v2.1}), and
   * major.minor.patch ({@code 2.1.3-xxx} -> {@code v2.1.3}).
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
   * Combines two exclude patterns into a single glob pattern.
   * <p>
   * If both are non-empty, uses {@code {pattern1,pattern2}} glob syntax.
   * If only one is non-empty, returns it directly.
   * If both are empty, returns an empty string (no exclusion).
   *
   * @param pattern1 the first pattern (may be empty)
   * @param pattern2 the second pattern (may be empty)
   * @return the combined glob pattern
   */
  private String combineExcludePatterns(String pattern1, String pattern2)
  {
    if (pattern1.isEmpty())
      return pattern2;
    if (pattern2.isEmpty())
      return pattern1;
    return "{" + pattern1 + "," + pattern2 + "}";
  }
}
