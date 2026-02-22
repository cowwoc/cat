/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.JvmScope;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

/**
 * Deterministic preparation phase for /cat:work. Orchestrates issue discovery, lock acquisition,
 * worktree creation, and metadata collection to produce a JSON result for the work skill.
 */
public final class WorkPrepare
{
  /**
   * The trust level for execution.
   */
  public enum TrustLevel
  {
    /**
     * Low trust: requires explicit approval for all operations.
     */
    LOW,
    /**
     * Medium trust: requires approval for destructive operations.
     */
    MEDIUM,
    /**
     * High trust: all operations are auto-approved.
     */
    HIGH;

    /**
     * Parses a trust level from a string value.
     *
     * @param value the string value to parse (case-insensitive)
     * @return the parsed trust level
     * @throws IllegalArgumentException if {@code value} is not a valid trust level
     * @throws NullPointerException if {@code value} is null
     */
    public static TrustLevel fromString(String value)
    {
      requireThat(value, "value").isNotNull();
      return switch (value.toLowerCase(java.util.Locale.ROOT))
      {
        case "low" -> LOW;
        case "medium" -> MEDIUM;
        case "high" -> HIGH;
        default -> throw new IllegalArgumentException("Invalid trust level: \"" + value +
          "\". Expected: low, medium, or high");
      };
    }

    @Override
    public String toString()
    {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  /**
   * Matches the status line in STATE.md: {@code - **Status:** open}.
   */
  private static final Pattern STATUS_PATTERN = Pattern.compile("\\*\\*Status:\\*\\*\\s+(\\S+)");
  /**
   * Matches the progress line in STATE.md: {@code - **Progress:** 0%}.
   */
  private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\*\\*Progress:\\*\\*\\s+\\d+%");
  /**
   * Matches the last-updated line in STATE.md: {@code - **Last Updated:** 2026-02-11}.
   */
  private static final Pattern LAST_UPDATED_PATTERN = Pattern.compile("\\*\\*Last Updated:\\*\\*\\s+.*");
  /**
   * Matches dependency entries in STATE.md: {@code - **Dependencies:** [dep1, dep2]}.
   */
  private static final Pattern DEPS_PATTERN = Pattern.compile("\\*\\*Dependencies:\\*\\*\\s+\\[([^\\]]*)\\]");
  /**
   * Matches the "Files to Create" section in PLAN.md.
   */
  private static final Pattern FILES_TO_CREATE_PATTERN =
    Pattern.compile("## Files to Create\\s+(.*?)(?=\\n##|\\Z)", Pattern.DOTALL);
  /**
   * Matches the "Files to Modify" section in PLAN.md.
   */
  private static final Pattern FILES_TO_MODIFY_PATTERN =
    Pattern.compile("## Files to Modify\\s+(.*?)(?=\\n##|\\Z)", Pattern.DOTALL);
  /**
   * Matches the "Execution Steps" section in PLAN.md.
   */
  private static final Pattern EXECUTION_STEPS_PATTERN =
    Pattern.compile("## Execution Steps\\s+(.*?)(?=\\n##|\\Z)", Pattern.DOTALL);
  /**
   * Hard limit for token estimation: issues estimated above this are considered oversized.
   */
  private static final int TOKEN_LIMIT = 160_000;
  /**
   * Maximum number of files to walk when scanning for diagnostic information.
   */
  private static final int DIAGNOSTIC_SCAN_LIMIT = 1000;
  /**
   * Type reference for deserializing JSON lock files into {@code Map<String, Object>}.
   */
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  private final JvmScope scope;

  /**
   * Creates a new WorkPrepare instance.
   *
   * @param scope the JVM scope providing project directory and shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public WorkPrepare(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Input parameters for the prepare phase.
   *
   * @param sessionId the Claude session ID (UUID format)
   * @param excludePattern glob pattern to exclude issues by name, or empty string for none
   * @param issueId specific issue ID to select, or empty string for priority-based selection
   * @param trustLevel the trust level for execution
   */
  public record PrepareInput(String sessionId, String excludePattern, String issueId,
    TrustLevel trustLevel)
  {
    /**
     * Creates new prepare input.
     *
     * @param sessionId the Claude session ID (UUID format)
     * @param excludePattern glob pattern to exclude issues by name, or empty string for none
     * @param issueId specific issue ID to select, or empty string for priority-based selection
     * @param trustLevel the trust level for execution
     * @throws IllegalArgumentException if {@code sessionId} is blank
     * @throws NullPointerException if {@code excludePattern}, {@code issueId}, or {@code trustLevel}
     *                              are null
     */
    public PrepareInput
    {
      requireThat(sessionId, "sessionId").isNotBlank();
      requireThat(excludePattern, "excludePattern").isNotNull();
      requireThat(issueId, "issueId").isNotNull();
      requireThat(trustLevel, "trustLevel").isNotNull();
    }
  }

  /**
   * Executes the full preparation phase and returns a JSON result string.
   * <p>
   * JSON status values:
   * <ul>
   *   <li>{@code READY} - worktree created, ready to execute</li>
   *   <li>{@code NO_TASKS} - no executable tasks found</li>
   *   <li>{@code LOCKED} - selected issue is locked by another session</li>
   *   <li>{@code OVERSIZED} - estimated tokens exceed hard limit</li>
   *   <li>{@code ERROR} - unexpected error during preparation</li>
   * </ul>
   *
   * @param input the preparation input parameters
   * @return JSON string with preparation result
   * @throws NullPointerException if {@code input} is null
   * @throws IOException if file operations fail
   */
  public String execute(PrepareInput input) throws IOException
  {
    requireThat(input, "input").isNotNull();

    Path projectDir = scope.getClaudeProjectDir();
    JsonMapper mapper = scope.getJsonMapper();

    // Step 1: Verify CAT structure
    if (!verifyCatStructure(projectDir))
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "No .claude/cat/ directory or cat-config.json found"));
    }

