/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.JvmScope;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Issue discovery for CAT workflow.
 * <p>
 * Java equivalent of {@code get-available-issues.sh}. Scans the {@code .claude/cat/issues} directory for
 * open or in-progress issues, checks dependencies, evaluates exit gates, and integrates with
 * {@link IssueLock} for lock acquisition.
 * <p>
 * The search scope controls which version directories are searched:
 * <ul>
 *   <li>{@code all} - all major version directories under {@code .claude/cat/issues}</li>
 *   <li>{@code major} - a specific major version (e.g., {@code v2})</li>
 *   <li>{@code minor} - a specific minor version (e.g., {@code v2.1})</li>
 *   <li>{@code issue} - a specific issue by fully-qualified ID (e.g., {@code 2.1-fix-bug})</li>
 *   <li>{@code bare_name} - a bare issue name resolved against all versions</li>
 * </ul>
 */
public final class IssueDiscovery
{
  /**
   * Pattern matching a version directory name like {@code v2}, {@code v2.1}, or {@code v2.1.3}.
   */
  private static final Pattern VERSION_DIR_PATTERN = Pattern.compile("^v\\d+(\\.\\d+){0,2}$");
  /**
   * Pattern matching a major version directory name like {@code v2}.
   */
  private static final Pattern MAJOR_VERSION_PATTERN = Pattern.compile("^v(\\d+)$");
  /**
   * Pattern matching a minor version directory name like {@code v2.1}.
   */
  private static final Pattern MINOR_VERSION_PATTERN = Pattern.compile("^v(\\d+)\\.(\\d+)$");
  /**
   * Pattern matching a patch version directory name like {@code v2.1.3}.
   */
  private static final Pattern PATCH_VERSION_PATTERN = Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)$");
  /**
   * Pattern matching a fully-qualified issue ID like {@code 2-fix-bug}, {@code 2.1-fix-bug}, or
   * {@code 2.1.3-fix-bug}.
   * <p>
   * Group 1: major (always present), group 2: minor (null for major-only), group 3: patch (null unless
   * patch-level), group 4: issue name.
   */
  private static final Pattern QUALIFIED_ISSUE_ID_PATTERN =
    Pattern.compile("^(\\d+)(?:\\.(\\d+)(?:\\.(\\d+))?)?-([a-zA-Z][a-zA-Z0-9_-]*)$");
  /**
   * Pattern matching a bare issue name like {@code fix-bug}.
   */
  private static final Pattern BARE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");
  /**
   * Pattern for the "Decomposed Into" section header in STATE.md.
   */
  private static final Pattern DECOMPOSED_INTO_PATTERN = Pattern.compile("^## Decomposed Into");
  /**
   * Pattern for the next section header in STATE.md.
   */
  private static final Pattern NEXT_SECTION_PATTERN = Pattern.compile("^## ");
  /**
   * Pattern for sub-issue list items in the "Decomposed Into" section.
   */
  private static final Pattern SUBISSUE_ITEM_PATTERN = Pattern.compile("^- ([^(\\s]+)");
  /**
   * Pattern for issue entries in the exit section of a version PLAN.md.
   */
  private static final Pattern EXIT_ISSUE_PATTERN = Pattern.compile("^- \\[issue\\] (.+)$");

  private final Path projectDir;
  private final Path issuesDir;
  private final IssueLock issueLock;

  /**
   * Creates a new issue discovery instance.
   *
   * @param scope the JVM scope providing configuration and services
   * @throws NullPointerException if {@code scope} is null
   * @throws IllegalArgumentException if the project directory is not a valid CAT project
   */
  public IssueDiscovery(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.projectDir = scope.getClaudeProjectDir();
    Path catDir = projectDir.resolve(".claude").resolve("cat");
    if (!Files.isDirectory(catDir))
    {
      throw new IllegalArgumentException("Not a CAT project: " + projectDir +
        " (no .claude/cat directory)");
    }
    this.issuesDir = catDir.resolve("issues");
    this.issueLock = new IssueLock(scope);
  }

  /**
   * Search scope for issue discovery.
   */
  public enum Scope
  {
    /**
     * Search all major versions.
     */
    ALL,
    /**
     * Search a specific major version.
     */
    MAJOR,
    /**
     * Search a specific minor version.
     */
    MINOR,
    /**
     * Search for a specific issue by fully-qualified ID.
     */
    ISSUE,
    /**
     * Search for an issue by bare name (resolved against all versions).
     */
    BARE_NAME
  }

  /**
   * Result of issue discovery.
   * <p>
   * Sealed hierarchy of possible outcomes.
   */
  public sealed interface DiscoveryResult permits
    DiscoveryResult.Found,
    DiscoveryResult.NotFound,
    DiscoveryResult.AlreadyComplete,
    DiscoveryResult.NotExecutable,
    DiscoveryResult.Blocked,
    DiscoveryResult.Decomposed,
    DiscoveryResult.ExistingWorktree,
    DiscoveryResult.DiscoveryError
  {
    /**
     * Converts this result to JSON format.
     *
     * @param mapper the JSON mapper for serialization
     * @return JSON string representation
     * @throws NullPointerException if {@code mapper} is null
     * @throws IOException if JSON serialization fails
     */
    String toJson(JsonMapper mapper) throws IOException;

    /**
     * Issue found and lock acquired.
     *
     * @param issueId the fully-qualified issue ID
     * @param major the major version number
     * @param minor the minor version number, or empty string for major-only issues
     * @param patch the patch version number, or empty string if not a patch-level issue
     * @param issueName the bare issue name
     * @param issuePath the absolute path to the issue directory
     * @param scope the scope used to find the issue
     */
    record Found(String issueId, String major, String minor, String patch, String issueName,
      String issuePath, String scope) implements DiscoveryResult
    {
      /**
       * Creates a new found result.
       *
       * @param issueId the fully-qualified issue ID
       * @param major the major version number
       * @param minor the minor version number, or empty string for major-only issues
       * @param patch the patch version number, or empty string if not a patch-level issue
       * @param issueName the bare issue name
       * @param issuePath the absolute path to the issue directory
       * @param scope the scope used to find the issue
       * @throws IllegalArgumentException if {@code issueId}, {@code major}, {@code issueName},
       *   {@code issuePath} or {@code scope} are blank
       * @throws NullPointerException if {@code minor} or {@code patch} are null
       */
      public Found
      {
        requireThat(issueId, "issueId").isNotBlank();
        requireThat(major, "major").isNotBlank();
        requireThat(minor, "minor").isNotNull();
        requireThat(patch, "patch").isNotNull();
        requireThat(issueName, "issueName").isNotBlank();
        requireThat(issuePath, "issuePath").isNotBlank();
        requireThat(scope, "scope").isNotBlank();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "found");
        result.put("issue_id", issueId);
        result.put("major", major);
        if (!minor.isEmpty())
        {
          result.put("minor", minor);
          if (!patch.isEmpty())
            result.put("patch", patch);
        }
        result.put("issue_name", issueName);
        result.put("issue_path", issuePath);
        result.put("scope", scope);
        result.put("lock_status", "acquired");
        return mapper.writeValueAsString(result);
      }
    }

    /**
     * No executable issue found.
     *
     * @param scope the scope that was searched
     * @param excludePattern the exclude pattern applied, or empty string if none
     * @param excludedCount the number of issues excluded by the pattern
     */
    record NotFound(String scope, String excludePattern, int excludedCount) implements DiscoveryResult
    {
      /**
       * Creates a not-found result.
       *
       * @param scope the scope that was searched
       * @param excludePattern the exclude pattern applied, or empty string if none
       * @param excludedCount the number of issues excluded by the pattern
       * @throws IllegalArgumentException if {@code scope} is blank
       * @throws NullPointerException if {@code excludePattern} is null
       */
      public NotFound
      {
        requireThat(scope, "scope").isNotBlank();
        requireThat(excludePattern, "excludePattern").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "not_found");
        if (!excludePattern.isEmpty() && excludedCount > 0)
        {
          result.put("message", "No executable issues found (" + excludedCount + " excluded by pattern)");
          result.put("scope", scope);
          result.put("exclude_pattern", excludePattern);
          result.put("excluded_count", excludedCount);
        }
        else
        {
          result.put("message", "No executable issues found");
          result.put("scope", scope);
        }
        return mapper.writeValueAsString(result);
      }
    }

    /**
     * The requested issue is already closed.
     *
     * @param issueId the fully-qualified issue ID
     */
    record AlreadyComplete(String issueId) implements DiscoveryResult
    {
      /**
       * Creates an already-complete result.
       *
       * @param issueId the fully-qualified issue ID
       * @throws IllegalArgumentException if {@code issueId} is blank
       */
      public AlreadyComplete
      {
        requireThat(issueId, "issueId").isNotBlank();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", "already_complete",
          "message", "Issue " + issueId + " is already closed - no work needed",
          "issue_id", issueId));
      }
    }

    /**
     * The requested issue exists but is not executable (e.g., blocked status or malformed).
     *
     * @param issueId the fully-qualified issue ID
     * @param reason description of why the issue is not executable
     */
    record NotExecutable(String issueId, String reason) implements DiscoveryResult
    {
      /**
       * Creates a not-executable result.
       *
       * @param issueId the fully-qualified issue ID
       * @param reason description of why the issue is not executable
       * @throws IllegalArgumentException if {@code issueId} or {@code reason} are blank
       */
      public NotExecutable
      {
        requireThat(issueId, "issueId").isNotBlank();
        requireThat(reason, "reason").isNotBlank();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", "not_executable",
          "message", reason,
          "issue_id", issueId));
      }
    }

    /**
     * The requested issue has unsatisfied dependencies.
     *
     * @param issueId the fully-qualified issue ID
     * @param blockingIssues list of issue IDs that are blocking this issue
     */
    record Blocked(String issueId, List<String> blockingIssues) implements DiscoveryResult
    {
      /**
       * Creates a blocked result.
       *
       * @param issueId the fully-qualified issue ID
       * @param blockingIssues list of issue IDs that are blocking this issue
       * @throws IllegalArgumentException if {@code issueId} is blank
       * @throws NullPointerException if {@code blockingIssues} is null
       */
      public Blocked
      {
        requireThat(issueId, "issueId").isNotBlank();
        requireThat(blockingIssues, "blockingIssues").isNotNull();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", "blocked",
          "message", "Dependencies not satisfied",
          "issue_id", issueId,
          "blocking", blockingIssues));
      }
    }

    /**
     * The requested issue is a decomposed parent task with open sub-issues.
     *
     * @param issueId the fully-qualified issue ID
     */
    record Decomposed(String issueId) implements DiscoveryResult
    {
      /**
       * Creates a decomposed result.
       *
       * @param issueId the fully-qualified issue ID
       * @throws IllegalArgumentException if {@code issueId} is blank
       */
      public Decomposed
      {
        requireThat(issueId, "issueId").isNotBlank();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", "decomposed",
          "message", "Issue is a decomposed parent task - execute sub-issues instead",
          "issue_id", issueId));
      }
    }

    /**
     * The requested issue has an existing worktree (likely in use by another session).
     *
     * @param issueId the fully-qualified issue ID
     * @param major the major version number
     * @param minor the minor version number, or empty string for major-only issues
     * @param patch the patch version number, or empty string if not a patch-level issue
     * @param issueName the bare issue name
     * @param issuePath the absolute path to the issue directory
     * @param worktreePath the path to the existing worktree
     */
    record ExistingWorktree(String issueId, String major, String minor, String patch,
      String issueName, String issuePath, String worktreePath) implements DiscoveryResult
    {
      /**
       * Creates an existing-worktree result.
       *
       * @param issueId the fully-qualified issue ID
       * @param major the major version number
       * @param minor the minor version number, or empty string for major-only issues
       * @param patch the patch version number, or empty string if not a patch-level issue
       * @param issueName the bare issue name
       * @param issuePath the absolute path to the issue directory
       * @param worktreePath the path to the existing worktree
       * @throws IllegalArgumentException if {@code issueId}, {@code major}, {@code issueName},
       *   {@code issuePath} or {@code worktreePath} are blank
       * @throws NullPointerException if {@code minor} or {@code patch} are null
       */
      public ExistingWorktree
      {
        requireThat(issueId, "issueId").isNotBlank();
        requireThat(major, "major").isNotBlank();
        requireThat(minor, "minor").isNotNull();
        requireThat(patch, "patch").isNotNull();
        requireThat(issueName, "issueName").isNotBlank();
        requireThat(issuePath, "issuePath").isNotBlank();
        requireThat(worktreePath, "worktreePath").isNotBlank();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "existing_worktree");
        result.put("issue_id", issueId);
        result.put("major", major);
        if (!minor.isEmpty())
        {
          result.put("minor", minor);
          if (!patch.isEmpty())
            result.put("patch", patch);
        }
        result.put("issue_name", issueName);
        result.put("issue_path", issuePath);
        result.put("worktree_path", worktreePath);
        result.put("message", "Issue has existing worktree - likely in use by another session");
        return mapper.writeValueAsString(result);
      }
    }

    /**
     * Discovery encountered an error.
     *
     * @param message the error message
     */
    record DiscoveryError(String message) implements DiscoveryResult
    {
      /**
       * Creates an error result.
       *
       * @param message the error message
       * @throws IllegalArgumentException if {@code message} is blank
       */
      public DiscoveryError
      {
        requireThat(message, "message").isNotBlank();
      }

      @Override
      public String toJson(JsonMapper mapper) throws IOException
      {
        requireThat(mapper, "mapper").isNotNull();
        return mapper.writeValueAsString(Map.of(
          "status", "error",
          "message", message));
      }
    }
  }

  /**
   * Configuration options for issue discovery.
   *
   * @param scope the search scope
   * @param target the target version or issue ID (may be empty)
   * @param sessionId the Claude session ID for lock acquisition (may be empty to skip locking)
   * @param excludePattern a glob-style pattern to exclude issues by name (may be empty)
   * @param overridePostconditions if true, skip post-condition evaluation
   */
  public record SearchOptions(Scope scope, String target, String sessionId, String excludePattern,
    boolean overridePostconditions)
  {
    /**
     * Creates new search options.
     *
     * @param scope the search scope
     * @param target the target version or issue ID (may be empty)
     * @param sessionId the Claude session ID for lock acquisition (may be empty to skip locking)
     * @param excludePattern a glob-style pattern to exclude issues by name (may be empty)
     * @param overridePostconditions if true, skip post-condition evaluation
     * @throws NullPointerException if {@code scope}, {@code target}, {@code sessionId} or
     *   {@code excludePattern} are null
     */
    public SearchOptions
    {
      requireThat(scope, "scope").isNotNull();
      requireThat(target, "target").isNotNull();
      requireThat(sessionId, "sessionId").isNotNull();
      requireThat(excludePattern, "excludePattern").isNotNull();
    }
  }

  /**
   * Finds the next executable issue based on the provided search options.
   *
   * @param options the search options
   * @return the discovery result
   * @throws NullPointerException if {@code options} is null
   * @throws IOException if file operations fail
   */
  public DiscoveryResult findNextIssue(SearchOptions options) throws IOException
  {
    requireThat(options, "options").isNotNull();

    Scope scope = options.scope();
    String target = options.target();

    // Handle bare_name scope: resolve to a fully-qualified issue ID
    if (scope == Scope.BARE_NAME && !target.isEmpty())
    {
      if (!BARE_NAME_PATTERN.matcher(target).matches())
      {
        return new DiscoveryResult.DiscoveryError("Invalid bare issue name format: " + target);
      }
      String resolvedId = resolveBareNameToIssueId(target);
      if (resolvedId == null)
        return new DiscoveryResult.NotFound("bare_name", "", 0);
      scope = Scope.ISSUE;
      target = resolvedId;
    }

    // Handle specific issue scope
    if (scope == Scope.ISSUE && !target.isEmpty())
      return findSpecificIssue(target, options);

    // Handle search scopes (all, major, minor)
    return searchForIssue(scope, target, options);
  }

  /**
   * Resolves a bare issue name to a fully-qualified issue ID by searching all version directories,
   * including patch subdirectories.
   *
   * @param bareName the bare issue name (e.g., {@code fix-bug})
   * @return the fully-qualified issue ID (e.g., {@code 2.1-fix-bug} or {@code 2.1.3-fix-bug}),
   *   or null if not found
   * @throws IOException if file operations fail
   */
  private String resolveBareNameToIssueId(String bareName) throws IOException
  {
    List<Path> matchingDirs = new ArrayList<>();
    for (Path majorDir : listMajorDirs())
    {
      // Check directly under the major dir (major-only layout)
      Path majorIssueDir = majorDir.resolve(bareName);
      if (Files.isDirectory(majorIssueDir) && Files.isRegularFile(majorIssueDir.resolve("STATE.md")))
        matchingDirs.add(majorIssueDir);

      for (Path minorDir : listMinorDirs(majorDir))
      {
        // Check directly under the minor dir
        Path issueDir = minorDir.resolve(bareName);
        if (Files.isDirectory(issueDir) && Files.isRegularFile(issueDir.resolve("STATE.md")))
          matchingDirs.add(issueDir);

        // Check under patch subdirs
        for (Path patchDir : listPatchDirs(minorDir))
        {
          Path patchIssueDir = patchDir.resolve(bareName);
          if (Files.isDirectory(patchIssueDir) && Files.isRegularFile(patchIssueDir.resolve("STATE.md")))
            matchingDirs.add(patchIssueDir);
        }
      }
    }

    if (matchingDirs.isEmpty())
      return null;

    // Select which match to use - use first match in version order
    Path selectedDir = matchingDirs.get(0);

    // Extract version components from path
    // Path format: .../issues/v2/issue-name OR .../issues/v2/v2.1/issue-name OR
    // .../issues/v2/v2.1/v2.1.3/issue-name
    Path parentDir = selectedDir.getParent();
    if (parentDir == null)
      return null;

    String parentName = parentDir.getFileName().toString();
    Matcher patchMatcher = PATCH_VERSION_PATTERN.matcher(parentName);
    if (patchMatcher.matches())
    {
      String major = patchMatcher.group(1);
      String minor = patchMatcher.group(2);
      String patch = patchMatcher.group(3);
      return buildIssueId(major, minor, patch, bareName);
    }

    Matcher minorMatcher = MINOR_VERSION_PATTERN.matcher(parentName);
    if (minorMatcher.matches())
    {
      String major = minorMatcher.group(1);
      String minor = minorMatcher.group(2);
      return buildIssueId(major, minor, "", bareName);
    }

    Matcher majorMatcher = MAJOR_VERSION_PATTERN.matcher(parentName);
    if (!majorMatcher.matches())
      return null;

    String major = majorMatcher.group(1);
    return buildIssueId(major, "", "", bareName);
  }

  /**
   * Finds a specific issue by fully-qualified ID.
   *
   * @param issueId the fully-qualified issue ID (e.g., {@code 2.1-fix-bug})
   * @param options the search options
   * @return the discovery result
   * @throws IOException if file operations fail
   */
  private DiscoveryResult findSpecificIssue(String issueId, SearchOptions options) throws IOException
  {
    Matcher matcher = QUALIFIED_ISSUE_ID_PATTERN.matcher(issueId);
    if (!matcher.matches())
      return new DiscoveryResult.NotFound("issue", "", 0);

    String major = matcher.group(1);
    String minor;
    if (matcher.group(2) != null)
      minor = matcher.group(2);
    else
      minor = "";
    String patch;
    if (matcher.group(3) != null)
      patch = matcher.group(3);
    else
      patch = "";
    String issueName = matcher.group(4);

    Path issueDir = resolveVersionDir(major, minor, patch).resolve(issueName);

    if (!Files.isDirectory(issueDir))
    {
      return new DiscoveryResult.NotFound("issue", "", 0);
    }

    Path statePath = issueDir.resolve("STATE.md");
    List<String> stateLines;
    try
    {
      stateLines = readFileLines(statePath);
    }
    catch (IOException _)
    {
      return new DiscoveryResult.NotExecutable(issueId,
        "Issue " + issueId + " has no readable status");
    }

    String status;
    try
    {
      status = getIssueStatus(stateLines, statePath);
    }
    catch (IOException _)
    {
      return new DiscoveryResult.NotExecutable(issueId,
        "Issue " + issueId + " has no readable status");
    }

    if (!status.equals("open") && !status.equals("in-progress"))
    {
      if (status.equals("closed"))
        return new DiscoveryResult.AlreadyComplete(issueId);
      return new DiscoveryResult.NotExecutable(issueId,
        "Issue status is " + status + " (not open/in-progress)");
    }

    // Check if decomposed parent task with open sub-issues
    if (isDecomposedParent(stateLines) && !allSubissuesClosed(statePath))
      return new DiscoveryResult.Decomposed(issueId);

    // Check dependencies
    List<String> dependencies = getDependencies(stateLines);
    List<String> blockingDependencies = getBlockingDependencies(dependencies);
    if (!blockingDependencies.isEmpty())
      return new DiscoveryResult.Blocked(issueId, blockingDependencies);

    // Check for existing worktree
    Path worktreePath = getWorktreePath(issueId);
    if (Files.isDirectory(worktreePath))
    {
      return new DiscoveryResult.ExistingWorktree(issueId, major, minor, patch, issueName,
        issueDir.toString(), worktreePath.toString());
    }

    // Try to acquire lock
    if (!options.sessionId().isEmpty())
    {
      IssueLock.LockResult lockResult = issueLock.acquire(issueId, options.sessionId(), "");
      if (lockResult instanceof IssueLock.LockResult.Locked locked)
      {
        return new DiscoveryResult.NotExecutable(issueId,
          "Issue locked by another session: " + locked.owner());
      }
    }

    return new DiscoveryResult.Found(issueId, major, minor, patch, issueName, issueDir.toString(),
      "issue");
  }

  /**
   * Searches for the next available issue based on scope.
   *
   * @param scope the search scope (all, major, or minor)
   * @param target the target identifier for major/minor scopes
   * @param options the search options
   * @return the discovery result
   * @throws IOException if file operations fail
   */
  private DiscoveryResult searchForIssue(Scope scope, String target, SearchOptions options)
    throws IOException
  {
    AtomicInteger excludedCount = new AtomicInteger(0);
    List<Path> searchDirs = buildSearchDirs(scope, target);

    for (Path searchDir : searchDirs)
    {
      if (!Files.isDirectory(searchDir))
        continue;

      String dirName = searchDir.getFileName().toString();
      Matcher majorMatcher = MAJOR_VERSION_PATTERN.matcher(dirName);
      Matcher minorMatcher = MINOR_VERSION_PATTERN.matcher(dirName);
      Matcher patchMatcher = PATCH_VERSION_PATTERN.matcher(dirName);

      if (majorMatcher.matches())
      {
        String major = majorMatcher.group(1);

        // Check for issues directly under the major dir (major-only layout)
        DiscoveryResult.Found majorOnlyFound = findIssueInDir(searchDir, major, "", "", options,
          excludedCount, scope.name().toLowerCase(Locale.ROOT));
        if (majorOnlyFound != null)
          return majorOnlyFound;

        // Also find the first incomplete minor under this major dir
        Path minorDir = findFirstIncompleteMinor(searchDir);
        if (minorDir == null)
          continue;

        DiscoveryResult.Found found = findIssueInMinor(minorDir, options, excludedCount,
          scope.name().toLowerCase(Locale.ROOT));
        if (found != null)
          return found;
      }
      if (minorMatcher.matches())
      {
        // Minor directory - search directly (including patch subdirs)
        DiscoveryResult.Found found = findIssueInMinor(searchDir, options, excludedCount,
          scope.name().toLowerCase(Locale.ROOT));
        if (found != null)
          return found;
      }
      if (patchMatcher.matches())
      {
        // Patch directory - search issues directly within it
        String major = patchMatcher.group(1);
        String minor = patchMatcher.group(2);
        String patch = patchMatcher.group(3);
        DiscoveryResult.Found found = findIssueInDir(searchDir, major, minor, patch, options,
          excludedCount, scope.name().toLowerCase(Locale.ROOT));
        if (found != null)
          return found;
      }
    }

    return new DiscoveryResult.NotFound(scope.name().toLowerCase(Locale.ROOT), options.excludePattern(),
      excludedCount.get());
  }

  /**
   * Builds the list of directories to search based on scope and target.
   *
   * @param scope the search scope
   * @param target the target identifier
   * @return list of directories to search
   */
  private List<Path> buildSearchDirs(Scope scope, String target)
  {
    switch (scope)
    {
      case ALL ->
      {
        try
        {
          return listMajorDirs();
        }
        catch (IOException _)
        {
          return Collections.emptyList();
        }
      }
      case MAJOR ->
      {
        return List.of(issuesDir.resolve("v" + target));
      }
      case MINOR ->
      {
        // Target may be "major.minor" or "major.minor.patch"
        int firstDot = target.indexOf('.');
        if (firstDot < 0)
          return Collections.emptyList();
        int secondDot = target.indexOf('.', firstDot + 1);
        if (secondDot >= 0)
        {
          // patch-qualified target: major.minor.patch
          String major = target.substring(0, firstDot);
          String minor = target.substring(firstDot + 1, secondDot);
          String patch = target.substring(secondDot + 1);
          return List.of(resolveVersionDir(major, minor, patch));
        }
        String major = target.substring(0, firstDot);
        String minor = target.substring(firstDot + 1);
        return List.of(resolveVersionDir(major, minor));
      }
      default ->
      {
        return Collections.emptyList();
      }
    }
  }

  /**
   * Finds the first minor version directory under a major directory that has open/in-progress issues.
   *
   * @param majorDir the major version directory
   * @return the first incomplete minor directory, or null if none found
   * @throws IOException if file operations fail
   */
  private Path findFirstIncompleteMinor(Path majorDir) throws IOException
  {
    for (Path minorDir : listMinorDirs(majorDir))
    {
      // Check version-level dependencies before scanning tasks
      Path versionStatePath = minorDir.resolve("STATE.md");
      if (Files.isRegularFile(versionStatePath))
      {
        List<String> versionDeps = getDependencies(versionStatePath);
        List<String> blockingVersionDeps = getBlockingDependencies(versionDeps);
        if (!blockingVersionDeps.isEmpty())
          continue;
      }

      // Check if minor has any open/in-progress issues
      if (hasOpenIssues(minorDir))
        return minorDir;
    }

    return null;
  }

  /**
   * Checks if a minor version directory has any open or in-progress issues, including patch subdirs.
   *
   * @param minorDir the minor version directory to check
   * @return true if there are open or in-progress issues
   * @throws IOException if file operations fail
   */
  private boolean hasOpenIssues(Path minorDir) throws IOException
  {
    // Check direct issue dirs
    for (Path issueDir : listIssueDirs(minorDir))
    {
      Path statePath = issueDir.resolve("STATE.md");
      if (!Files.isRegularFile(statePath))
        continue;
      try
      {
        String status = getIssueStatus(statePath);
        if ("open".equals(status) || "in-progress".equals(status))
          return true;
      }
      catch (IOException _)
      {
        // Skip unreadable issues
      }
    }

    // Check patch subdirs
    for (Path patchDir : listPatchDirs(minorDir))
    {
      for (Path issueDir : listIssueDirs(patchDir))
      {
        Path statePath = issueDir.resolve("STATE.md");
        if (!Files.isRegularFile(statePath))
          continue;
        try
        {
          String status = getIssueStatus(statePath);
          if ("open".equals(status) || "in-progress".equals(status))
            return true;
        }
        catch (IOException _)
        {
          // Skip unreadable issues
        }
      }
    }

    return false;
  }

  /**
   * Finds the first executable issue in a minor version directory, including patch subdirectories.
   *
   * @param minorDir the minor version directory
   * @param options the search options
   * @param excludedCount counter for excluded issues, incremented for each skipped issue
   * @param scopeName the scope name for the result
   * @return the found issue result, or null if no executable issue found
   * @throws IOException if file operations fail
   */
  private DiscoveryResult.Found findIssueInMinor(Path minorDir, SearchOptions options,
    AtomicInteger excludedCount, String scopeName) throws IOException
  {
    Matcher minorMatcher = MINOR_VERSION_PATTERN.matcher(minorDir.getFileName().toString());
    if (!minorMatcher.matches())
      return null;

    String major = minorMatcher.group(1);
    String minor = minorMatcher.group(2);

    // Search direct issue dirs under the minor directory (no patch)
    DiscoveryResult.Found found = findIssueInDir(minorDir, major, minor, "", options, excludedCount,
      scopeName);
    if (found != null)
      return found;

    // Search patch subdirectories
    for (Path patchDir : listPatchDirs(minorDir))
    {
      Matcher patchMatcher = PATCH_VERSION_PATTERN.matcher(patchDir.getFileName().toString());
      if (!patchMatcher.matches())
        continue;
      String patch = patchMatcher.group(3);
      found = findIssueInDir(patchDir, major, minor, patch, options, excludedCount, scopeName);
      if (found != null)
        return found;
    }

    return null;
  }

  /**
   * Finds the first executable issue in a single directory (major, minor, or patch level).
   *
   * @param searchDir the directory to search for issues
   * @param major the major version number
   * @param minor the minor version number, or empty string for major-only layout
   * @param patch the patch version number, or empty string if not patch-level
   * @param options the search options
   * @param excludedCount counter for excluded issues, incremented for each skipped issue
   * @param scopeName the scope name for the result
   * @return the found issue result, or null if no executable issue found
   * @throws IOException if file operations fail
   */
  private DiscoveryResult.Found findIssueInDir(Path searchDir, String major, String minor,
    String patch, SearchOptions options, AtomicInteger excludedCount, String scopeName)
    throws IOException
  {
    Path minorDir;
    if (patch.isEmpty())
      minorDir = searchDir;
    else
      minorDir = searchDir.getParent();

    for (Path issueDir : listIssueDirs(searchDir))
    {
      String issueName = issueDir.getFileName().toString();
      Path statePath = issueDir.resolve("STATE.md");
      if (!Files.isRegularFile(statePath))
        continue;

      // Skip if matches exclude pattern
      if (!options.excludePattern().isEmpty() && matchesGlob(issueName, options.excludePattern()))
      {
        excludedCount.incrementAndGet();
        continue;
      }

      // Read STATE.md once and reuse across all checks to avoid repeated I/O
      List<String> stateLines;
      try
      {
        stateLines = readFileLines(statePath);
      }
      catch (IOException _)
      {
        continue;
      }

      String status;
      try
      {
        status = getIssueStatus(stateLines, statePath);
      }
      catch (IOException _)
      {
        continue;
      }

      if (!status.equals("open") && !status.equals("in-progress"))
        continue;

      // Skip decomposed parent tasks with open sub-issues
      if (isDecomposedParent(stateLines) && !allSubissuesClosed(statePath))
        continue;

      String issueId = buildIssueId(major, minor, patch, issueName);

      // Check dependencies
      List<String> dependencies = getDependencies(stateLines);
      List<String> blockingDependencies = getBlockingDependencies(dependencies);
      if (!blockingDependencies.isEmpty())
        continue;

      // Check post-condition gate if this is a post-condition issue (applies at minor dir level)
      if (!options.overridePostconditions() && isPostconditionIssue(minorDir, issueName) &&
        !postconditionsSatisfied(minorDir))
        continue;

      // Check for existing worktree
      if (Files.isDirectory(getWorktreePath(issueId)))
        continue;

      // Try to acquire lock
      if (!options.sessionId().isEmpty())
      {
        IssueLock.LockResult lockResult = issueLock.acquire(issueId, options.sessionId(), "");
        if (!(lockResult instanceof IssueLock.LockResult.Acquired))
          continue;
      }

      return new DiscoveryResult.Found(issueId, major, minor, patch, issueName, issueDir.toString(),
        scopeName);
    }

    return null;
  }

  /**
   * Reads all lines from a file, throwing if the file does not exist.
   *
   * @param path the path to the file
   * @return the list of lines
   * @throws IOException if the file does not exist or reading fails
   */
  private List<String> readFileLines(Path path) throws IOException
  {
    if (!Files.isRegularFile(path))
      throw new IOException("File not found: " + path);
    return Files.readAllLines(path);
  }

  /**
   * Reads and validates the status from a STATE.md file.
   *
   * @param statePath the path to the STATE.md file
   * @return the normalized status string
   * @throws IOException if reading the file fails, the status field is missing, or the status is invalid
   */
  private String getIssueStatus(Path statePath) throws IOException
  {
    List<String> lines = readFileLines(statePath);
    return getIssueStatus(lines, statePath);
  }

  /**
   * Reads and validates the status from pre-read STATE.md lines.
   *
   * @param lines the lines already read from the STATE.md file
   * @param statePath the path to the STATE.md file (used in error messages only)
   * @return the normalized status string
   * @throws IOException if the status field is missing or the status is invalid
   */
  private String getIssueStatus(List<String> lines, Path statePath) throws IOException
  {
    String rawStatus = null;
    for (String line : lines)
    {
      if (line.startsWith("- **Status:**"))
      {
        rawStatus = line.substring("- **Status:**".length()).strip();
        break;
      }
    }

    if (rawStatus == null)
      throw new IOException("Status field missing in " + statePath +
        ". STATE.md must contain a '- **Status:**' line.");

    // Normalize aliases to canonical values
    String status;
    switch (rawStatus)
    {
      case "pending" -> status = "open";
      case "completed", "complete", "done" -> status = "closed";
      case "in_progress", "active" -> status = "in-progress";
      default -> status = rawStatus;
    }

    // Validate against allowed status values
    switch (status)
    {
      case "open", "in-progress", "closed", "blocked" ->
      {
        // Valid
      }
      default -> throw new IOException("Unknown status '" + status + "' in " + statePath +
        ". Valid values: open, in-progress, closed, blocked");
    }

    return status;
  }

  /**
   * Checks if pre-read STATE.md lines describe a decomposed parent task.
   *
   * @param lines the lines already read from the STATE.md file
   * @return true if the lines contain a "## Decomposed Into" section
   */
  private boolean isDecomposedParent(List<String> lines)
  {
    for (String line : lines)
    {
      if (DECOMPOSED_INTO_PATTERN.matcher(line).matches())
        return true;
    }
    return false;
  }

  /**
   * Checks if all sub-issues of a decomposed parent task are closed.
   *
   * @param statePath the path to the parent's STATE.md file
   * @return true if all sub-issues are closed (or no sub-issues listed)
   * @throws IOException if file operations fail
   */
  private boolean allSubissuesClosed(Path statePath) throws IOException
  {
    List<String> lines = Files.readAllLines(statePath);

    // Extract sub-issue names from "## Decomposed Into" section
    List<String> subissueNames = new ArrayList<>();
    boolean inDecomposedSection = false;
    for (String line : lines)
    {
      if (DECOMPOSED_INTO_PATTERN.matcher(line).matches())
      {
        inDecomposedSection = true;
        continue;
      }
      if (inDecomposedSection)
      {
        // Stop at next section header
        if (NEXT_SECTION_PATTERN.matcher(line).matches())
          break;
        Matcher itemMatcher = SUBISSUE_ITEM_PATTERN.matcher(line);
        if (itemMatcher.find())
        {
          String name = itemMatcher.group(1).strip().replaceAll("[()]", "");
          if (!name.isEmpty())
            subissueNames.add(name);
        }
      }
    }

    if (subissueNames.isEmpty())
      return true;

    // Check each sub-issue
    Path parentVersionDir = statePath.getParent().getParent();
    if (parentVersionDir == null)
      return false;

    for (String subissueName : subissueNames)
    {
      // Skip malformed sub-issue references that could cause path traversal
      if (!BARE_NAME_PATTERN.matcher(subissueName).matches())
        continue;
      Path subissueStatePath = parentVersionDir.resolve(subissueName).resolve("STATE.md");
      if (!Files.isRegularFile(subissueStatePath))
        return false;
      try
      {
        String subStatus = getIssueStatus(subissueStatePath);
        if (!"closed".equals(subStatus))
          return false;
      }
      catch (IOException _)
      {
        return false;
      }
    }

    return true;
  }

  /**
   * Parses the dependencies list from a STATE.md file.
   *
   * @param statePath the path to the STATE.md file
   * @return list of dependency issue IDs, empty if none
   * @throws IOException if reading the file fails
   */
  private List<String> getDependencies(Path statePath) throws IOException
  {
    if (!Files.isRegularFile(statePath))
      return Collections.emptyList();

    List<String> lines = Files.readAllLines(statePath);
    return getDependencies(lines);
  }

  /**
   * Parses the dependencies list from pre-read STATE.md lines.
   *
   * @param lines the lines already read from the STATE.md file
   * @return list of dependency issue IDs, empty if none
   */
  private List<String> getDependencies(List<String> lines)
  {
    for (String line : lines)
    {
      if (line.startsWith("- **Dependencies:**"))
      {
        String depsContent = line.substring("- **Dependencies:**".length()).strip();

        // Check for empty dependencies
        if (depsContent.equals("[]") || depsContent.equalsIgnoreCase("none") ||
          depsContent.isEmpty())
          return Collections.emptyList();

        // Extract from array notation [dep1, dep2]
        if (depsContent.startsWith("[") && depsContent.contains("]"))
        {
          String inner = depsContent.substring(1, depsContent.lastIndexOf(']'));
          if (inner.isBlank())
            return Collections.emptyList();
          List<String> deps = new ArrayList<>();
          for (String part : inner.split(","))
          {
            String dep = part.strip().replaceAll("^\"|\"$", "");
            if (!dep.isEmpty())
              deps.add(dep);
          }
          return deps;
        }

        return Collections.emptyList();
      }
    }

    return Collections.emptyList();
  }

  /**
   * Checks which of the provided dependencies are not satisfied (not closed).
   *
   * @param dependencies list of dependency issue IDs
   * @return list of unsatisfied dependency IDs
   * @throws IOException if file operations fail
   */
  private List<String> getBlockingDependencies(List<String> dependencies) throws IOException
  {
    List<String> blocking = new ArrayList<>();
    for (String dep : dependencies)
    {
      if (!isDependencySatisfied(dep))
        blocking.add(dep);
    }
    return blocking;
  }

  /**
   * Checks if a specific dependency issue is satisfied (closed).
   * <p>
   * Uses a two-step search strategy: first attempts a direct path lookup using the qualified issue ID
   * (O(1)), then falls back to a {@link Files#walk} scan of all issue directories if the direct path
   * does not exist or the dependency name is not fully-qualified.
   *
   * @param depName the dependency issue ID (may be fully-qualified like {@code 2.1-fix-bug})
   * @return true if the dependency is closed
   * @throws IOException if file operations fail
   */
  private boolean isDependencySatisfied(String depName) throws IOException
  {
    // Search for the dependency issue
    Path depStatePath = null;

    // Try version-qualified lookup first (O(1)): 2.1-dep-name -> v2/v2.1/dep-name
    Matcher matcher = QUALIFIED_ISSUE_ID_PATTERN.matcher(depName);
    if (matcher.matches())
    {
      String depMajor = matcher.group(1);
      String depMinor;
      if (matcher.group(2) != null)
        depMinor = matcher.group(2);
      else
        depMinor = "";
      String depPatch;
      if (matcher.group(3) != null)
        depPatch = matcher.group(3);
      else
        depPatch = "";
      String depIssueName = matcher.group(4);
      Path directPath = resolveVersionDir(depMajor, depMinor, depPatch).
        resolve(depIssueName).
        resolve("STATE.md");
      if (Files.isRegularFile(directPath))
        depStatePath = directPath;
    }

    // Fall back to searching all issue directories
    if (depStatePath == null && Files.isDirectory(issuesDir))
    {
      // Only search if depName is a valid bare issue name (no path traversal)
      if (!BARE_NAME_PATTERN.matcher(depName).matches() &&
        !QUALIFIED_ISSUE_ID_PATTERN.matcher(depName).matches())
        return false;
      try (Stream<Path> stream = Files.walk(issuesDir, 4))
      {
        depStatePath = stream.
          filter(p -> p.getFileName().toString().equals("STATE.md")).
          filter(p ->
          {
            Path parentDir = p.getParent();
            return parentDir != null && parentDir.getFileName().toString().equals(depName);
          }).
          findFirst().
          orElse(null);
      }
    }

    if (depStatePath == null)
      return false;

    try
    {
      String status = getIssueStatus(depStatePath);
      return "closed".equals(status);
    }
    catch (IOException _)
    {
      return false;
    }
  }

  /**
   * Checks if a specific issue is a post-condition issue for its version.
   *
   * @param minorDir the minor version directory
   * @param issueName the bare issue name
   * @return true if the issue is listed as a post-condition issue in the version's PLAN.md
   * @throws IOException if file operations fail
   */
  private boolean isPostconditionIssue(Path minorDir, String issueName) throws IOException
  {
    return parsePostconditionIssues(minorDir).contains(issueName);
  }

  /**
   * Checks if post-conditions are satisfied for a version: all non-post-condition issues must be closed.
   *
   * @param minorDir the minor version directory
   * @return true if all non-post-condition issues are closed
   * @throws IOException if file operations fail
   */
  private boolean postconditionsSatisfied(Path minorDir) throws IOException
  {
    List<String> postconditionIssues = parsePostconditionIssues(minorDir);

    // Check all non-post-condition issues in the version
    for (Path issueDir : listIssueDirs(minorDir))
    {
      String dirName = issueDir.getFileName().toString();
      // Skip post-condition issues
      if (postconditionIssues.contains(dirName))
        continue;

      Path statePath = issueDir.resolve("STATE.md");
      if (!Files.isRegularFile(statePath))
        return false;

      try
      {
        String status = getIssueStatus(statePath);
        if (!"closed".equals(status))
          return false;
      }
      catch (IOException _)
      {
        return false;
      }
    }

    return true;
  }

  /**
   * Resolves the path to a version directory for major-only, major.minor, or major.minor.patch versions.
   * <p>
   * When {@code minor} is empty, resolves to the major directory (e.g., {@code .../issues/v2}).
   * When {@code minor} is present, resolves to the minor directory (e.g., {@code .../issues/v2/v2.1}).
   * When {@code patch} is also present, resolves to the patch directory (e.g.,
   * {@code .../issues/v2/v2.1/v2.1.3}).
   *
   * @param major the major version number (e.g., {@code "2"})
   * @param minor the minor version number (e.g., {@code "1"}), or empty string for major-only
   * @param patch the patch version number (e.g., {@code "3"}), or empty string for no patch
   * @return the path to the version directory
   */
  private Path resolveVersionDir(String major, String minor, String patch)
  {
    Path majorDir = issuesDir.resolve("v" + major);
    if (minor.isEmpty())
      return majorDir;
    Path minorDir = majorDir.resolve("v" + major + "." + minor);
    if (patch.isEmpty())
      return minorDir;
    return minorDir.resolve("v" + major + "." + minor + "." + patch);
  }

  /**
   * Resolves the path to a minor version directory.
   *
   * @param major the major version number (e.g., {@code "2"})
   * @param minor the minor version number (e.g., {@code "1"})
   * @return the path to the minor version directory (e.g., {@code .../issues/v2/v2.1})
   */
  private Path resolveVersionDir(String major, String minor)
  {
    return resolveVersionDir(major, minor, "");
  }

  /**
   * Constructs a fully-qualified issue ID from its components.
   * <p>
   * When {@code minor} is empty, constructs a major-only ID (e.g., {@code 2-fix-bug}).
   * When {@code minor} is present but {@code patch} is empty, constructs a major.minor ID (e.g.,
   * {@code 2.1-fix-bug}).
   * When both {@code minor} and {@code patch} are present, constructs a full ID (e.g.,
   * {@code 2.1.3-fix-bug}).
   *
   * @param major the major version number (e.g., {@code "2"})
   * @param minor the minor version number (e.g., {@code "1"}), or empty string for major-only
   * @param patch the patch version number (e.g., {@code "3"}), or empty string if not patch-level
   * @param issueName the bare issue name (e.g., {@code "fix-bug"})
   * @return the fully-qualified issue ID
   */
  private String buildIssueId(String major, String minor, String patch, String issueName)
  {
    if (minor.isEmpty())
      return major + "-" + issueName;
    if (patch.isEmpty())
      return major + "." + minor + "-" + issueName;
    return major + "." + minor + "." + patch + "-" + issueName;
  }

  /**
   * Returns the path to the worktree directory for a given issue ID.
   *
   * @param issueId the fully-qualified issue ID (e.g., {@code "2.1-fix-bug"})
   * @return the path to the worktree directory
   */
  private Path getWorktreePath(String issueId)
  {
    return projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(issueId);
  }

  /**
   * Parses post-condition issue names from a version's PLAN.md file.
   *
   * @param minorDir the minor version directory containing the PLAN.md
   * @return list of bare issue names that are post-condition issues, empty if no PLAN.md or no such issues
   * @throws IOException if reading the file fails
   */
  private List<String> parsePostconditionIssues(Path minorDir) throws IOException
  {
    Path planPath = minorDir.resolve("PLAN.md");
    if (!Files.isRegularFile(planPath))
      return Collections.emptyList();

    List<String> postconditionIssues = new ArrayList<>();
    List<String> lines = Files.readAllLines(planPath);
    for (String line : lines)
    {
      Matcher matcher = EXIT_ISSUE_PATTERN.matcher(line.strip());
      if (matcher.matches())
        postconditionIssues.add(matcher.group(1).strip());
    }
    return postconditionIssues;
  }

  /**
   * Lists major version directories under the issues directory, sorted by name.
   *
   * @return sorted list of major version directories, empty if none found
   * @throws IOException if listing the directory fails
   */
  private List<Path> listMajorDirs() throws IOException
  {
    if (!Files.isDirectory(issuesDir))
      return Collections.emptyList();
    try (Stream<Path> stream = Files.list(issuesDir))
    {
      return stream.
        filter(Files::isDirectory).
        filter(d -> MAJOR_VERSION_PATTERN.matcher(d.getFileName().toString()).matches()).
        sorted().
        toList();
    }
  }

  /**
   * Lists minor version directories under a major version directory, sorted by name.
   *
   * @param majorDir the major version directory
   * @return sorted list of minor version directories, empty if none found
   * @throws IOException if listing the directory fails
   */
  private List<Path> listMinorDirs(Path majorDir) throws IOException
  {
    if (!Files.isDirectory(majorDir))
      return Collections.emptyList();
    try (Stream<Path> stream = Files.list(majorDir))
    {
      return stream.
        filter(Files::isDirectory).
        filter(d -> MINOR_VERSION_PATTERN.matcher(d.getFileName().toString()).matches()).
        sorted().
        toList();
    }
  }

  /**
   * Lists patch version directories under a minor version directory, sorted by name.
   *
   * @param minorDir the minor version directory
   * @return sorted list of patch version directories, empty if none found
   * @throws IOException if listing the directory fails
   */
  private List<Path> listPatchDirs(Path minorDir) throws IOException
  {
    if (!Files.isDirectory(minorDir))
      return Collections.emptyList();
    try (Stream<Path> stream = Files.list(minorDir))
    {
      return stream.
        filter(Files::isDirectory).
        filter(d -> PATCH_VERSION_PATTERN.matcher(d.getFileName().toString()).matches()).
        sorted().
        toList();
    }
  }

  /**
   * Lists issue directories (non-version directories) under a minor version directory, sorted by name.
   *
   * @param minorDir the minor version directory
   * @return sorted list of issue directories, empty if none found
   * @throws IOException if listing the directory fails
   */
  private List<Path> listIssueDirs(Path minorDir) throws IOException
  {
    if (!Files.isDirectory(minorDir))
      return Collections.emptyList();
    try (Stream<Path> stream = Files.list(minorDir))
    {
      return stream.
        filter(Files::isDirectory).
        filter(d -> !VERSION_DIR_PATTERN.matcher(d.getFileName().toString()).matches()).
        sorted().
        toList();
    }
  }

  /**
   * Tests if an issue name matches a glob pattern.
   * <p>
   * Supports {@code *} as a wildcard matching any sequence of characters (not including path separators).
   *
   * @param issueName the issue name to test
   * @param pattern the glob pattern (may contain {@code *})
   * @return true if the issue name matches the pattern
   */
  private boolean matchesGlob(String issueName, String pattern)
  {
    if (!pattern.contains("*"))
      return issueName.equals(pattern);

    // Convert glob to regex
    StringBuilder regex = new StringBuilder("^");
    for (char c : pattern.toCharArray())
    {
      if (c == '*')
        regex.append(".*");
      else if ("\\^$.|+?()[]{}".indexOf(c) >= 0)
        regex.append('\\').append(c);
      else
        regex.append(c);
    }
    regex.append('$');

    return issueName.matches(regex.toString());
  }
}
