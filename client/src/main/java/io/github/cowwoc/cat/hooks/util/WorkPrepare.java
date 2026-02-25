/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
   * Default safety threshold for the diagnostic scan: returns an error if the file-system walk
   * exceeds this number of entries. This prevents unbounded scans on pathological repositories
   * while allowing realistic projects (thousands of issues) to complete without silent truncation.
   */
  private static final int DEFAULT_DIAGNOSTIC_SCAN_SAFETY_THRESHOLD = 100_000;
  /**
   * Default maximum recursion depth for cycle detection. Prevents {@code StackOverflowError} on
   * deeply nested dependency chains or unusually large graphs.
   */
  private static final int DEFAULT_MAX_CYCLE_DETECTION_DEPTH = 1000;
  /**
   * Type reference for deserializing JSON lock files into {@code Map<String, Object>}.
   */
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  private final JvmScope scope;
  private final int diagnosticScanSafetyThreshold;
  private final int maxCycleDetectionDepth;

  /**
   * Creates a new WorkPrepare instance with default thresholds.
   *
   * @param scope the JVM scope providing project directory and shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public WorkPrepare(JvmScope scope)
  {
    this(scope, DEFAULT_DIAGNOSTIC_SCAN_SAFETY_THRESHOLD, DEFAULT_MAX_CYCLE_DETECTION_DEPTH);
  }

  /**
   * Creates a new WorkPrepare instance with configurable safety thresholds.
   * <p>
   * This constructor is intended for testing: pass small values to exercise error paths without
   * creating large numbers of real files or deeply nested dependency chains.
   *
   * @param scope the JVM scope providing project directory and shared services
   * @param diagnosticScanSafetyThreshold the maximum number of filesystem entries allowed during
   *                                      the diagnostic scan before throwing an error
   * @param maxCycleDetectionDepth the maximum DFS recursion depth allowed during cycle detection;
   *                               0 means only direct dependencies are traversed
   * @throws NullPointerException     if {@code scope} is null
   * @throws IllegalArgumentException if {@code diagnosticScanSafetyThreshold} is not positive or
   *                                  {@code maxCycleDetectionDepth} is negative
   */
  public WorkPrepare(JvmScope scope, int diagnosticScanSafetyThreshold, int maxCycleDetectionDepth)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(diagnosticScanSafetyThreshold, "diagnosticScanSafetyThreshold").isPositive();
    requireThat(maxCycleDetectionDepth, "maxCycleDetectionDepth").isGreaterThanOrEqualTo(0);
    this.scope = scope;
    this.diagnosticScanSafetyThreshold = diagnosticScanSafetyThreshold;
    this.maxCycleDetectionDepth = maxCycleDetectionDepth;
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
   *   <li>{@code NO_ISSUES} - no executable issues found</li>
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

    // Step 2: Find available issue
    IssueDiscovery discovery = new IssueDiscovery(scope);
    IssueDiscovery.Scope discoveryScope;
    String discoveryTarget;

    if (!input.issueId().isEmpty())
    {
      String id = input.issueId();
      // Detect whether the ID is fully-qualified (e.g. "2.1-fix-bug") or a bare name (e.g. "fix-bug")
      if (id.matches("^\\d+(?:\\.\\d+(?:\\.\\d+)?)?-[a-zA-Z][a-zA-Z0-9_-]*$"))
        discoveryScope = IssueDiscovery.Scope.ISSUE;
      else
        discoveryScope = IssueDiscovery.Scope.BARE_NAME;
      discoveryTarget = id;
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
    String nonFoundResult = handleNonFoundResult(discoveryResult, projectDir, mapper);
    if (!nonFoundResult.isEmpty())
      return nonFoundResult;

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
        "message", "Issue estimated at " + estimatedTokens + " tokens (limit: " + TOKEN_LIMIT + ")",
        "suggestion", "Use /cat:decompose-issue to break into smaller issues",
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
   * Returns an empty string if the result is {@code Found}, indicating the caller should
   * continue with worktree creation.
   *
   * @param discoveryResult the discovery result to handle
   * @param projectDir the project root directory
   * @param mapper the JSON mapper for serialization
   * @return a JSON response string if the result is not Found, or an empty string if Found
   * @throws IOException if file operations or JSON serialization fail
   */
  private String handleNonFoundResult(IssueDiscovery.DiscoveryResult discoveryResult,
    Path projectDir, JsonMapper mapper) throws IOException
  {
    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.NotFound)
    {
      DiagnosticInfo diagnostics = gatherDiagnosticInfo(projectDir);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("status", "NO_ISSUES");
      result.put("message", "No executable issues available");
      result.put("suggestion", "Use /cat:status to see available issues");

      if (!diagnostics.blockedIssues().isEmpty())
        result.put("blocked_issues", diagnostics.blockedIssues());
      if (!diagnostics.lockedIssues().isEmpty())
        result.put("locked_issues", diagnostics.lockedIssues());
      if (!diagnostics.circularDependencies().isEmpty())
        result.put("circular_dependencies", diagnostics.circularDependencies());

      result.put("closed_count", diagnostics.closedCount());
      result.put("total_count", diagnostics.totalCount());

      return mapper.writeValueAsString(result);
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.NotExecutable notExec)
    {
      String msg = notExec.reason();
      if (msg.contains("locked"))
      {
        return mapper.writeValueAsString(Map.of(
          "status", "LOCKED",
          "message", msg,
          "issue_id", notExec.issueId(),
          "locked_by", ""));
      }
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", msg));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.AlreadyComplete alreadyComplete)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Issue " + alreadyComplete.issueId() + " is already closed"));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.Decomposed decomposed)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "NO_ISSUES",
        "message", "Issue " + decomposed.issueId() + " is a decomposed parent issue with open " +
          "sub-issues - work on its sub-issues first, then the parent will become available " +
          "automatically when all sub-issues are closed",
        "suggestion", "Use /cat:status to see available sub-issues"));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.Blocked blocked)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Issue " + blocked.issueId() + " is blocked by: " +
          String.join(", ", blocked.blockingIssues())));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.ExistingWorktree existingWorktree)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Issue " + existingWorktree.issueId() + " has an existing worktree at: " +
          existingWorktree.worktreePath()));
    }

    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.DiscoveryError error)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", error.message()));
    }

    if (!(discoveryResult instanceof IssueDiscovery.DiscoveryResult.Found))
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Unexpected discovery result: " + discoveryResult.getClass().getSimpleName()));
    }

    return "";
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

    // Update lock with worktree path
    try
    {
      updateLockWorktree(issueId, input.sessionId(), worktreePath.toString());
    }
    catch (IOException e)
    {
      cleanupWorktree(projectDir, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to update lock with worktree path: " + e.getMessage()));
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
    String suspiciousCommits = checkBaseBranchCommits(projectDir, baseBranch, issueName, planPath);

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

    // Read pre-conditions from PLAN.md
    List<String> preconditions;
    try
    {
      preconditions = readPreconditionsFromPlan(planPath);
    }
    catch (IOException e)
    {
      cleanupWorktree(projectDir, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to read pre-conditions from PLAN.md: " + e.getMessage()));
    }

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
    result.put("preconditions", preconditions);
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
   * Gathers diagnostic information when no issues are available.
   * <p>
   * Scans issue directories to find blocked tasks, locked tasks, closed/total counts,
   * and circular dependencies.
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

    List<Map<String, Object>> blockedIssues = findBlockedIssues(issueIndex, bareNameIndex);
    List<String> circularDependencies = findCircularDependencies(issueIndex, bareNameIndex);
    int closedCount = 0;
    int totalCount = 0;

    for (IssueIndexEntry entry : issueIndex.values())
    {
      ++totalCount;
      Matcher statusMatcher = STATUS_PATTERN.matcher(entry.content());
      if (statusMatcher.find() && statusMatcher.group(1).equals("closed"))
        ++closedCount;
    }

    List<Map<String, Object>> lockedIssues = findLockedIssues(projectDir);

    return new DiagnosticInfo(blockedIssues, lockedIssues, closedCount, totalCount, circularDependencies);
  }

  /**
   * Builds the issue index and bare name index from STATE.md files.
   * <p>
   * Populates the provided maps with qualified issue names mapped to their state content,
   * and bare issue names mapped to lists of qualified names for ambiguous lookups.
   * <p>
   * Returns an error via IOException if the scan reaches {@link #diagnosticScanSafetyThreshold}
   * filesystem entries, preventing unbounded scans on pathological repositories.
   *
   * @param issuesDir the issues directory to scan
   * @param issueIndex the map to populate with qualified name to issue entry mappings
   * @param bareNameIndex the map to populate with bare name to qualified name list mappings
   * @throws IOException if file operations fail or the scan exceeds the safety threshold
   */
  private void buildIssueIndex(Path issuesDir, Map<String, IssueIndexEntry> issueIndex,
    Map<String, List<String>> bareNameIndex) throws IOException
  {
    if (!Files.isDirectory(issuesDir))
      return;

    try (Stream<Path> stream = Files.walk(issuesDir, 4))
    {
      List<Path> allEntries = stream.limit(diagnosticScanSafetyThreshold).toList();
      if (allEntries.size() == diagnosticScanSafetyThreshold)
      {
        throw new IOException(
          "Diagnostic scan exceeded safety threshold: " + issuesDir + " contains at least " +
            diagnosticScanSafetyThreshold +
            " filesystem entries. Consider archiving old issues.");
      }

      List<Path> stateFiles = allEntries.stream().
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
   * Returns the resolved dependency IDs for an open or in-progress issue.
   * <p>
   * Bare dependency names (without a version prefix) are resolved to qualified names via
   * {@code bareNameIndex}. When exactly one candidate exists, that candidate is used. When
   * multiple candidates exist (ambiguous), all candidates are added so cycle detection can
   * find cycles through any of them. When no candidates exist, the bare name is skipped
   * because it would not match any graph key.
   *
   * @param content the content of the issue's STATE.md file
   * @param issueIndex the qualified name to issue entry index
   * @param bareNameIndex the bare name to qualified name list index
   * @return the list of resolved dependency IDs, or an empty list if the issue is not active
   *         or has no dependencies
   */
  private List<String> resolveActiveDependencies(String content,
    Map<String, IssueIndexEntry> issueIndex, Map<String, List<String>> bareNameIndex)
  {
    Matcher statusMatcher = STATUS_PATTERN.matcher(content);
    if (!statusMatcher.find())
      return List.of();

    String status = statusMatcher.group(1);
    if (!status.equals("open") && !status.equals("in-progress"))
      return List.of();

    Matcher depsMatcher = DEPS_PATTERN.matcher(content);
    if (!depsMatcher.find())
      return List.of();

    String depsStr = depsMatcher.group(1).strip();
    if (depsStr.isEmpty())
      return List.of();

    String[] depsArr = depsStr.split(",");
    List<String> resolvedDeps = new ArrayList<>();
    for (String dep : depsArr)
    {
      String depId = dep.strip();
      if (depId.isEmpty())
        continue;

      if (issueIndex.containsKey(depId))
      {
        resolvedDeps.add(depId);
      }
      else
      {
        List<String> candidates = bareNameIndex.getOrDefault(depId, List.of());
        resolvedDeps.addAll(candidates);
      }
    }

    return resolvedDeps;
  }

  /**
   * Returns a map describing the status of a dependency.
   * <p>
   * Returns {@code {"id": depId, "status": "closed"|"open"|"in-progress"|"unknown"|"not_found"}}.
   *
   * @param depId the qualified dependency issue ID
   * @param issueIndex the qualified name to issue entry index
   * @return a map with "id" and "status" keys
   */
  private Map<String, String> getDependencyStatus(String depId,
    Map<String, IssueIndexEntry> issueIndex)
  {
    IssueIndexEntry depData = issueIndex.get(depId);
    if (depData == null)
      return Map.of("id", depId, "status", "not_found");

    Matcher depStatusMatcher = STATUS_PATTERN.matcher(depData.content());
    if (depStatusMatcher.find())
      return Map.of("id", depId, "status", depStatusMatcher.group(1));
    return Map.of("id", depId, "status", "unknown");
  }

  /**
   * Finds blocked issues from the issue index by checking unresolved dependencies.
   *
   * @param issueIndex the qualified name to issue entry index
   * @param bareNameIndex the bare name to qualified name list index
   * @return a list of blocked issue maps with issue_id, blocked_by, and reason fields
   */
  private List<Map<String, Object>> findBlockedIssues(Map<String, IssueIndexEntry> issueIndex,
    Map<String, List<String>> bareNameIndex)
  {
    List<Map<String, Object>> blockedIssues = new ArrayList<>();

    for (Map.Entry<String, IssueIndexEntry> entry : issueIndex.entrySet())
    {
      String qualifiedIssueName = entry.getKey();
      String content = entry.getValue().content();

      List<String> activeDeps = resolveActiveDependencies(
        content, issueIndex, bareNameIndex);
      if (activeDeps.isEmpty())
        continue;

      List<Map<String, String>> unresolvedDeps = new ArrayList<>();
      for (String depId : activeDeps)
      {
        Map<String, String> depStatus = getDependencyStatus(depId, issueIndex);
        if (!depStatus.get("status").equals("closed"))
          unresolvedDeps.add(depStatus);
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

        Map<String, Object> blockedIssue = new LinkedHashMap<>();
        blockedIssue.put("issue_id", qualifiedIssueName);
        blockedIssue.put("blocked_by", blockedBy);
        blockedIssue.put("reason", String.join(", ", reasonParts));
        blockedIssues.add(blockedIssue);
      }
    }

    return blockedIssues;
  }

  /**
   * Mutable state maintained during a cycle detection DFS traversal.
   */
  private static final class CycleDetectionContext
  {
    /**
     * Fully-processed nodes (all descendants visited, no cycles originating here).
     */
    final Set<String> visited = new HashSet<>();
    /**
     * Nodes on the current DFS path (for cycle detection).
     */
    final Set<String> onPath = new LinkedHashSet<>();
    /**
     * The current DFS path as an ordered list.
     */
    final List<String> path = new ArrayList<>();
    /**
     * Detected cycle path strings.
     */
    final List<String> cycles = new ArrayList<>();
    /**
     * Cycle canonical forms already reported (to avoid duplicates).
     */
    final Set<String> reportedCycles = new HashSet<>();
    /**
     * The maximum recursion depth allowed.
     */
    final int maxDepth;

    /**
     * Creates a new context.
     *
     * @param maxDepth the maximum DFS recursion depth allowed
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    CycleDetectionContext(int maxDepth)
    {
      requireThat(maxDepth, "maxDepth").isGreaterThanOrEqualTo(0);
      this.maxDepth = maxDepth;
    }
  }

  /**
   * Detects circular dependency chains in the issue index using depth-first search.
   * <p>
   * For each open or in-progress issue, traverses its dependency graph looking for cycles.
   * Each detected cycle is represented as a path string in the format
   * {@code A -> B -> C -> A}.
   * <p>
   * Only open or in-progress issues are included in cycle detection, since closed issues
   * cannot participate in a live blocking cycle.
   *
   * @param issueIndex the qualified name to issue entry index
   * @param bareNameIndex the bare name to qualified name list index
   * @return a list of cycle path strings, empty if no cycles are detected
   * @throws IOException if the dependency graph is too deep for cycle detection
   */
  private List<String> findCircularDependencies(Map<String, IssueIndexEntry> issueIndex,
    Map<String, List<String>> bareNameIndex) throws IOException
  {
    // Build a dependency graph restricted to open/in-progress issues
    Map<String, List<String>> dependencyGraph = new LinkedHashMap<>();
    for (Map.Entry<String, IssueIndexEntry> entry : issueIndex.entrySet())
    {
      String qualifiedName = entry.getKey();
      String content = entry.getValue().content();

      List<String> activeDeps = resolveActiveDependencies(
        content, issueIndex, bareNameIndex);
      if (activeDeps.isEmpty())
        continue;

      dependencyGraph.put(qualifiedName, activeDeps);
    }

    // Augment the graph with implicit edges from decomposed parents to their sub-issues.
    // When issue A depends on decomposed parent B, the explicit graph has A -> B.
    // Adding B -> sub-issues allows cycle detection to traverse: A -> B -> sub-issue -> A.
    // Build augmented edges separately, then merge into the graph after the loop to avoid
    // modifying the graph during iteration.
    Map<String, List<String>> augmentedEdges = new LinkedHashMap<>();
    for (Map.Entry<String, IssueIndexEntry> entry : issueIndex.entrySet())
    {
      String qualifiedName = entry.getKey();
      String content = entry.getValue().content();

      List<String> subIssueNames = getDecomposedSubIssueNames(content, issueIndex);
      if (subIssueNames.isEmpty())
        continue;

      augmentedEdges.put(qualifiedName, subIssueNames);
    }

    for (Map.Entry<String, List<String>> augEntry : augmentedEdges.entrySet())
    {
      String qualifiedName = augEntry.getKey();
      List<String> subIssueNames = augEntry.getValue();

      List<String> existing = dependencyGraph.get(qualifiedName);
      if (existing == null)
      {
        dependencyGraph.put(qualifiedName, new ArrayList<>(subIssueNames));
      }
      else
      {
        Set<String> merged = new LinkedHashSet<>(existing);
        merged.addAll(subIssueNames);
        dependencyGraph.put(qualifiedName, new ArrayList<>(merged));
      }
    }

    // DFS-based cycle detection: track visited nodes and current path
    CycleDetectionContext context = new CycleDetectionContext(maxCycleDetectionDepth);

    for (String startNode : dependencyGraph.keySet())
    {
      if (context.visited.contains(startNode))
        continue;

      context.onPath.clear();
      context.path.clear();
      detectCycles(startNode, dependencyGraph, context, 0);
    }

    return context.cycles;
  }

  /**
   * Returns the qualified names of sub-issues listed in the "## Decomposed Into" section of a
   * STATE.md file.
   * <p>
   * Names in the "Decomposed Into" section are fully-qualified (e.g., {@code 2.1-parser-lexer})
   * and are returned as-is since they already match the keys in the issue index.
   * <p>
   * Entries that do not match the qualified name pattern are skipped.
   *
   * @param content the content of the STATE.md file
   * @param issueIndex the qualified name to issue entry index, used to verify that returned names
   *         exist in the index
   * @return the list of qualified sub-issue names, or an empty list if the issue is not a
   *         decomposed parent or has no recognized sub-issue entries
   */
  private List<String> getDecomposedSubIssueNames(String content,
    Map<String, IssueIndexEntry> issueIndex)
  {
    Matcher decomposedMatcher = IssueDiscovery.DECOMPOSED_INTO_PATTERN.matcher(content);
    if (!decomposedMatcher.find())
      return List.of();

    int sectionStart = decomposedMatcher.end();

    // Find the next section header to delimit the "Decomposed Into" section
    int sectionEnd = content.length();
    Matcher nextSectionMatcher = IssueDiscovery.NEXT_SECTION_PATTERN.matcher(content);
    nextSectionMatcher.region(sectionStart, content.length());
    if (nextSectionMatcher.find())
      sectionEnd = nextSectionMatcher.start();

    String sectionContent = content.substring(sectionStart, sectionEnd);

    List<String> result = new ArrayList<>();
    Matcher itemMatcher = IssueDiscovery.SUBISSUE_ITEM_PATTERN.matcher(sectionContent);
    while (itemMatcher.find())
    {
      String name = itemMatcher.group(1);
      if (name.isEmpty())
        continue;
      if (!IssueDiscovery.QUALIFIED_NAME_PATTERN.matcher(name).matches())
        continue;
      if (issueIndex.containsKey(name))
        result.add(name);
    }

    return result;
  }

  /**
   * Recursive DFS helper for cycle detection.
   * <p>
   * Traverses the dependency graph from {@code current} tracking the current path. When a node is
   * found that is already on the current path, a cycle is recorded.
   * <p>
   * Recursion is bounded by {@link CycleDetectionContext#maxDepth} to prevent
   * {@code StackOverflowError} on deeply nested dependency chains.
   *
   * @param current the current node being visited
   * @param graph the dependency graph (node to list of dependencies)
   * @param context the mutable traversal state
   * @param depth the current recursion depth
   * @throws IOException if the recursion depth exceeds {@link CycleDetectionContext#maxDepth}
   */
  private void detectCycles(String current, Map<String, List<String>> graph,
    CycleDetectionContext context, int depth) throws IOException
  {
    if (depth > context.maxDepth)
    {
      throw new IOException(
        "Cycle detection exceeded maximum recursion depth of " + context.maxDepth +
          " while processing node: " + current +
          ". The dependency graph may contain an unusually deep chain. Consider simplifying dependencies.");
    }

    if (context.visited.contains(current))
      return;

    if (context.onPath.contains(current))
    {
      // Found a cycle: extract the cycle path from where current appears in path
      int cycleStart = context.path.indexOf(current);
      List<String> cyclePath = context.path.subList(cycleStart, context.path.size());
      cyclePath = new ArrayList<>(cyclePath);
      cyclePath.add(current);

      // Create a canonical form to avoid duplicate reports
      String cycleKey = String.join(" -> ", cyclePath);
      if (!context.reportedCycles.contains(cycleKey))
      {
        context.reportedCycles.add(cycleKey);
        context.cycles.add(cycleKey);
      }
      return;
    }

    context.onPath.add(current);
    context.path.add(current);

    List<String> deps = graph.getOrDefault(current, List.of());
    for (String dep : deps)
      detectCycles(dep, graph, context, depth + 1);

    context.onPath.remove(current);
    context.path.remove(context.path.size() - 1);
    context.visited.add(current);
  }

  /**
   * Finds locked issues by scanning the locks directory.
   *
   * @param projectDir the project root directory
   * @return a list of locked issue maps with issue_id and locked_by fields
   * @throws IOException if file operations fail
   */
  private List<Map<String, Object>> findLockedIssues(Path projectDir) throws IOException
  {
    List<Map<String, Object>> lockedIssues = new ArrayList<>();
    Path locksDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");

    if (!Files.isDirectory(locksDir))
      return lockedIssues;

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

          Map<String, Object> lockedIssue = new LinkedHashMap<>();
          lockedIssue.put("issue_id", lockIssueId);
          lockedIssue.put("locked_by", lockData.getOrDefault("session_id", "unknown").toString());
          lockedIssues.add(lockedIssue);
        }
        catch (IOException _)
        {
          // Skip malformed lock files
        }
      }
    }

    return lockedIssues;
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
   * Checks the base branch for suspicious commits that may have already implemented this issue.
   * <p>
   * Uses two complementary strategies:
   * <ol>
   *   <li>Message search: looks for commits whose message mentions the issue name.</li>
   *   <li>File overlap search: looks for commits that modified files listed in PLAN.md's
   *       "Files to Create" or "Files to Modify" sections.</li>
   * </ol>
   * Planning commits (e.g., {@code planning: add issue ...}) are filtered out as false positives.
   *
   * @param projectDir the project root directory
   * @param baseBranch the base branch to search
   * @param issueName the issue name to search for in commit messages
   * @param planPath the path to PLAN.md, used to extract planned files for overlap detection
   * @return a newline-separated list of suspicious commit lines, or empty string if none found
   */
  private String checkBaseBranchCommits(Path projectDir, String baseBranch, String issueName,
    Path planPath)
  {
    List<String> planningPrefixes = List.of(
      "planning:", "config: add issue", "planning: add issue", "config: mark", "config: decompose");

    Set<String> suspiciousHashes = new LinkedHashSet<>();
    List<String> suspiciousLines = new ArrayList<>();

    // Strategy 1: message-based search
    try
    {
      String logOutput = GitCommands.runGit(projectDir, "log", "--oneline",
        "--grep=" + issueName, baseBranch, "-5");

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
        {
          String hash;
          if (spaceIndex >= 0)
            hash = line.substring(0, spaceIndex);
          else
            hash = "";
          if (!hash.isEmpty() && suspiciousHashes.add(hash))
            suspiciousLines.add(line);
        }
      }
    }
    catch (IOException _)
    {
      // Ignore - strategy 2 may still find results
    }

    // Strategy 2: file-overlap search — catches implementations whose commit messages don't
    // reference the issue name (the failure mode that triggered M408)
    Set<String> plannedFiles = extractPlannedFiles(planPath);
    if (!plannedFiles.isEmpty())
    {
      try
      {
        // Get the last 20 commits on base to check for file overlap
        String logOutput = GitCommands.runGit(projectDir, "log", "--oneline", baseBranch, "-20");
        for (String line : logOutput.split("\n"))
        {
          if (line.isBlank())
            continue;
          int spaceIndex = line.indexOf(' ');
          if (spaceIndex < 0)
            continue;
          String hash = line.substring(0, spaceIndex);
          String msg = line.substring(spaceIndex + 1);

          // Skip already-found commits and planning commits
          if (suspiciousHashes.contains(hash))
            continue;
          boolean isPlanning = false;
          for (String prefix : planningPrefixes)
          {
            if (msg.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
            {
              isPlanning = true;
              break;
            }
          }
          if (isPlanning)
            continue;

          // Check if this commit touched any of the planned files
          String changedFiles = GitCommands.runGit(projectDir, "diff-tree", "--no-commit-id",
            "-r", "--name-only", hash);
          for (String changedFile : changedFiles.split("\n"))
          {
            if (changedFile.isBlank())
              continue;
            for (String plannedFile : plannedFiles)
            {
              // Match by suffix to handle glob patterns like `plugin/agents/stakeholder-*.md`
              if (changedFile.endsWith(plannedFile) || matchesGlobSuffix(changedFile, plannedFile))
              {
                suspiciousHashes.add(hash);
                suspiciousLines.add(line + " [touches planned file: " + changedFile + "]");
                break;
              }
            }
            if (suspiciousHashes.contains(hash))
              break;
          }
        }
      }
      catch (IOException _)
      {
        // Ignore - return whatever was found by strategy 1
      }
    }

    return String.join("\n", suspiciousLines);
  }

  /**
   * Extracts the set of file paths listed under "Files to Create" and "Files to Modify" in PLAN.md.
   * <p>
   * Glob patterns (e.g., {@code plugin/agents/stakeholder-*.md}) are included as-is and matched
   * using {@link #matchesGlobSuffix(String, String)}.
   *
   * @param planPath the path to PLAN.md
   * @return the set of planned file paths, or an empty set if PLAN.md is absent or unreadable
   */
  private Set<String> extractPlannedFiles(Path planPath)
  {
    if (!Files.isRegularFile(planPath))
      return Collections.emptySet();

    String content;
    try
    {
      content = Files.readString(planPath);
    }
    catch (IOException _)
    {
      return Collections.emptySet();
    }

    Set<String> files = new HashSet<>();
    // Match backtick-quoted file paths in list items under Files to Create / Files to Modify
    Pattern filePattern = Pattern.compile("`([^`]+)`");

    for (Pattern sectionPattern : List.of(FILES_TO_CREATE_PATTERN, FILES_TO_MODIFY_PATTERN))
    {
      Matcher sectionMatcher = sectionPattern.matcher(content);
      if (!sectionMatcher.find())
        continue;
      String sectionContent = sectionMatcher.group(1);
      for (String line : sectionContent.split("\n"))
      {
        if (!line.strip().startsWith("-"))
          continue;
        Matcher fileMatcher = filePattern.matcher(line);
        if (fileMatcher.find())
        {
          String filePath = fileMatcher.group(1);
          // Normalize: strip leading ./ or /
          if (filePath.startsWith("./"))
            filePath = filePath.substring(2);
          else if (filePath.startsWith("/"))
            filePath = filePath.substring(1);
          files.add(filePath);
        }
      }
    }
    return files;
  }

  /**
   * Returns true if {@code filePath} matches {@code pattern} treating {@code *} as a wildcard
   * that matches any sequence of non-separator characters.
   * <p>
   * Only the final path segment of {@code pattern} is checked for glob wildcards. Patterns without
   * {@code *} are matched by exact suffix.
   *
   * @param filePath the actual file path from git
   * @param pattern the planned file path, optionally containing a {@code *} wildcard
   * @return true if the path matches the pattern
   */
  private boolean matchesGlobSuffix(String filePath, String pattern)
  {
    if (!pattern.contains("*"))
      return filePath.endsWith(pattern);

    // Convert glob pattern to regex: escape dots, replace * with [^/]*
    String regexPattern = pattern.
      replace(".", "\\.").
      replace("*", "[^/]*");
    return filePath.matches(".*" + regexPattern);
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
   * Reads the pre-conditions from a PLAN.md file.
   * <p>
   * Extracts checkbox items from the "## Pre-conditions" section. Returns a list of pre-condition
   * text strings (without the checkbox prefix).
   *
   * @param planPath the path to the PLAN.md file
   * @return list of pre-condition text strings, empty if section not found or file does not exist
   * @throws IOException if the file exists but cannot be read
   */
  private List<String> readPreconditionsFromPlan(Path planPath) throws IOException
  {
    if (!Files.isRegularFile(planPath))
      return Collections.emptyList();

    List<String> lines = Files.readAllLines(planPath);

    List<String> preconditions = new ArrayList<>();
    boolean inSection = false;
    for (String line : lines)
    {
      if (line.strip().startsWith("## Pre-conditions"))
      {
        inSection = true;
        continue;
      }
      if (inSection && line.strip().startsWith("##"))
        break;
      if (inSection)
      {
        String stripped = line.strip();
        if (stripped.startsWith("- [ ]"))
          preconditions.add(stripped.substring(6).strip());
        else if (stripped.startsWith("- [x]"))
          preconditions.add(stripped.substring(6).strip());
      }
    }

    return preconditions;
  }

  /**
   * Updates the lock file with the worktree path.
   *
   * @param issueId the issue ID to update the lock for
   * @param sessionId the session ID that owns the lock
   * @param worktreePath the worktree path to store in the lock
   * @throws IOException if the lock file cannot be updated
   */
  private void updateLockWorktree(String issueId, String sessionId, String worktreePath)
    throws IOException
  {
    assert that(issueId, "issueId").isNotBlank().elseThrow();
    assert that(sessionId, "sessionId").isNotBlank().elseThrow();
    assert that(worktreePath, "worktreePath").isNotBlank().elseThrow();
    IssueLock issueLock = new IssueLock(scope);
    issueLock.update(issueId, sessionId, worktreePath);
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
   * Main method for command-line execution.
   * <p>
   * Accepts named arguments:
   * <ul>
   *   <li>{@code --session-id ID} — the Claude session ID (defaults to {@code CLAUDE_SESSION_ID})</li>
   *   <li>{@code --exclude-pattern GLOB} — glob to exclude issues by name</li>
   *   <li>{@code --issue-id ID} — specific issue ID to select</li>
   *   <li>{@code --trust-level LEVEL} — low, medium, or high (defaults to {@code CAT_TRUST_LEVEL} or medium)</li>
   *   <li>{@code --arguments RAW} — raw user arguments to parse into issue-id or exclude-pattern</li>
   * </ul>
   *
   * @param args command-line arguments
   * @throws IOException if execution fails
   */
  public static void main(String[] args) throws IOException
  {
    String sessionIdOverride = "";
    String excludePattern = "";
    String issueId = "";
    String trustLevelStr = "medium";
    String rawArguments = "";

    // Loop bound is args.length - 1 so that args[i+1] (the flag value) is always available.
    // A lone flag key at the last position is intentionally skipped to avoid ArrayIndexOutOfBoundsException.
    for (int i = 0; i < args.length - 1; ++i)
    {
      switch (args[i])
      {
        case "--session-id" ->
        {
          ++i;
          sessionIdOverride = args[i];
        }
        case "--exclude-pattern" ->
        {
          ++i;
          excludePattern = args[i];
        }
        case "--issue-id" ->
        {
          ++i;
          issueId = args[i];
        }
        case "--trust-level" ->
        {
          ++i;
          trustLevelStr = args[i];
        }
        case "--arguments" ->
        {
          ++i;
          rawArguments = args[i];
        }
        default ->
        {
          // ignore unknown flags
        }
      }
    }

    // Parse raw arguments into issue-id or exclude-pattern
    if (!rawArguments.isBlank() && issueId.isEmpty() && excludePattern.isEmpty())
    {
      String raw = rawArguments.strip();
      if (raw.matches("^[0-9]+\\.[0-9]+(-[a-zA-Z0-9_-]+)?$") ||
        raw.matches("^[a-zA-Z][a-zA-Z0-9_-]*$"))
      {
        issueId = raw;
      }
      else if (raw.startsWith("skip "))
      {
        String word = raw.substring(5).strip();
        excludePattern = "*" + word + "*";
      }
    }

    TrustLevel trustLevel;
    try
    {
      trustLevel = TrustLevel.fromString(trustLevelStr);
    }
    catch (IllegalArgumentException e)
    {
      System.out.println("""
        {
          "status": "ERROR",
          "message": "%s"
        }""".formatted(e.getMessage().replace("\"", "\\\"")));
      System.exit(1);
      return;
    }

    try (MainJvmScope scope = new MainJvmScope())
    {
      // Use scope-provided session ID if not overridden via --session-id
      String sessionId;
      if (!sessionIdOverride.isEmpty())
        sessionId = sessionIdOverride;
      else
        sessionId = scope.getClaudeSessionId();

      PrepareInput input = new PrepareInput(sessionId, excludePattern, issueId, trustLevel);
      WorkPrepare wp = new WorkPrepare(scope);
      String result = wp.execute(input);
      System.out.println(result);
    }
    catch (RuntimeException | AssertionError e)
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "Unexpected error: %s"
        }""".formatted(e.getMessage().replace("\"", "\\\"")));
      System.exit(1);
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
   * Diagnostic information about the state of issues when no issues are available.
   *
   * @param blockedIssues issues that are blocked by unsatisfied dependencies
   * @param lockedIssues issues that are locked by another session
   * @param closedCount the number of closed issues
   * @param totalCount the total number of issues
   * @param circularDependencies detected dependency cycles as path strings (e.g., {@code A -> B -> A})
   */
  private record DiagnosticInfo(
    List<Map<String, Object>> blockedIssues,
    List<Map<String, Object>> lockedIssues,
    int closedCount,
    int totalCount,
    List<String> circularDependencies)
  {
    /**
     * Creates new diagnostic info.
     *
     * @param blockedIssues issues that are blocked by unsatisfied dependencies
     * @param lockedIssues issues that are locked by another session
     * @param closedCount the number of closed issues
     * @param totalCount the total number of issues
     * @param circularDependencies detected dependency cycles as path strings
     * @throws NullPointerException if {@code blockedIssues}, {@code lockedIssues}, or
     *   {@code circularDependencies} are null
     * @throws IllegalArgumentException if {@code closedCount} or {@code totalCount} are negative
     */
    DiagnosticInfo
    {
      assert that(blockedIssues, "blockedIssues").isNotNull().elseThrow();
      assert that(lockedIssues, "lockedIssues").isNotNull().elseThrow();
      assert that(closedCount, "closedCount").isGreaterThanOrEqualTo(0).elseThrow();
      assert that(totalCount, "totalCount").isGreaterThanOrEqualTo(0).elseThrow();
      assert that(circularDependencies, "circularDependencies").isNotNull().elseThrow();
    }
  }
}