    // Step 2: Find available task
    IssueDiscovery discovery = new IssueDiscovery(scope);
    IssueDiscovery.Scope discoveryScope;
    String discoveryTarget;

    if (!input.issueId().isEmpty())
    {
      discoveryScope = IssueDiscovery.Scope.ISSUE;
      discoveryTarget = input.issueId();
    }
    else
    {
      discoveryScope = IssueDiscovery.Scope.ALL;
      discoveryTarget = "";
    }

    IssueDiscovery.SearchOptions searchOptions = new IssueDiscovery.SearchOptions(
      discoveryScope, discoveryTarget, input.sessionId(), input.excludePattern(), false);

    // Step 3 (lock acquisition) is handled implicitly by IssueDiscovery.findNextIssue()
    IssueDiscovery.DiscoveryResult discoveryResult = discovery.findNextIssue(searchOptions);

    // Handle non-found statuses
    Optional<String> nonFoundResult = handleNonFoundResult(discoveryResult, projectDir, mapper);
    if (nonFoundResult.isPresent())
      return nonFoundResult.get();

    IssueDiscovery.DiscoveryResult.Found found =
      (IssueDiscovery.DiscoveryResult.Found) discoveryResult;

    // Extract issue info from discovery result
    String issueId = found.issueId();
    String major = found.major();
    String minor = found.minor();
    String issueName = found.issueName();
    Path issuePath = Path.of(found.issuePath());

    // Get base branch (current branch in project dir)
    String baseBranch = GitCommands.getCurrentBranch(projectDir.toString());

    // Step 4: Estimate tokens
    Path planPath = issuePath.resolve("PLAN.md");
    int estimatedTokens = estimateTokens(planPath);

