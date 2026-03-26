/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.DiscoveryResult.ExistingWorktree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
   * Matches the "Files to Create" section in plan.md.
   */
  private static final Pattern FILES_TO_CREATE_PATTERN =
    Pattern.compile("## Files to Create\\s+(.*?)(?=\\n## [^#]|\\Z)", Pattern.DOTALL);
  /**
   * Matches the "Files to Modify" section in plan.md.
   */
  private static final Pattern FILES_TO_MODIFY_PATTERN =
    Pattern.compile("## Files to Modify\\s+(.*?)(?=\\n## [^#]|\\Z)", Pattern.DOTALL);
  /**
   * Matches the "Execution Waves" section in plan.md.
   */
  private static final Pattern EXECUTION_WAVES_PATTERN =
    Pattern.compile("## Execution Waves\\s+(.*?)(?=\\n## [^#]|\\Z)", Pattern.DOTALL);
  /**
   * Matches a CAT agent ID token at the start of a string. The token is either a plain UUID or a
   * subagent ID of the form {@code uuid/subagents/name}. Skills expand {@code $ARGUMENTS} to include
   * this token as the first word, so it must be stripped before interpreting the remaining arguments.
   */
  private static final Pattern CAT_AGENT_ID_TOKEN =
    Pattern.compile(
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/subagents/[0-9a-zA-Z_-]+)?");
  /**
   * Tokenizes a glob pattern into {@code **}, {@code *}, or literal text segments.
   */
  private static final Pattern GLOB_TOKEN_PATTERN = Pattern.compile("\\*\\*|\\*|[^*]+");
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

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ClaudeTool scope;
  private final int diagnosticScanSafetyThreshold;
  private final int maxCycleDetectionDepth;
  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

  /**
   * Creates a new WorkPrepare instance with default thresholds.
   *
   * @param scope the JVM scope providing project directory and shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public WorkPrepare(ClaudeTool scope)
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
  public WorkPrepare(ClaudeTool scope, int diagnosticScanSafetyThreshold, int maxCycleDetectionDepth)
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
   * @param resume whether the user explicitly requested resume/continue semantics
   */
  public record PrepareInput(String sessionId, String excludePattern, String issueId,
    TrustLevel trustLevel, boolean resume)
  {
    /**
     * Creates new prepare input.
     *
     * @param sessionId the Claude session ID (UUID format)
     * @param excludePattern glob pattern to exclude issues by name, or empty string for none
     * @param issueId specific issue ID to select, or empty string for priority-based selection
     * @param trustLevel the trust level for execution
     * @param resume whether the user explicitly requested resume/continue semantics
     * @throws IllegalArgumentException if {@code sessionId} is blank
     * @throws NullPointerException if {@code excludePattern}, {@code issueId}, or
     *   {@code trustLevel} are null
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
   *   <li>{@code CORRUPT} - issue directory has index.json but no plan.md</li>
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
    warnings.clear();

    Path projectPath = scope.getProjectPath();
    JsonMapper mapper = scope.getJsonMapper();

    // Step 1: Verify CAT structure
    if (!verifyCatStructure())
    {
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "No .cat/ directory or config.json found"));
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

    // For the ALL scope, build the issue index once so it can be reused in gatherDiagnosticInfo
    // if no issue is found, avoiding a double scan of all index.json files.
    Map<String, IssueIndexEntry> preBuiltIssueIndex;
    Map<String, List<String>> preBuiltBareNameIndex;
    if (discoveryScope == IssueDiscovery.Scope.ALL)
    {
      preBuiltIssueIndex = new LinkedHashMap<>();
      preBuiltBareNameIndex = new LinkedHashMap<>();
      Path issuesDir = scope.getCatDir().resolve("issues");
      buildIssueIndex(issuesDir, preBuiltIssueIndex, preBuiltBareNameIndex);
    }
    else
    {
      preBuiltIssueIndex = null;
      preBuiltBareNameIndex = null;
    }

    IssueDiscovery.DiscoveryResult discoveryResult = discovery.findNextIssue(searchOptions);
    warnings.addAll(discovery.getWarnings());

    // Handle non-found statuses
    String nonFoundResult = handleNonFoundResult(input, discoveryResult, mapper,
      preBuiltIssueIndex, preBuiltBareNameIndex);
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

    // Check for corrupt directory (index.json present, plan.md absent)
    if (found.isCorrupt())
    {
      Map<String, Object> corruptResult = new LinkedHashMap<>();
      corruptResult.put("status", "CORRUPT");
      corruptResult.put("issue_id", issueId);
      corruptResult.put("issue_path", issuePath.toString());
      corruptResult.put("message", "Issue directory is corrupt: index.json exists but plan.md is " +
        "missing at " + issuePath);
      return mapper.writeValueAsString(corruptResult);
    }

    // Get target branch (current branch in project dir)
    String targetBranch = GitCommands.getCurrentBranch(projectPath.toString());

    // Decomposed parents with all sub-issues closed require only closure work — skip OVERSIZED check
    if (found.isDecomposedComplete())
    {
      String issueBranch = buildIssueBranch(major, minor, found.patch(), issueName);
      return executeWhileLocked(input, projectPath, mapper, issueId, major, minor, issueName,
        issuePath, targetBranch, issuePath.resolve("plan.md"), 5000, issueBranch);
    }

    // Step 4: Estimate tokens
    Path planPath = issuePath.resolve("plan.md");
    int estimatedTokens = estimateTokens(planPath);

    // Check if oversized
    if (estimatedTokens > TOKEN_LIMIT)
    {
      Map<String, Object> oversizedResult = new LinkedHashMap<>();
      oversizedResult.put("status", "OVERSIZED");
      oversizedResult.put("message", "Issue estimated at " + estimatedTokens + " tokens (limit: " + TOKEN_LIMIT + ")");
      oversizedResult.put("suggestion", "Use /cat:decompose-issue to break into smaller issues");
      oversizedResult.put("issue_id", issueId);
      oversizedResult.put("estimated_tokens", estimatedTokens);
      return mapper.writeValueAsString(oversizedResult);
    }

    // Steps 5-10: Create worktree and build READY result
    String issueBranch = buildIssueBranch(major, minor, found.patch(), issueName);
    return executeWhileLocked(input, projectPath, mapper, issueId, major, minor, issueName,
      issuePath, targetBranch, planPath, estimatedTokens, issueBranch);
  }

  /**
   * Handles discovery results that are not {@code Found}, returning a JSON response for each.
   * <p>
   * Returns an empty string if the result is {@code Found}, indicating the caller should
   * continue with worktree creation.
   * <p>
   * For {@code ExistingWorktree} results, this method calls {@code issueLock.check()} to inspect
   * the lock state inline: if the current session owns the lock, it returns a {@code READY} response
   * using the existing worktree path (resume semantics); if another session owns the lock, it returns
   * a {@code LOCKED} response; if the issue is unlocked, it returns an {@code ERROR} response.
   *
   * @param input the preparation input parameters
   * @param discoveryResult the discovery result to handle
   * @param mapper the JSON mapper for serialization
   * @param preBuiltIssueIndex a pre-built issue index to reuse (may be null to trigger a fresh scan)
   * @param preBuiltBareNameIndex a pre-built bare name index to reuse (may be null)
   * @return a JSON response string if the result is not Found, or an empty string if Found
   * @throws IOException if file operations or JSON serialization fail
   */
  private String handleNonFoundResult(PrepareInput input,
    IssueDiscovery.DiscoveryResult discoveryResult,
    JsonMapper mapper,
    Map<String, IssueIndexEntry> preBuiltIssueIndex,
    Map<String, List<String>> preBuiltBareNameIndex) throws IOException
  {
    if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.NotFound)
    {
      DiagnosticInfo diagnostics = gatherDiagnosticInfo(preBuiltIssueIndex,
        preBuiltBareNameIndex);

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

      if (!warnings.isEmpty())
        result.put("warnings", warnings);

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

    if (discoveryResult instanceof ExistingWorktree existingWorktree)
    {
      // The issue has an existing worktree. Check the lock state to determine the correct response:
      // - Locked by current session: resume with READY response
      // - resume=true: force-release any stale lock, acquire for current session, return READY
      // - Locked by another session (no resume): return ERROR with locked_by/lock_age_seconds so the
      //   skill can show a confirmation dialog before the user decides whether to resume
      // - Unlocked (no resume): return ERROR about the existing worktree
      IssueLock issueLock = new IssueLock(scope);
      IssueLock.LockResult lockCheck = issueLock.check(existingWorktree.issueId());
      Path projectPath = scope.getProjectPath();
      if (lockCheck instanceof IssueLock.LockResult.CheckLocked locked)
      {
        if (locked.sessionId().equals(input.sessionId()))
          return resumeWithExistingWorktree(existingWorktree, projectPath, mapper);
        if (input.resume())
          return forceResumeWithExistingWorktree(existingWorktree, issueLock, input, projectPath,
            mapper);
        Map<String, Object> errorResult = new LinkedHashMap<>();
        errorResult.put("status", "ERROR");
        errorResult.put("message", "Issue " + existingWorktree.issueId() +
          " has an existing worktree locked by another session");
        errorResult.put("issue_id", existingWorktree.issueId());
        errorResult.put("locked_by", locked.sessionId());
        errorResult.put("lock_age_seconds", locked.ageSeconds());
        errorResult.put("worktree_path", existingWorktree.worktreePath());
        return mapper.writeValueAsString(errorResult);
      }
      // Unlocked worktree: resume if requested, otherwise return ERROR
      if (input.resume())
        return forceResumeWithExistingWorktree(existingWorktree, issueLock, input, projectPath,
          mapper);
      Map<String, Object> errorResult = new LinkedHashMap<>();
      errorResult.put("status", "ERROR");
      errorResult.put("message", "Issue " + existingWorktree.issueId() +
        " has an existing worktree at: " + existingWorktree.worktreePath());
      errorResult.put("issue_id", existingWorktree.issueId());
      return mapper.writeValueAsString(errorResult);
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
   * Acquires the issue lock and executes the worktree creation and metadata collection steps
   * after a Found result.
   * <p>
   * Lock acquisition is the first step inside this method, using the pre-computed worktree path
   * derived from {@code issueBranch}. If the lock is already held by another session, a LOCKED
   * JSON response is returned immediately before any worktree is created.
   *
   * @param input the preparation input parameters
   * @param projectPath the project root directory
   * @param mapper the JSON mapper for serialization
   * @param issueId the qualified issue ID
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the bare issue name
   * @param issuePath the path to the issue directory
   * @param targetBranch the target branch name
   * @param planPath the path to plan.md
   * @param estimatedTokens the estimated token count
   * @param issueBranch the issue branch name
   * @return JSON string with READY, LOCKED, or ERROR result
   * @throws IOException if file operations fail
   */
  private String executeWhileLocked(PrepareInput input, Path projectPath, JsonMapper mapper,
    String issueId, String major, String minor,
    String issueName, Path issuePath, String targetBranch, Path planPath, int estimatedTokens,
    String issueBranch) throws IOException
  {
    // Step 3: Acquire lock with the pre-computed worktree path so the lock file is correct
    // the moment it is written, eliminating the two-step acquire-then-update pattern.
    Path worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve(issueBranch);
    IssueLock issueLock = new IssueLock(scope);
    IssueLock.LockResult lockResult = issueLock.acquire(issueId, input.sessionId(),
      worktreePath.toString());
    if (lockResult instanceof IssueLock.LockResult.Locked locked)
    {
      return mapper.writeValueAsString(Map.of(
        "status", "LOCKED",
        "message", "Issue locked by another session: " + locked.owner(),
        "issue_id", issueId,
        "locked_by", locked.owner()));
    }

    // Step 5: Create worktree
    try
    {
      createWorktree(projectPath, issueBranch, worktreePath);
    }
    catch (IOException e)
    {
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to create worktree: " + e.getMessage()));
    }

    // Step 5.5: Create and commit index.json in worktree if needed
    // Ensures index.json is always tracked in the issue branch, preventing the case where
    // index.json exists untracked in the main workspace but is absent from the worktree.
    try
    {
      if (!indexFileExistsInWorktree(worktreePath, issuePath, projectPath))
      {
        createIndexFileAndCommit(worktreePath, issuePath, projectPath, targetBranch);
      }
    }
    catch (IOException e)
    {
      cleanupWorktree(projectPath, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to create and commit index.json: " + e.getMessage()));
    }

    // Step 6: Verify worktree branch
    try
    {
      String actualBranch = GitCommands.getCurrentBranch(worktreePath.toString());
      if (!actualBranch.equals(issueBranch))
      {
        cleanupWorktree(projectPath, worktreePath);
        releaseLock(issueId, input.sessionId());
        return mapper.writeValueAsString(Map.of(
          "status", "ERROR",
          "message", "Worktree created on wrong branch (expected: " + issueBranch +
            ", actual: " + actualBranch + ")"));
      }
    }
    catch (IOException e)
    {
      cleanupWorktree(projectPath, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to verify worktree branch: " + e.getMessage()));
    }

    // Step 7: Check for existing work
    ExistingWorkChecker.CheckResult existingWork;
    try
    {
      existingWork = ExistingWorkChecker.check(worktreePath.toString(), targetBranch);
    }
    catch (IOException e)
    {
      cleanupWorktree(projectPath, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to check existing work: " + e.getMessage()));
    }

    // Step 8: Check target branch for suspicious commits
    String suspiciousCommits = checkTargetBranchCommits(projectPath, targetBranch, issueName, planPath);

    // Step 9: Update index.json in worktree
    // index.json now always exists in the worktree (created in Step 5.5 if needed)
    try
    {
      updateIndexJson(worktreePath, issuePath, projectPath, targetBranch);
    }
    catch (IOException e)
    {
      cleanupWorktree(projectPath, worktreePath);
      releaseLock(issueId, input.sessionId());
      return mapper.writeValueAsString(Map.of(
        "status", "ERROR",
        "message", "Failed to update index.json: " + e.getMessage()));
    }

    // Read goal from plan.md
    String goal = IssueGoalReader.readGoalFromPlan(planPath);

    // Step 10: Return READY JSON
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "READY");
    result.put("issue_id", issueId);
    result.put("major", major);
    result.put("minor", minor);
    result.put("issue_name", issueName);
    Path relativeIssuePath = projectPath.relativize(issuePath);
    result.put("issue_path", worktreePath.resolve(relativeIssuePath).toString());
    result.put("worktree_path", worktreePath.toString());
    result.put("issue_branch", issueBranch);
    result.put("target_branch", targetBranch);
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

    if (!warnings.isEmpty())
      result.put("warnings", warnings);

    return mapper.writeValueAsString(result);
  }

  /**
   * Builds a READY JSON response by reusing an already-existing worktree owned by the current session.
   * <p>
   * Called when {@code IssueDiscovery} returns an {@code ExistingWorktree} result and the lock is
   * confirmed to be owned by the current session. All metadata (tokens, goal,
   * existing work) is recomputed from the existing worktree path using the same helpers used during
   * initial worktree creation.
   *
   * @param existing the existing-worktree discovery result
   * @param projectPath the main project root directory
   * @param mapper the JSON mapper for serialization
   * @return JSON string with READY result or OVERSIZED result if token limit exceeded
   * @throws IOException if file operations or JSON serialization fail
   */
  private String resumeWithExistingWorktree(
    IssueDiscovery.DiscoveryResult.ExistingWorktree existing,
    Path projectPath,
    JsonMapper mapper) throws IOException
  {
    String issueBranch = buildIssueBranch(existing.major(), existing.minor(), existing.patch(),
      existing.issueName());
    String targetBranch = GitCommands.getCurrentBranch(projectPath.toString());

    // The issuePath from IssueDiscovery points to the main-workspace issue directory.
    // Recompute the equivalent path inside the existing worktree so plan.md can be read.
    Path mainIssuePath = Path.of(existing.issuePath());
    Path relativeIssuePath = projectPath.relativize(mainIssuePath);
    Path worktreePath = Path.of(existing.worktreePath());
    Path planPath = worktreePath.resolve(relativeIssuePath).resolve("plan.md");

    int estimatedTokens = estimateTokens(planPath);

    if (estimatedTokens > TOKEN_LIMIT)
    {
      Map<String, Object> oversizedResult = new LinkedHashMap<>();
      oversizedResult.put("status", "OVERSIZED");
      oversizedResult.put("message", "Issue estimated at " + estimatedTokens +
        " tokens (limit: " + TOKEN_LIMIT + ")");
      oversizedResult.put("suggestion", "Use /cat:decompose-issue to break into smaller issues");
      oversizedResult.put("issue_id", existing.issueId());
      oversizedResult.put("estimated_tokens", estimatedTokens);
      return mapper.writeValueAsString(oversizedResult);
    }

    ExistingWorkChecker.CheckResult existingWork =
      ExistingWorkChecker.check(existing.worktreePath(), targetBranch);
    String suspiciousCommits =
      checkTargetBranchCommits(projectPath, targetBranch, existing.issueName(), planPath);
    String goal = IssueGoalReader.readGoalFromPlan(planPath);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "READY");
    result.put("issue_id", existing.issueId());
    result.put("major", existing.major());
    result.put("minor", existing.minor());
    result.put("issue_name", existing.issueName());
    result.put("issue_path", worktreePath.resolve(relativeIssuePath).toString());
    result.put("worktree_path", existing.worktreePath());
    result.put("issue_branch", issueBranch);
    result.put("target_branch", targetBranch);
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

    if (!warnings.isEmpty())
      result.put("warnings", warnings);

    return mapper.writeValueAsString(result);
  }

  /**
   * Force-acquires the lock for the current session and resumes with an existing worktree.
   * <p>
   * Releases any existing lock unconditionally, then acquires a fresh lock for the current session.
   * Used when the user explicitly requests resume/continue semantics.
   *
   * @param existingWorktree the existing-worktree discovery result
   * @param issueLock the issue lock manager
   * @param input the preparation input parameters
   * @param projectPath the main project root directory
   * @param mapper the JSON mapper for serialization
   * @return JSON string with READY result, LOCKED result if a race condition occurs, or ERROR
   * @throws IOException if file operations or JSON serialization fail
   */
  private String forceResumeWithExistingWorktree(
    ExistingWorktree existingWorktree,
    IssueLock issueLock,
    PrepareInput input,
    Path projectPath,
    JsonMapper mapper) throws IOException
  {
    IssueLock.LockResult forceResult = issueLock.forceRelease(existingWorktree.issueId());
    if (forceResult instanceof IssueLock.LockResult.Error forceError)
    {
      Map<String, Object> errorResult = new LinkedHashMap<>();
      errorResult.put("status", "ERROR");
      errorResult.put("message",
        "Failed to release existing lock: " + forceError.message());
      errorResult.put("issue_id", existingWorktree.issueId());
      return mapper.writeValueAsString(errorResult);
    }
    IssueLock.LockResult acquireResult = issueLock.acquire(existingWorktree.issueId(),
      input.sessionId(), existingWorktree.worktreePath());
    if (acquireResult instanceof IssueLock.LockResult.Locked locked)
    {
      Map<String, Object> lockedResult = new LinkedHashMap<>();
      lockedResult.put("status", "LOCKED");
      lockedResult.put("message", "Issue " + existingWorktree.issueId() +
        " was locked by another session during resume");
      lockedResult.put("issue_id", existingWorktree.issueId());
      lockedResult.put("locked_by", locked.owner());
      return mapper.writeValueAsString(lockedResult);
    }
    return resumeWithExistingWorktree(existingWorktree, projectPath, mapper);
  }

  /**
   * Verifies that the project has a valid CAT structure.
   *
   * @return true if the structure is valid
   */
  private boolean verifyCatStructure()
  {
    Path catDir = scope.getCatDir();
    Path configFile = catDir.resolve("config.json");
    return Files.isDirectory(catDir) && Files.isRegularFile(configFile);
  }

  /**
   * Gathers diagnostic information when no issues are available.
   * <p>
   * Scans issue directories to find blocked tasks, locked tasks, closed/total counts,
   * and circular dependencies.
   * <p>
   * When {@code preBuiltIssueIndex} and {@code preBuiltBareNameIndex} are non-null (pre-built by
   * the caller), they are used directly without rebuilding the index, avoiding a redundant scan.
   *
   * @param preBuiltIssueIndex a pre-built issue index to reuse, or null to trigger a fresh scan
   * @param preBuiltBareNameIndex a pre-built bare name index to reuse, or null to trigger a fresh scan
   * @return the diagnostic info
   * @throws IOException if file operations fail
   */
  private DiagnosticInfo gatherDiagnosticInfo(
    Map<String, IssueIndexEntry> preBuiltIssueIndex,
    Map<String, List<String>> preBuiltBareNameIndex) throws IOException
  {
    Map<String, IssueIndexEntry> issueIndex;
    Map<String, List<String>> bareNameIndex;
    if (preBuiltIssueIndex != null && preBuiltBareNameIndex != null)
    {
      issueIndex = preBuiltIssueIndex;
      bareNameIndex = preBuiltBareNameIndex;
    }
    else
    {
      Path issuesDir = scope.getCatDir().resolve("issues");
      issueIndex = new LinkedHashMap<>();
      bareNameIndex = new LinkedHashMap<>();
      buildIssueIndex(issuesDir, issueIndex, bareNameIndex);
    }

    List<Map<String, Object>> blockedIssues = findBlockedIssues(issueIndex, bareNameIndex);
    List<String> circularDependencies = findCircularDependencies(issueIndex, bareNameIndex);
    int closedCount = 0;
    int totalCount = 0;

    JsonMapper diagnosticMapper = scope.getJsonMapper();
    for (IssueIndexEntry entry : issueIndex.values())
    {
      ++totalCount;
      try
      {
        JsonNode root = diagnosticMapper.readTree(entry.content());
        JsonNode statusNode = root.get("status");
        if (statusNode != null && statusNode.isString() && statusNode.asString().equals("closed"))
          ++closedCount;
      }
      catch (JacksonException _)
      {
        // Malformed index.json — not counted as closed
      }
    }

    List<Map<String, Object>> lockedIssues = findLockedIssues();

    return new DiagnosticInfo(blockedIssues, lockedIssues, closedCount, totalCount, circularDependencies);
  }

  /**
   * A (path, qualifiedName, content) tuple collected during the parallel read phase of
   * {@link #buildIssueIndex}.
   *
   * @param indexFile the path to the index.json file
   * @param qualifiedName the qualified issue name (e.g., {@code 2.1-fix-bug})
   * @param issueName the bare issue name (e.g., {@code fix-bug})
   * @param content the content of the index.json file
   */
  private record ReadResult(Path indexFile, String qualifiedName, String issueName, String content)
  {
  }

  /**
   * Builds the issue index and bare name index from index.json files.
   * <p>
   * Populates the provided maps with qualified issue names mapped to their index.json content,
   * and bare issue names mapped to lists of qualified names for ambiguous lookups.
   * <p>
   * {@code Files.readString()} calls are parallelized using virtual threads. Results are collected
   * into a thread-safe queue and then merged sequentially into the output maps to preserve
   * insertion order and avoid concurrent modification.
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

    List<Path> indexFiles;
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

      indexFiles = allEntries.stream().
        filter(p -> p.getFileName().toString().equals("index.json")).
        toList();
    }

    // Parallelize Files.readString() calls using virtual threads.
    // The results array mirrors indexFiles order so that insertion into the output maps
    // preserves the original Files.walk() ordering (same as the sequential implementation).
    @SuppressWarnings("unchecked")
    ReadResult[] readResults = new ReadResult[indexFiles.size()];
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
    {
      List<Future<?>> futures = new ArrayList<>(indexFiles.size());
      for (int i = 0; i < indexFiles.size(); ++i)
      {
        int index = i;
        Path indexFile = indexFiles.get(index);
        futures.add(executor.submit(() ->
        {
          String issueName = indexFile.getParent().getFileName().toString();
          String versionDirName = indexFile.getParent().getParent().getFileName().toString();
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
          try
          {
            String content = Files.readString(indexFile);
            readResults[index] = new ReadResult(indexFile, qualifiedName, issueName, content);
          }
          catch (IOException e)
          {
            warnings.add("Failed to read " + indexFile + ": " + e.getMessage());
            // Skip unreadable index.json files — slot remains null, skipped below
          }
          return null;
        }));
      }
      for (Future<?> future : futures)
      {
        try
        {
          future.get();
        }
        catch (InterruptedException _)
        {
          Thread.currentThread().interrupt();
          break;
        }
        catch (ExecutionException e)
        {
          // Individual read failures are already swallowed inside the task; re-throw only
          // unexpected execution failures that surface as IOException in the cause.
          if (e.getCause() instanceof IOException ioe)
            throw ioe;
        }
      }
    }

    // Populate the output maps in Files.walk() order (same as sequential implementation).
    for (ReadResult r : readResults)
    {
      if (r == null)
        continue;
      issueIndex.put(r.qualifiedName(), new IssueIndexEntry(r.indexFile(), r.content()));
      bareNameIndex.computeIfAbsent(r.issueName(), k -> new ArrayList<>()).add(r.qualifiedName());
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
   * @param content the content of the issue's index.json file
   * @param indexPath the path to the index.json file (used in error messages)
   * @param issueIndex the qualified name to issue entry index
   * @param bareNameIndex the bare name to qualified name list index
   * @return the list of resolved dependency IDs, or an empty list if the issue is not active
   *         or has no dependencies
   * @throws IOException if the content is not valid JSON
   */
  private List<String> resolveActiveDependencies(String content, Path indexPath,
    Map<String, IssueIndexEntry> issueIndex, Map<String, List<String>> bareNameIndex)
    throws IOException
  {
    JsonNode root;
    try
    {
      root = scope.getJsonMapper().readTree(content);
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + indexPath + ": " + e.getMessage(), e);
    }

    JsonNode statusNode = root.get("status");
    if (statusNode == null || !statusNode.isString())
      return List.of();
    String status = statusNode.asString();
    if (!status.equals("open") && !status.equals("in-progress"))
      return List.of();

    JsonNode depsNode = root.get("dependencies");
    if (depsNode == null || !depsNode.isArray())
      return List.of();

    List<String> resolvedDeps = new ArrayList<>();
    for (JsonNode item : depsNode)
    {
      if (!item.isString())
        continue;
      String depId = item.asString().strip();
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
   * @throws IOException if the index.json content is not valid JSON
   */
  private Map<String, String> getDependencyStatus(String depId,
    Map<String, IssueIndexEntry> issueIndex) throws IOException
  {
    IssueIndexEntry depData = issueIndex.get(depId);
    if (depData == null)
      return Map.of("id", depId, "status", "not_found");

    try
    {
      JsonNode root = scope.getJsonMapper().readTree(depData.content());
      JsonNode statusNode = root.get("status");
      if (statusNode != null && statusNode.isString())
        return Map.of("id", depId, "status", statusNode.asString());
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + depData.indexPath() + ": " + e.getMessage(), e);
    }
    return Map.of("id", depId, "status", "unknown");
  }

  /**
   * Finds blocked issues from the issue index by checking unresolved dependencies.
   *
   * @param issueIndex the qualified name to issue entry index
   * @param bareNameIndex the bare name to qualified name list index
   * @return a list of blocked issue maps with issueId, blockedBy, and reason fields
   * @throws IOException if an index.json file contains invalid JSON
   */
  private List<Map<String, Object>> findBlockedIssues(Map<String, IssueIndexEntry> issueIndex,
    Map<String, List<String>> bareNameIndex) throws IOException
  {
    List<Map<String, Object>> blockedIssues = new ArrayList<>();

    for (Map.Entry<String, IssueIndexEntry> entry : issueIndex.entrySet())
    {
      String qualifiedIssueName = entry.getKey();
      IssueIndexEntry issueEntry = entry.getValue();
      String content = issueEntry.content();

      List<String> activeDeps = resolveActiveDependencies(
        content, issueEntry.indexPath(), issueIndex, bareNameIndex);
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
      IssueIndexEntry issueEntry = entry.getValue();
      String content = issueEntry.content();

      List<String> activeDeps = resolveActiveDependencies(
        content, issueEntry.indexPath(), issueIndex, bareNameIndex);
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
      IssueIndexEntry issueEntry = entry.getValue();
      String content = issueEntry.content();

      List<String> subIssueNames = getDecomposedSubIssueNames(content, issueEntry.indexPath(), issueIndex);
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
   * Returns the qualified names of sub-issues listed in the {@code decomposedInto} array of an
   * index.json file.
   * <p>
   * Names are fully-qualified (e.g., {@code 2.1-parser-lexer}) and are returned as-is since they
   * already match the keys in the issue index.
   * <p>
   * Entries that do not match the qualified name pattern are skipped.
   *
   * @param content the content of the index.json file
   * @param indexPath the path to the index.json file (used in error messages)
   * @param issueIndex the qualified name to issue entry index, used to verify that returned names
   *         exist in the index
   * @return the list of qualified sub-issue names, or an empty list if the issue is not a
   *         decomposed parent or has no recognized sub-issue entries
   * @throws IOException if the content is not valid JSON
   */
  private List<String> getDecomposedSubIssueNames(String content, Path indexPath,
    Map<String, IssueIndexEntry> issueIndex) throws IOException
  {
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root;
    try
    {
      root = mapper.readTree(content);
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + indexPath + ": " + e.getMessage(), e);
    }

    JsonNode decomposedInto = root.get("decomposedInto");
    if (decomposedInto == null || !decomposedInto.isArray())
      return List.of();

    List<String> result = new ArrayList<>();
    for (JsonNode item : decomposedInto)
    {
      if (!item.isString())
        continue;
      String name = item.asString();
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
   * @return a list of locked issue maps with issueId and lockedBy fields
   * @throws IOException if file operations fail
   */
  private List<Map<String, Object>> findLockedIssues() throws IOException
  {
    List<Map<String, Object>> lockedIssues = new ArrayList<>();
    Path locksDir = scope.getCatWorkPath().resolve("locks");

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
   * Estimates the token count heuristically from plan.md.
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
   * @param planPath the path to plan.md
   * @return the estimated token count
   * @throws IOException if plan.md exists but cannot be read
   */
  private int estimateTokens(Path planPath) throws IOException
  {
    if (!Files.isRegularFile(planPath))
      return 10_000;

    String content = Files.readString(planPath);

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
          if (line.toLowerCase(Locale.ROOT).contains("test"))
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
          if (line.toLowerCase(Locale.ROOT).contains("test"))
            ++testFilesInModify;
        }
      }
    }

    int testFiles = testFilesInCreate + testFilesInModify;

    // Count items in Execution Waves (### Wave N sections with bullet items)
    int executionItems = 0;
    Matcher wavesSection = EXECUTION_WAVES_PATTERN.matcher(content);
    if (wavesSection.find())
    {
      String wavesContent = wavesSection.group(1);
      for (String line : wavesContent.split("\n"))
      {
        // Count top-level bullet items only; top-level items start with "- " (zero indent)
        if (line.startsWith("- "))
          ++executionItems;
      }
    }

    return filesToCreate * 5_000 + filesToModify * 3_000 + testFiles * 4_000 + executionItems * 2_000 + 10_000;
  }

  /**
   * Creates a git worktree for the issue branch.
   * <p>
   * If the branch already exists (stale from a previous session), it is deleted first.
   *
   * @param projectPath  the project root directory
   * @param issueBranch  the branch name for the issue
   * @param worktreePath the path where the worktree will be created
   * @throws IOException if worktree creation fails
   */
  private void createWorktree(Path projectPath, String issueBranch, Path worktreePath) throws IOException
  {
    // Check if branch already exists (stale from previous session)
    try
    {
      GitCommands.runGit(projectPath, "rev-parse", "--verify", issueBranch);
      // Branch exists - delete it first
      GitCommands.runGit(projectPath, "branch", "-D", issueBranch);
    }
    catch (IOException _)
    {
      // Branch does not exist - that is fine
    }

    // Create worktree
    GitCommands.runGit(projectPath, "worktree", "add", "-b", issueBranch, worktreePath.toString(),
      "HEAD");
  }

  /**
   * Checks the target branch for suspicious commits that may have already implemented this issue.
   * <p>
   * Uses two complementary strategies:
   * <ol>
   *   <li>Message search: looks for commits whose message mentions the issue name.</li>
   *   <li>File overlap search: looks for commits that modified files listed in plan.md's
   *       "Files to Create" or "Files to Modify" sections.</li>
   * </ol>
   * Planning commits (e.g., {@code planning: add issue ...}) are filtered out as false positives.
   *
   * @param projectPath the project root directory
   * @param targetBranch the target branch to search
   * @param issueName the issue name to search for in commit messages
   * @param planPath the path to plan.md, used to extract planned files for overlap detection
   * @return a newline-separated list of suspicious commit lines, or empty string if none found
   * @throws IOException if plan.md exists but cannot be read
   */
  private String checkTargetBranchCommits(Path projectPath, String targetBranch, String issueName,
    Path planPath) throws IOException
  {
    List<String> planningPrefixes = List.of(
      "planning:", "config: add issue", "planning: add issue", "config: mark", "config: decompose");

    Set<String> suspiciousHashes = new LinkedHashSet<>();
    List<String> suspiciousLines = new ArrayList<>();

    // Strategy 1: message-based search
    try
    {
      String logOutput = GitCommands.runGit(projectPath, "log", "--oneline",
        "--grep=" + issueName, targetBranch, "-5");

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
          if (msg.toLowerCase(Locale.ROOT).startsWith(prefix))
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
      // Pre-compile glob patterns once before the triple-nested loop
      Map<String, Pattern> globPatterns = new LinkedHashMap<>();
      for (String plannedFile : plannedFiles)
      {
        if (plannedFile.contains("*"))
        {
          // Glob-to-regex conversion:
          //   * matches any sequence of characters except path separators (converted to [^/]*)
          //   ** matches zero or more complete path segments, including their separators.
          //      The regex (?:[^/]+/)* always ends with a trailing /, so when the following
          //      literal token starts with /, stripping that / prevents doubling the separator
          //      (e.g., "src/**/foo" would otherwise match "src//foo").
          //   All literal text between wildcards is quoted with Pattern.quote() so characters
          //   like "[", "]", ".", and "(" are treated as literals, not regex syntax.
          StringBuilder regexBuilder = new StringBuilder(".*");
          Matcher tokenMatcher = GLOB_TOKEN_PATTERN.matcher(plannedFile);
          String previousToken = null;
          while (tokenMatcher.find())
          {
            String token = tokenMatcher.group();

            // (?:[^/]+/)* emits a trailing /, so strip the leading / from the next
            // literal token to avoid a doubled separator in the output regex.
            if (Objects.equals(previousToken, "**") && token.startsWith("/"))
              token = token.substring(1);

            if (token.equals("**"))
              regexBuilder.append("(?:[^/]+/)*");
            else if (token.equals("*"))
              regexBuilder.append("[^/]*");
            else
              regexBuilder.append(Pattern.quote(token));

            previousToken = token;
          }
          globPatterns.put(plannedFile, Pattern.compile(regexBuilder.toString()));
        }
      }

      try
      {
        // Get the last 20 commits on base to check for file overlap
        String logOutput = GitCommands.runGit(projectPath, "log", "--oneline", targetBranch, "-20");
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
            if (msg.toLowerCase(Locale.ROOT).startsWith(prefix))
            {
              isPlanning = true;
              break;
            }
          }
          if (isPlanning)
            continue;

          // Check if this commit touched any of the planned files
          String changedFiles = GitCommands.runGit(projectPath, "diff-tree", "--no-commit-id",
            "-r", "--name-only", hash);
          for (String changedFile : changedFiles.split("\n"))
          {
            if (changedFile.isBlank())
              continue;
            for (String plannedFile : plannedFiles)
            {
              // Match by suffix to handle glob patterns like `plugin/agents/stakeholder-*.md`
              boolean matches;
              if (plannedFile.contains("*"))
                matches = globPatterns.get(plannedFile).matcher(changedFile).matches();
              else
                matches = changedFile.endsWith(plannedFile);
              if (matches)
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
   * Extracts the set of file paths listed under "Files to Create" and "Files to Modify" in plan.md.
   * <p>
   * Glob patterns (e.g., {@code plugin/agents/stakeholder-*.md}) are included as-is for later
   * matching against actual changed files.
   *
   * @param planPath the path to plan.md
   * @return the set of planned file paths, or an empty set if plan.md is absent
   * @throws IOException if plan.md exists but cannot be read
   */
  private Set<String> extractPlannedFiles(Path planPath) throws IOException
  {
    if (!Files.isRegularFile(planPath))
      return Collections.emptySet();

    String content = Files.readString(planPath);

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
   * Updates index.json in the worktree to mark the issue as in-progress and record the target branch.
   *
   * @param worktreePath the path to the worktree
   * @param issuePath the absolute path to the issue directory in the main working tree
   * @param projectPath the project root directory
   * @param targetBranch the target branch name for this issue
   * @throws IOException if file operations fail
   */
  private void updateIndexJson(Path worktreePath, Path issuePath, Path projectPath, String targetBranch)
    throws IOException
  {
    // index.json is in the worktree's copy of the issue directory
    Path relativeIssuePath = projectPath.relativize(issuePath);
    Path indexFile = worktreePath.resolve(relativeIssuePath).resolve("index.json");

    if (!Files.isRegularFile(indexFile))
      throw new IOException("index.json not found in worktree: " + indexFile);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(indexFile.toFile());
    if (!root.isObject())
      throw new IOException("index.json does not contain a JSON object: " + indexFile);

    ObjectNode node = (ObjectNode) root;
    node.put("status", "in-progress");
    node.put("target_branch", targetBranch);

    Files.writeString(indexFile, mapper.writeValueAsString(node));

    // Commit index.json to keep the worktree clean
    Path relativeIndexFile = relativeIssuePath.resolve("index.json");
    commitStateFile(worktreePath, relativeIndexFile, "planning: update index.json to in-progress");
  }

  /**
   * Creates a minimal index.json in the worktree for an issue that had no index.json in the main workspace.
   * <p>
   * The created file contains the standard initial state: status {@code in-progress} and the target branch.
   *
   * @param worktreePath the path to the worktree
   * @param issuePath the absolute path to the issue directory in the main working tree
   * @param projectPath the project root directory
   * @param targetBranch the target branch name for this issue
   * @throws IOException if file operations fail
   */
  private void createIndexJson(Path worktreePath, Path issuePath, Path projectPath, String targetBranch)
    throws IOException
  {
    Path relativeIssuePath = projectPath.relativize(issuePath);
    Path issueDir = worktreePath.resolve(relativeIssuePath);
    Files.createDirectories(issueDir);
    Path indexFile = issueDir.resolve("index.json");

    Map<String, Object> state = new LinkedHashMap<>();
    state.put("status", "in-progress");
    state.put("target_branch", targetBranch);

    Files.writeString(indexFile, scope.getJsonMapper().writeValueAsString(state));
  }

  /**
   * Checks whether index.json exists in the worktree.
   *
   * @param worktreePath the path to the worktree
   * @param issuePath the absolute path to the issue directory in the main working tree
   * @param projectPath the project root directory
   * @return true if index.json exists in the worktree, false otherwise
   */
  private boolean indexFileExistsInWorktree(Path worktreePath, Path issuePath, Path projectPath)
  {
    Path relativeIssuePath = projectPath.relativize(issuePath);
    Path indexFile = worktreePath.resolve(relativeIssuePath).resolve("index.json");
    return Files.isRegularFile(indexFile);
  }

  /**
   * Creates index.json in the worktree and commits it to the issue branch.
   * <p>
   * This ensures index.json is established as a committed file in the issue branch,
   * preventing the case where index.json exists untracked in the main workspace but
   * is absent from the worktree.
   *
   * @param worktreePath the path to the worktree
   * @param issuePath the absolute path to the issue directory in the main working tree
   * @param projectPath the project root directory
   * @param targetBranch the target branch name for this issue
   * @throws IOException if file operations or git operations fail
   */
  private void createIndexFileAndCommit(Path worktreePath, Path issuePath, Path projectPath,
    String targetBranch) throws IOException
  {
    // Create the index.json file in the worktree
    createIndexJson(worktreePath, issuePath, projectPath, targetBranch);

    // Commit index.json to the issue branch
    Path relativeIssuePath = projectPath.relativize(issuePath);
    Path relativeIndexFile = relativeIssuePath.resolve("index.json");
    commitStateFile(worktreePath, relativeIndexFile, "planning: create index.json for new issue");
  }

  /**
   * Stages index.json and commits it if the file has staged changes.
   * <p>
   * After staging, checks whether index.json itself has staged changes (as opposed to any
   * other staged file) before committing. This prevents bundling unrelated staged changes into
   * the index.json commit.
   *
   * @param worktreePath the path to the worktree
   * @param relativeIndexFile the path to index.json relative to the worktree root
   * @param commitMessage the commit message to use
   * @throws IOException if git operations fail
   */
  private void commitStateFile(Path worktreePath, Path relativeIndexFile, String commitMessage)
    throws IOException
  {
    try
    {
      GitCommands.runGit(worktreePath, "add", relativeIndexFile.toString());
      // Check whether index.json itself has staged changes; git diff --cached --name-only exits 0
      // regardless of whether files are staged, and returns the names of staged files.
      // We filter to just the index.json path rather than using "git status --porcelain" which
      // would detect unrelated staged files and bundle them into this commit.
      String stagedFiles = GitCommands.runGit(worktreePath, "diff", "--cached", "--name-only",
        "--", relativeIndexFile.toString());
      if (!stagedFiles.isBlank())
        GitCommands.runGit(worktreePath, "commit", "-m", commitMessage);
    }
    catch (IOException e)
    {
      throw new IOException("Failed to commit index.json to git: " + e.getMessage(), e);
    }
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
    catch (Exception e)
    {
      log.warn("Failed to release lock for issue {}: {}", issueId, e.getMessage());
    }
  }

  /**
   * Removes a worktree on failure (best-effort, errors are swallowed).
   *
   * @param projectPath the project root directory
   * @param worktreePath the path to the worktree to remove
   */
  private void cleanupWorktree(Path projectPath, Path worktreePath)
  {
    try
    {
      GitCommands.runGit(projectPath, "worktree", "remove", worktreePath.toString(), "--force");
    }
    catch (IOException _)
    {
      // Best-effort
    }
  }

  /**
   * Holds the result of parsing raw {@code --arguments} input into structured fields.
   *
   * @param issueId the resolved issue ID, or empty string if not present
   * @param excludePattern the resolved exclude-pattern glob, or empty string if not present
   * @param resume whether the user explicitly used a resume/continue keyword
   */
  public record ParsedArguments(String issueId, String excludePattern, boolean resume)
  {
  }

  /**
   * Parses raw {@code --arguments} input into a structured result.
   * <p>
   * Skills pass {@code $ARGUMENTS} with the CAT agent ID (a UUID, or a subagent ID of the form
   * {@code uuid/subagents/name}) as the first token. This prefix is mandatory: if
   * {@code rawArguments} is non-blank and does not start with a valid CAT agent ID, an
   * {@link IllegalArgumentException} is thrown.
   * <p>
   * Leading modifier keywords ({@code resume}, {@code continue}) are stripped before matching,
   * so {@code "resume 2.1-fix-bug"} resolves to issue ID {@code "2.1-fix-bug"}.
   * Keyword stripping is case-sensitive and requires at least one space after the keyword;
   * any extra spaces are collapsed by {@link String#strip()} after the keyword is removed.
   * <p>
   * If {@code issueId} or {@code excludePattern} are already set (non-empty), raw arguments are
   * not parsed (the explicit flags take precedence).
   *
   * @param rawArguments the raw user-supplied argument string, or empty string
   * @param issueId the explicit issue ID already set via {@code --issue-id}, or empty string
   * @param excludePattern the explicit exclude pattern already set via {@code --exclude-pattern}, or empty string
   * @return parsed result with resolved issueId and excludePattern (never null; fields may be empty)
   * @throws IllegalArgumentException if {@code rawArguments} is non-blank, {@code issueId} and
   *   {@code excludePattern} are both empty, and {@code rawArguments} does not start with a valid
   *   CAT agent ID
   */
  public static ParsedArguments parseRawArguments(String rawArguments, String issueId,
    String excludePattern)
  {
    if (rawArguments.isBlank() || !issueId.isEmpty() || !excludePattern.isEmpty())
      return new ParsedArguments(issueId, excludePattern, false);

    String raw = rawArguments.strip();
    // Skills pass $ARGUMENTS with the catAgentId as the first token. It must be present and stripped
    // before interpreting the remainder as user-supplied arguments.
    Matcher agentIdMatcher = CAT_AGENT_ID_TOKEN.matcher(raw);
    if (!agentIdMatcher.lookingAt())
    {
      throw new IllegalArgumentException(
        "rawArguments does not start with a valid catAgentId. " +
          "Skills must pass $ARGUMENTS which prepends the CAT agent ID as the first token. " +
          "rawArguments: '" + rawArguments + "'");
    }
    raw = raw.substring(agentIdMatcher.end()).strip();
    if (raw.isBlank())
      return new ParsedArguments("", "", false);
    // Detect leading modifier keywords (e.g. "resume 2.1-fix-bug") and capture as a boolean flag
    boolean resume = false;
    for (String keyword : new String[]{"resume", "continue"})
    {
      if (raw.startsWith(keyword + " "))
      {
        raw = raw.substring(keyword.length()).strip();
        resume = true;
        break;
      }
    }
    if (raw.matches("^[0-9]+\\.[0-9]+(-[a-zA-Z0-9_-]+)?$") ||
      raw.matches("^[a-zA-Z][a-zA-Z0-9_-]*$"))
    {
      return new ParsedArguments(raw, "", resume);
    }
    if (raw.startsWith("skip "))
    {
      String word = raw.substring(5).strip();
      return new ParsedArguments("", "*" + word + "*", resume);
    }
    return new ParsedArguments("", "", resume);
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
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (RuntimeException | AssertionError e)
      {
        LoggerFactory.getLogger(WorkPrepare.class).error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the work-prepare logic with a caller-provided output stream.
   *
   * @param scope the JVM scope
   * @param args  command-line arguments
   * @param out   the output stream to write JSON to
   * @throws NullPointerException if {@code args} or {@code out} are null
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out)
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    String sessionIdOverride = "";
    String excludePattern = "";
    String issueId = "";
    String trustLevelStr = "medium";
    String rawArguments = "";

    try
    {
      for (int i = 0; i < args.length; ++i)
      {
        switch (args[i])
        {
          case "--session-id" ->
          {
            if (!checkFlag(i, args, "--session-id", out, scope))
              return;
            ++i;
            sessionIdOverride = args[i];
          }
          case "--exclude-pattern" ->
          {
            if (!checkFlag(i, args, "--exclude-pattern", out, scope))
              return;
            ++i;
            excludePattern = args[i];
          }
          case "--issue-id" ->
          {
            if (!checkFlag(i, args, "--issue-id", out, scope))
              return;
            ++i;
            issueId = args[i];
          }
          case "--trust-level" ->
          {
            if (!checkFlag(i, args, "--trust-level", out, scope))
              return;
            ++i;
            trustLevelStr = args[i];
          }
          case "--arguments" ->
          {
            if (!checkFlag(i, args, "--arguments", out, scope))
              return;
            ++i;
            rawArguments = args[i];
          }
          default ->
          {
            out.println(toErrorJson(scope, "Unknown flag '" + args[i] + "'. Valid flags: --session-id, " +
              "--exclude-pattern, --issue-id, --trust-level, --arguments"));
            return;
          }
        }
      }
    }
    catch (IOException e)
    {
      LoggerFactory.getLogger(WorkPrepare.class).error("Failed to serialize error message", e);
      out.println("{\"status\":\"ERROR\",\"message\":\"serialization failed\"}");
      return;
    }

    // Parse raw arguments into issue-id or exclude-pattern
    ParsedArguments parsed = parseRawArguments(rawArguments, issueId, excludePattern);
    issueId = parsed.issueId();
    excludePattern = parsed.excludePattern();

    TrustLevel trustLevel;
    try
    {
      trustLevel = TrustLevel.fromString(trustLevelStr);
    }
    catch (IllegalArgumentException e)
    {
      out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      return;
    }

    // Use scope-provided session ID if not overridden via --session-id
    String sessionId;
    if (!sessionIdOverride.isEmpty())
      sessionId = sessionIdOverride;
    else
    {
      sessionId = scope.getSessionId();
    }

    PrepareInput input = new PrepareInput(sessionId, excludePattern, issueId, trustLevel,
      parsed.resume());
    WorkPrepare wp = new WorkPrepare(scope);
    try
    {
      String result = wp.execute(input);
      out.println(result);
    }
    catch (IOException e)
    {
      // Use business-format JSON (status + message) because the work skill parses this output directly,
      // not via Claude Code's hook output parser.
      String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
      try
      {
        out.println(toErrorJson(scope, message));
      }
      catch (IOException jsonException)
      {
        LoggerFactory.getLogger(WorkPrepare.class).error("Failed to serialize error message", jsonException);
        out.println("{\"status\":\"ERROR\",\"message\":\"serialization failed\"}");
      }
    }
  }

  /**
   * Validates that the flag at position {@code flagIndex} has a value argument, outputting an error
   * JSON message and returning {@code false} if not.
   *
   * @param flagIndex the index of the flag in {@code args}
   * @param args the command-line arguments
   * @param flagName the name of the flag (e.g., "--session-id")
   * @param out the output stream to write the error JSON to
   * @param scope the JVM scope
   * @return {@code true} if the flag has a value argument; {@code false} otherwise
   * @throws IOException if JSON serialization of the error message fails
   */
  private static boolean checkFlag(int flagIndex, String[] args, String flagName, PrintStream out,
    ClaudeTool scope) throws IOException
  {
    try
    {
      CliArgs.requiredValue(flagIndex, args, flagName);
      return true;
    }
    catch (IllegalArgumentException e)
    {
      out.println(toErrorJson(scope, e.getMessage()));
      return false;
    }
  }

  /**
   * Produces a business-format JSON error string with properly escaped {@code message}.
   * <p>
   * Uses {@link JsonMapper#writeValueAsString(Object)} for correct JSON encoding of all control
   * characters (newlines, tabs, carriage returns) in addition to {@code "} and {@code \}.
   *
   * @param scope   the JVM scope providing the shared {@link JsonMapper}
   * @param message the error message to include in the JSON
   * @return a JSON string of the form {@code {"status":"ERROR","message":"..."}}
   * @throws NullPointerException if {@code message} is null
   * @throws IOException if JSON serialization fails
   */
  public static String toErrorJson(ClaudeTool scope, String message) throws IOException
  {
    requireThat(message, "message").isNotNull();
    String escapedMessage = scope.getJsonMapper().writeValueAsString(message);
    return "{\"status\":\"ERROR\",\"message\":" + escapedMessage + "}";
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
   * Holder for an issue's index file path and content.
   *
   * @param indexPath the path to the index.json file
   * @param content the file content
   */
  private record IssueIndexEntry(Path indexPath, String content)
  {
    /**
     * Creates a new issue index entry.
     *
     * @param indexPath the path to the index.json file
     * @param content the file content
     * @throws NullPointerException if {@code indexPath} or {@code content} are null
     */
    IssueIndexEntry
    {
      assert that(indexPath, "indexPath").isNotNull().elseThrow();
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