    // Check if oversized
    if (estimatedTokens > TOKEN_LIMIT)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "OVERSIZED",
        "message", "Task estimated at " + estimatedTokens + " tokens (limit: " + TOKEN_LIMIT + ")",
        "suggestion", "Use /cat:decompose-issue to break into smaller tasks",
        "issue_id", issueId,
        "estimated_tokens", estimatedTokens));
    }

    // Steps 5-10: Create worktree and build READY result
    String issueBranch = buildIssueBranch(major, minor, found.patch(), issueName);
    return executeWithLock(input, projectDir, mapper, issueId, major, minor, issueName,
      issuePath, baseBranch, planPath, estimatedTokens, issueBranch);
  }

  /**
   * Handles discovery results that are not {@code Found}, returning a JSON response for each.
   * <p>
   * Returns an empty optional if the result is {@code Found}, indicating the caller should
   * continue with worktree creation.
   *
   * @param discoveryResult the discovery result to handle
   * @param projectDir the project root directory
   * @param mapper the JSON mapper for serialization
   * @return a JSON response string if the result is not Found, or empty if Found
   * @throws IOException if file operations or JSON serialization fail
   */
  private Optional<String> handleNonFoundResult(IssueDiscovery.DiscoveryResult discoveryResult,
    Path projectDir, JsonMapper mapper) throws IOException
  {
    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.NotFound)
    {
      DiagnosticInfo diagnostics = gatherDiagnosticInfo(projectDir);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("status", "NO_TASKS");
      result.put("message", "No executable tasks available");
      result.put("suggestion", "Use /cat:status to see available tasks");

      if (!diagnostics.blockedTasks().isEmpty())
        result.put("blocked_tasks", diagnostics.blockedTasks());
      if (!diagnostics.lockedTasks().isEmpty())
        result.put("locked_tasks", diagnostics.lockedTasks());

      result.put("closed_count", diagnostics.closedCount());
      result.put("total_count", diagnostics.totalCount());

      return Optional.of(mapper.writeValueAsString(result));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.NotExecutable notExec)
    {
      String msg = notExec.reason();
      if (msg.contains("locked"))
      {
        return Optional.of(mapper.writeValueAsString(Map.of(
          "status", "LOCKED",
          "message", msg,
          "issue_id", notExec.issueId(),
          "locked_by", "")));
      }
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", msg)));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.AlreadyComplete alreadyComplete)
    {
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Issue " + alreadyComplete.issueId() + " is already closed")));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.Decomposed decomposed)
    {
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "NO_TASKS",
        "message", "Issue " + decomposed.issueId() + " is a decomposed parent task with open " +
          "sub-issues - work on its sub-issues first, then the parent will become available " +
          "automatically when all sub-issues are closed",
        "suggestion", "Use /cat:status to see available sub-issues")));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.Blocked blocked)
    {
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Issue " + blocked.issueId() + " is blocked by: " +
          String.join(", ", blocked.blockingIssues()))));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.ExistingWorktree existingWorktree)
    {
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Issue " + existingWorktree.issueId() + " has an existing worktree at: " +
          existingWorktree.worktreePath())));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.DiscoveryError error)
    {
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", error.message())));
    }

    if (!(discoveryResult instanceof IssueDiscovery.DiscoveryResult.Found))
    {
      return Optional.of(mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Unexpected discovery result: " + discoveryResult.getClass().getSimpleName())));
    }

    return Optional.empty();
  }

  /**
   * Executes the worktree creation and metadata collection steps after a Found result.
   *
   * @param input the preparation input parameters
   * @param projectDir the project root directory
   * @param mapper the JSON mapper for serialization
   * @param issueId the qualified issue ID
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the bare issue name
   * @param issuePath the path to the issue directory
   * @param baseBranch the base branch name
   * @param planPath the path to PLAN.md
   * @param estimatedTokens the estimated token count
   * @param issueBranch the issue branch name
   * @return JSON string with READY or ERROR result
   * @throws IOException if file operations fail
   */
  private String executeWithLock(PrepareInput input, Path projectDir, JsonMapper mapper,
    String issueId, String major, String minor,
    String issueName, Path issuePath, String baseBranch, Path planPath, int estimatedTokens,
    String issueBranch) throws IOException
  {
    // Step 5: Create worktree
    Path worktreePath;
    try
    {
      worktreePath = createWorktree(projectDir, issueBranch, baseBranch);
    }
    catch (IOException e)
    {
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to create worktree: " + e.getMessage()));
    }

    // Step 6: Verify worktree branch
    try
    {
      String actualBranch = GitCommands.getCurrentBranch(worktreePath.toString());
      if (!actualBranch.equals(issueBranch))
      {
        cleanupWorktree(projectDir, worktreePath);
        releaseLock(issueId, input.sessionId());
        return mapper.writeValueAsString(Map.of(
          "status", "ERROR",
          "message", "Worktree created on wrong branch (expected: " + issueBranch +
            ", actual: " + actualBranch + ")"));
      }
    }
    catch (IOException e)
    {
      cleanupWorktree(projectDir, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to verify worktree branch: " + e.getMessage()));
    }

    // Step 7: Check for existing work
    ExistingWorkChecker.CheckResult existingWork;
    try
    {
      existingWork = ExistingWorkChecker.check(worktreePath.toString(), baseBranch);
    }
    catch (IOException e)
    {
      cleanupWorktree(projectDir, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to check existing work: " + e.getMessage()));
    }

    // Step 8: Check base branch for suspicious commits
    String suspiciousCommits = checkBaseBranchCommits(projectDir, baseBranch, issueName);

    // Step 9: Update STATE.md in worktree
    try
    {
      updateStateMd(worktreePath, issuePath, projectDir);
    }
    catch (IOException e)
    {
      cleanupWorktree(projectDir, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to update STATE.md: " + e.getMessage()));
    }

    // Read goal from PLAN.md
    String goal = readGoalFromPlan(planPath);

    // Step 10: Return READY JSON
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "READY");
    result.put("issue_id", issueId);
    result.put("major", major);
    result.put("minor", minor);
    result.put("issue_name", issueName);
    result.put("issue_path", issuePath.toString());
    result.put("worktree_path", worktreePath.toString());
    result.put("branch", issueBranch);
    result.put("base_branch", baseBranch);
    result.put("estimated_tokens", estimatedTokens);
    result.put("percent_of_threshold", (int) ((estimatedTokens / (double) TOKEN_LIMIT) * 100));
    result.put("goal", goal);
    result.put("approach_selected", "auto");
    result.put("lock_acquired", true);
    result.put("has_existing_work", existingWork.hasExistingWork());
    result.put("existing_commits", existingWork.existingCommits());
    result.put("commit_summary", existingWork.commitSummary());

    if (!suspiciousCommits.isEmpty())
    {
      result.put("potentially_complete", true);
      result.put("suspicious_commits", suspiciousCommits);
    }

    return mapper.writeValueAsString(result);
  }

  /**
   * Verifies that the project has a valid CAT structure.
   *
   * @param projectDir the project root directory
   * @return true if the structure is valid
   */
  private boolean verifyCatStructure(Path projectDir)
  {
    Path catDir = projectDir.resolve(".claude").resolve("cat");
    Path configFile = catDir.resolve("cat-config.json");
    return Files.isDirectory(catDir) && Files.isRegularFile(configFile);
  }

  /**
   * Gathers diagnostic information when no tasks are available.
   * <p>
   * Scans issue directories to find blocked tasks, locked tasks, and closed/total counts.
   *
   * @param projectDir the project root directory
   * @return the diagnostic info
   * @throws IOException if file operations fail
   */
  private DiagnosticInfo gatherDiagnosticInfo(Path projectDir) throws IOException
  {
    Path issuesDir = projectDir.resolve(".claude").resolve("cat").resolve("issues");

    Map<String, IssueIndexEntry> issueIndex = new LinkedHashMap<>();
    Map<String, List<String>> bareNameIndex = new LinkedHashMap<>();
    buildIssueIndex(issuesDir, issueIndex, bareNameIndex);

    List<Map<String, Object>> blockedTasks = findBlockedTasks(issueIndex, bareNameIndex);
    int closedCount = 0;
    int totalCount = 0;

    for (IssueIndexEntry entry : issueIndex.values())
    {
      ++totalCount;
      Matcher statusMatcher = STATUS_PATTERN.matcher(entry.content());
      if (statusMatcher.find() && statusMatcher.group(1).equals("closed"))
        ++closedCount;
    }

    List<Map<String, Object>> lockedTasks = findLockedTasks(projectDir);

    return new DiagnosticInfo(blockedTasks, lockedTasks, closedCount, totalCount);
  }

  /**
   * Builds the issue index and bare name index from STATE.md files.
   * <p>
   * Populates the provided maps with qualified issue names mapped to their state content,
   * and bare issue names mapped to lists of qualified names for ambiguous lookups.
   *
   * @param issuesDir the issues directory to scan
   * @param issueIndex the map to populate with qualified name to issue entry mappings
   * @param bareNameIndex the map to populate with bare name to qualified name list mappings
   * @throws IOException if file operations fail
   */
  private void buildIssueIndex(Path issuesDir, Map<String, IssueIndexEntry> issueIndex,
    Map<String, List<String>> bareNameIndex) throws IOException
  {
    if (!Files.isDirectory(issuesDir))
      return;

    try (Stream<Path> stream = Files.walk(issuesDir, 4).limit(DIAGNOSTIC_SCAN_LIMIT))
    {
      List<Path> stateFiles = stream.
        filter(p -> p.getFileName().toString().equals("STATE.md")).
        toList();

      for (Path stateFile : stateFiles)
      {
        String issueName = stateFile.getParent().getFileName().toString();
        String versionDirName = stateFile.getParent().getParent().getFileName().toString();
        String qualifiedName;
        if (versionDirName.startsWith("v"))
        {
          String version = versionDirName.substring(1);
          qualifiedName = version + "-" + issueName;
        }
        else
        {
          qualifiedName = issueName;
        }

        String content = Files.readString(stateFile);
        issueIndex.put(qualifiedName, new IssueIndexEntry(stateFile, content));
        bareNameIndex.computeIfAbsent(issueName, k -> new ArrayList<>()).add(qualifiedName);
      }
    }
  }

  /**
   * Finds blocked tasks from the issue index by checking unresolved dependencies.
   *
   * @param issueIndex the qualified name to issue entry index
   * @param bareNameIndex the bare name to qualified name list index
   * @return a list of blocked task maps with issue_id, blocked_by, and reason fields
   */
  private List<Map<String, Object>> findBlockedTasks(Map<String, IssueIndexEntry> issueIndex,
    Map<String, List<String>> bareNameIndex)
  {
    List<Map<String, Object>> blockedTasks = new ArrayList<>();

    for (Map.Entry<String, IssueIndexEntry> entry : issueIndex.entrySet())
    {
      String qualifiedIssueName = entry.getKey();
      String content = entry.getValue().content();

      Matcher statusMatcher = STATUS_PATTERN.matcher(content);
      if (!statusMatcher.find())
        continue;

      String status = statusMatcher.group(1);
      if (!status.equals("open") && !status.equals("in-progress"))
        continue;

      Matcher depsMatcher = DEPS_PATTERN.matcher(content);
      if (!depsMatcher.find())
        continue;

      String depsStr = depsMatcher.group(1).strip();
      if (depsStr.isEmpty())
        continue;

      String[] depsArr = depsStr.split(",");
      List<Map<String, String>> unresolvedDeps = new ArrayList<>();

      for (String dep : depsArr)
      {
        String depId = dep.strip();
        if (depId.isEmpty())
          continue;

        IssueIndexEntry depData = issueIndex.get(depId);
        if (depData == null)
        {
          List<String> candidates = bareNameIndex.getOrDefault(depId, List.of());
          if (candidates.size() == 1)
            depData = issueIndex.get(candidates.get(0));
        }

        if (depData != null)
        {
          Matcher depStatusMatcher = STATUS_PATTERN.matcher(depData.content());
          if (depStatusMatcher.find())
          {
            String depStatus = depStatusMatcher.group(1);
            if (!depStatus.equals("closed"))
              unresolvedDeps.add(Map.of("id", depId, "status", depStatus));
          }
          else
          {
            unresolvedDeps.add(Map.of("id", depId, "status", "unknown"));
          }
        }
        else
        {
          unresolvedDeps.add(Map.of("id", depId, "status", "not_found"));
        }
      }

      if (!unresolvedDeps.isEmpty())
      {
        List<String> blockedBy = new ArrayList<>();
        List<String> reasonParts = new ArrayList<>();
        for (Map<String, String> depEntry : unresolvedDeps)
        {
          blockedBy.add(depEntry.get("id"));
          reasonParts.add(depEntry.get("id") + " (" + depEntry.get("status") + ")");
        }

        Map<String, Object> blockedTask = new LinkedHashMap<>();
        blockedTask.put("issue_id", qualifiedIssueName);
        blockedTask.put("blocked_by", blockedBy);
        blockedTask.put("reason", String.join(", ", reasonParts));
        blockedTasks.add(blockedTask);
      }
    }

    return blockedTasks;
  }

  /**
   * Finds locked tasks by scanning the locks directory.
   *
   * @param projectDir the project root directory
   * @return a list of locked task maps with issue_id and locked_by fields
   * @throws IOException if file operations fail
   */
  private List<Map<String, Object>> findLockedTasks(Path projectDir) throws IOException
  {
    List<Map<String, Object>> lockedTasks = new ArrayList<>();
    Path locksDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");

    if (!Files.isDirectory(locksDir))
      return lockedTasks;

    try (Stream<Path> stream = Files.list(locksDir))
    {
      List<Path> lockFiles = stream.
        filter(p -> p.getFileName().toString().endsWith(".lock")).
        toList();

      JsonMapper mapper = scope.getJsonMapper();
      for (Path lockFile : lockFiles)
      {
        try
        {
          String lockContent = Files.readString(lockFile);
          Map<String, Object> lockData = mapper.readValue(lockContent, MAP_TYPE);

          String lockIssueId = lockFile.getFileName().toString().
            substring(0, lockFile.getFileName().toString().length() - ".lock".length());

          Map<String, Object> lockedTask = new LinkedHashMap<>();
          lockedTask.put("issue_id", lockIssueId);
          lockedTask.put("locked_by", lockData.getOrDefault("session_id", "unknown").toString());
          lockedTasks.add(lockedTask);
        }
        catch (IOException _)
        {
          // Skip malformed lock files
        }
      }
    }

    return lockedTasks;
  }

  /**
   * Estimates the token count heuristically from PLAN.md.
   * <p>
   * Heuristic:
   * <ul>
   *   <li>Files to create: 5000 tokens each</li>
   *   <li>Files to modify: 3000 tokens each</li>
   *   <li>Test files (in either create or modify sections): 4000 additional tokens each</li>
   *   <li>Execution steps: 2000 tokens each</li>
   *   <li>Base overhead: 10000 tokens</li>
   * </ul>
   *
   * @param planPath the path to PLAN.md
   * @return the estimated token count
   */
  private int estimateTokens(Path planPath)
  {
    if (!Files.isRegularFile(planPath))
      return 10_000;

    String content;
    try
    {
      content = Files.readString(planPath);
    }
    catch (IOException _)
    {
      return 10_000;
    }

    // Count files to create
    Matcher createSection = FILES_TO_CREATE_PATTERN.matcher(content);
    int filesToCreate = 0;
    int testFilesInCreate = 0;
    if (createSection.find())
    {
      String createContent = createSection.group(1);
      for (String line : createContent.split("\n"))
      {
        if (line.strip().startsWith("-"))
        {
          ++filesToCreate;
          if (line.toLowerCase(java.util.Locale.ROOT).contains("test"))
            ++testFilesInCreate;
        }
      }
    }

    // Count files to modify
    Matcher modifySection = FILES_TO_MODIFY_PATTERN.matcher(content);
    int filesToModify = 0;
    int testFilesInModify = 0;
    if (modifySection.find())
    {
      String modifyContent = modifySection.group(1);
      for (String line : modifyContent.split("\n"))
      {
        if (line.strip().startsWith("-"))
        {
          ++filesToModify;
          if (line.toLowerCase(java.util.Locale.ROOT).contains("test"))
            ++testFilesInModify;
        }
      }
    }

    int testFiles = testFilesInCreate + testFilesInModify;

    // Count execution steps
    Matcher stepsSection = EXECUTION_STEPS_PATTERN.matcher(content);
    int steps = 0;
    if (stepsSection.find())
    {
      for (String line : stepsSection.group(1).split("\n"))
      {
        if (line.strip().matches("^\\d+\\..*"))
          ++steps;
      }
    }

    return filesToCreate * 5_000 + filesToModify * 3_000 + testFiles * 4_000 + steps * 2_000 + 10_000;
  }

  /**
   * Creates a git worktree for the issue branch.
   * <p>
   * If the branch already exists (stale from a previous session), it is deleted first.
   * Also writes the {@code cat-base} file recording the base branch for merge operations.
   *
   * @param projectDir the project root directory
   * @param issueBranch the branch name for the issue
   * @param baseBranch the base branch to branch from
   * @return the path to the created worktree
   * @throws IOException if worktree creation fails
   */
  private Path createWorktree(Path projectDir, String issueBranch, String baseBranch) throws IOException
  {
    Path worktreePath = projectDir.resolve(".claude").resolve("cat").resolve("worktrees").
      resolve(issueBranch);

    // Check if branch already exists (stale from previous session)
    try
    {
      GitCommands.runGit(projectDir, "rev-parse", "--verify", issueBranch);
      // Branch exists - delete it first
      GitCommands.runGit(projectDir, "branch", "-D", issueBranch);
    }
    catch (IOException _)
    {
      // Branch does not exist - that is fine
    }

    // Create worktree
    GitCommands.runGit(projectDir, "worktree", "add", "-b", issueBranch, worktreePath.toString(),
      "HEAD");

    // Write cat-base file
    String gitCommonDir = GitCommands.runGit(projectDir, "rev-parse", "--git-common-dir");
    Path catBaseFile = projectDir.resolve(gitCommonDir).resolve("worktrees").
      resolve(issueBranch).resolve("cat-base");
    Files.createDirectories(catBaseFile.getParent());
    Files.writeString(catBaseFile, baseBranch);

    return worktreePath;
  }

  /**
   * Checks the base branch for suspicious commits mentioning the issue name.
   * <p>
   * Filters out planning commits that merely add issue definitions (false positives).
   *
   * @param projectDir the project root directory
   * @param baseBranch the base branch to search
   * @param issueName the issue name to search for in commit messages
   * @return a newline-separated list of suspicious commit lines, or empty string if none found
   */
  private String checkBaseBranchCommits(Path projectDir, String baseBranch, String issueName)
  {
    try
    {
      String logOutput = GitCommands.runGit(projectDir, "log", "--oneline",
        "--grep=" + issueName, baseBranch, "-5");

      if (logOutput.isEmpty())
        return "";

      List<String> planningPrefixes = List.of(
        "planning:", "config: add issue", "config: add task", "config: mark", "config: decompose");

      List<String> filtered = new ArrayList<>();
      for (String line : logOutput.split("\n"))
      {
        if (line.isBlank())
          continue;
        int spaceIndex = line.indexOf(' ');
        String msg;
        if (spaceIndex >= 0)
          msg = line.substring(spaceIndex + 1);
        else
          msg = line;

        boolean isPlanning = false;
        for (String prefix : planningPrefixes)
        {
          if (msg.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
          {
            isPlanning = true;
            break;
          }
        }
        if (!isPlanning)
          filtered.add(line);
      }

      return String.join("\n", filtered);
    }
    catch (IOException _)
    {
      return "";
    }
  }

  /**
   * Updates STATE.md in the worktree to mark the issue as in-progress.
   *
   * @param worktreePath the path to the worktree
   * @param issuePath the absolute path to the issue directory in the main working tree
   * @param projectDir the project root directory
   * @throws IOException if file operations fail
   */
  private void updateStateMd(Path worktreePath, Path issuePath, Path projectDir) throws IOException
  {
    // STATE.md is in the worktree's copy of the issue directory
    Path relativeIssuePath = projectDir.relativize(issuePath);
    Path stateFile = worktreePath.resolve(relativeIssuePath).resolve("STATE.md");

    if (!Files.isRegularFile(stateFile))
    {
      throw new IOException("STATE.md not found in worktree: " + stateFile);
    }

    String content = Files.readString(stateFile);

    content = STATUS_PATTERN.matcher(content).replaceAll("**Status:** in-progress");
    content = PROGRESS_PATTERN.matcher(content).replaceAll("**Progress:** 0%");
    content = LAST_UPDATED_PATTERN.matcher(content).
      replaceAll("**Last Updated:** " + LocalDate.now());

    Files.writeString(stateFile, content);
  }

  /**
   * Reads the goal from PLAN.md.
   * <p>
   * Extracts the first paragraph of text under the {@code ## Goal} heading.
   *
   * @param planPath the path to PLAN.md
   * @return the goal text, or "No goal found" if absent
   */
  private String readGoalFromPlan(Path planPath)
  {
    if (!Files.isRegularFile(planPath))
      return "No goal found";

    List<String> lines;
    try
    {
      lines = Files.readAllLines(planPath);
    }
    catch (IOException _)
    {
      return "No goal found";
    }

    // Find ## Goal heading
    int goalStart = -1;
    for (int i = 0; i < lines.size(); ++i)
    {
      if (lines.get(i).strip().startsWith("## Goal"))
      {
        goalStart = i + 1;
        break;
      }
    }

    if (goalStart < 0)
      return "No goal found";

    // Extract text until next ## heading or end of file
    List<String> goalLines = new ArrayList<>();
    for (int i = goalStart; i < lines.size(); ++i)
    {
      String line = lines.get(i);
      if (line.strip().startsWith("##"))
        break;
      goalLines.add(line.stripTrailing());
    }

    String goal = String.join("\n", goalLines).strip();

    // Return first paragraph
    int blankIndex = goal.indexOf("\n\n");
    if (blankIndex >= 0)
      return goal.substring(0, blankIndex).strip();
    return goal;
  }

  /**
   * Releases the lock on an issue (best-effort, errors are swallowed).
   *
   * @param issueId the issue ID to release the lock for
   * @param sessionId the session ID that owns the lock
   */
  private void releaseLock(String issueId, String sessionId)
  {
    try
    {
      IssueLock issueLock = new IssueLock(scope);
      issueLock.release(issueId, sessionId);
    }
    catch (Exception _)
    {
      // Best-effort
    }
  }

  /**
   * Removes a worktree on failure (best-effort, errors are swallowed).
   *
   * @param projectDir the project root directory
   * @param worktreePath the path to the worktree to remove
   */
  private void cleanupWorktree(Path projectDir, Path worktreePath)
  {
    try
    {
      GitCommands.runGit(projectDir, "worktree", "remove", worktreePath.toString(), "--force");
    }
    catch (IOException _)
    {
      // Best-effort
    }
  }

  /**
   * Constructs the issue branch name from version components.
   * <p>
   * Format: {@code major.minor-issueName} (or {@code major.minor.patch-issueName} for patch versions).
   * For major-only versions: {@code major-issueName}.
   *
   * @param major the major version number
   * @param minor the minor version number, or empty for major-only
   * @param patch the patch version number, or empty if not patch-level
   * @param issueName the bare issue name
   * @return the issue branch name
   */
  private String buildIssueBranch(String major, String minor, String patch, String issueName)
  {
    if (minor.isEmpty())
      return major + "-" + issueName;
    if (patch.isEmpty())
      return major + "." + minor + "-" + issueName;
    return major + "." + minor + "." + patch + "-" + issueName;
  }

  /**
   * Holder for an issue's state file path and content.
   *
   * @param statePath the path to the STATE.md file
   * @param content the file content
   */
  private record IssueIndexEntry(Path statePath, String content)
  {
    /**
     * Creates a new issue index entry.
     *
     * @param statePath the path to the STATE.md file
     * @param content the file content
     * @throws NullPointerException if {@code statePath} or {@code content} are null
     */
    IssueIndexEntry
    {
      assert that(statePath, "statePath").isNotNull().elseThrow();
      assert that(content, "content").isNotNull().elseThrow();
    }
  }

  /**
   * Diagnostic information about the state of issues when no tasks are available.
   *
   * @param blockedTasks issues that are blocked by unsatisfied dependencies
   * @param lockedTasks issues that are locked by another session
   * @param closedCount the number of closed issues
   * @param totalCount the total number of issues
   */
  private record DiagnosticInfo(
    List<Map<String, Object>> blockedTasks,
    List<Map<String, Object>> lockedTasks,
    int closedCount,
    int totalCount)
  {
    /**
     * Creates new diagnostic info.
     *
     * @param blockedTasks issues that are blocked by unsatisfied dependencies
     * @param lockedTasks issues that are locked by another session
     * @param closedCount the number of closed issues
     * @param totalCount the total number of issues
     * @throws NullPointerException if {@code blockedTasks} or {@code lockedTasks} are null
     * @throws IllegalArgumentException if {@code closedCount} or {@code totalCount} are negative
     */
    DiagnosticInfo
    {
      assert that(blockedTasks, "blockedTasks").isNotNull().elseThrow();
      assert that(lockedTasks, "lockedTasks").isNotNull().elseThrow();
      assert that(closedCount, "closedCount").isGreaterThanOrEqualTo(0).elseThrow();
      assert that(totalCount, "totalCount").isGreaterThanOrEqualTo(0).elseThrow();
    }
  }
}
