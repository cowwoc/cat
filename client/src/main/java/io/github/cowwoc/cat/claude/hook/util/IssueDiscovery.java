/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import io.github.cowwoc.cat.claude.hook.Config;
import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.internal.SharedSecrets;
import io.github.cowwoc.cat.claude.hook.IssueStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Issue discovery for CAT workflow.
 * <p>
 * Java equivalent of {@code get-available-issues.sh}. Scans the {@code .cat/issues} directory for
 * open or in-progress issues, checks dependencies, and evaluates exit gates.
 * <p>
 * The search scope controls which version directories are searched:
 * <ul>
 *   <li>{@code all} - all major version directories under {@code .cat/issues}</li>
 *   <li>{@code major} - a specific major version (e.g., {@code v2})</li>
 *   <li>{@code minor} - a specific minor version (e.g., {@code v2.1})</li>
 *   <li>{@code issue} - a specific issue by fully-qualified ID (e.g., {@code 2.1-fix-bug})</li>
 *   <li>{@code bareName} - a bare issue name resolved against all versions</li>
 * </ul>
 */
public final class IssueDiscovery
{
  /**
   * Pattern matching a bare issue name like {@code fix-bug}.
   */
  static final Pattern BARE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");
  /**
   * Pattern matching a fully-qualified issue name like {@code 2.1-fix-bug} or {@code 2.1a-fix-bug}.
   * <p>
   * Group 1 captures the version prefix (e.g., {@code 2.1-} or {@code 2.1a-}).
   * Group 2 captures the bare name (e.g., {@code fix-bug}).
   */
  static final Pattern QUALIFIED_NAME_PATTERN =
    Pattern.compile("^(\\d+\\.\\d+[a-z]?-)(\\S+)$");
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
   * Pattern for issue entries in the exit section of a version plan.md.
   */
  private static final Pattern EXIT_ISSUE_PATTERN = Pattern.compile("^- \\[issue\\] (.+)$");

  static
  {
    SharedSecrets.setIssueDiscoveryAccess(IssueDiscovery::parseIssueStatus);
  }

  private final JvmScope scope;
  private final Path projectPath;
  private final Path issuesDir;
  private final JsonMapper mapper;
  /**
   * Cache mapping issue directory paths to their git creation timestamps (seconds since epoch).
   * <p>
   * Avoids redundant git subprocess calls when the same issue directory is queried multiple times.
   * Thread-safe to support concurrent writes from virtual threads.
   */
  private final Map<Path, Long> creationTimeCache = new ConcurrentHashMap<>();
  /**
   * Cache mapping version directory paths to their sorted issue directory listings.
   * <p>
   * Avoids redundant directory listings and git subprocess calls when the same version directory is
   * searched multiple times during a single discovery operation.
   * Thread-safe to support concurrent writes from virtual threads.
   */
  private final Map<Path, List<Path>> sortedDirCache = new ConcurrentHashMap<>();
  /**
   * Warnings accumulated during discovery operations.
   */
  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

  /**
   * Creates a new issue discovery instance.
   *
   * @param scope the JVM scope providing configuration and services
   * @throws IllegalArgumentException if the project directory is not a valid CAT project
   */
  public IssueDiscovery(JvmScope scope)
  {
    this.scope = scope;
    this.projectPath = scope.getProjectPath();
    Path catDir = scope.getCatDir();
    if (!Files.isDirectory(catDir))
    {
      throw new IllegalArgumentException("Not a CAT project: " + projectPath +
        " (no .cat directory)");
    }
    this.issuesDir = catDir.resolve("issues");
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Returns warnings accumulated during discovery operations.
   *
   * @return an unmodifiable copy of the warnings list
   */
  public List<String> getWarnings()
  {
    return List.copyOf(warnings);
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
     * Search a specific version (major, major.minor, or major.minor.patch).
     */
    VERSION,
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
     * @param createIndexJson true if the issue directory had no index.json file (index.json is absent and must
     *   be created)
     * @param isCorrupt true if plan.md is absent (the issue cannot be executed without it); both
     *   {@code isCorrupt} and {@code createIndexJson} can be true when neither file is present
     * @param isDecomposedComplete true if this is a decomposed parent with all sub-issues closed
     */
    record Found(String issueId, String major, String minor, String patch, String issueName,
      String issuePath, String scope, boolean createIndexJson, boolean isCorrupt,
      boolean isDecomposedComplete) implements DiscoveryResult
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
       * @param createIndexJson true if the issue directory had no index.json file (index.json is absent and
       *   must be created)
       * @param isCorrupt true if plan.md is absent (the issue cannot be executed without it); both
       *   {@code isCorrupt} and {@code createIndexJson} can be true when neither file is present
       * @param isDecomposedComplete true if this is a decomposed parent with all sub-issues closed
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
  public record SearchOptions(Scope scope, String target, String sessionId,
    String excludePattern, boolean overridePostconditions)
  {
    /**
     * Creates new search options.
     *
     * @param scope the search scope
     * @param target the target version or issue ID (may be empty)
     * @param sessionId the Claude session ID for lock acquisition (may be empty to skip locking)
     * @param excludePattern a glob-style pattern to exclude issues by name (may be empty)
     * @param overridePostconditions if true, skip post-condition evaluation
     * @throws NullPointerException if {@code scope}, {@code target}, {@code sessionId},
     *   or {@code excludePattern} are null
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

    // Handle bareName scope: resolve to a fully-qualified issue ID
    if (scope == Scope.BARE_NAME && !target.isEmpty())
    {
      if (!BARE_NAME_PATTERN.matcher(target).matches())
      {
        return new DiscoveryResult.DiscoveryError("Invalid bare issue name format: " + target);
      }
      String resolvedId = resolveBareNameToIssueId(target);
      if (resolvedId == null)
        return new DiscoveryResult.NotFound("bareName", "", 0);
      scope = Scope.ISSUE;
      target = resolvedId;
    }

    // Handle specific issue scope
    if (scope == Scope.ISSUE && !target.isEmpty())
      return findSpecificIssue(target);

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
      if (Files.isDirectory(majorIssueDir))
        matchingDirs.add(majorIssueDir);

      for (Path minorDir : listMinorDirs(majorDir))
      {
        // Check directly under the minor dir
        Path issueDir = minorDir.resolve(bareName);
        if (Files.isDirectory(issueDir))
          matchingDirs.add(issueDir);

        // Check under patch subdirs
        for (Path patchDir : listPatchDirs(minorDir))
        {
          Path patchIssueDir = patchDir.resolve(bareName);
          if (Files.isDirectory(patchIssueDir))
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
   * @return the discovery result
   * @throws IOException if file operations fail
   */
  private DiscoveryResult findSpecificIssue(String issueId) throws IOException
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

    Path indexPath = issueDir.resolve("index.json");
    Path issuePlanPath = issueDir.resolve("plan.md");
    boolean createIndexJson = !Files.isRegularFile(indexPath);
    boolean isCorrupt = !Files.isRegularFile(issuePlanPath);
    String indexContent;
    String status;
    if (createIndexJson)
    {
      indexContent = "";
      status = "open";
    }
    else
    {
      try
      {
        indexContent = Files.readString(indexPath);
      }
      catch (IOException _)
      {
        return new DiscoveryResult.NotExecutable(issueId,
          "Issue " + issueId + " has no readable status");
      }

      try
      {
        status = getIssueStatus(indexContent, indexPath, warnings, mapper);
      }
      catch (IOException _)
      {
        return new DiscoveryResult.NotExecutable(issueId,
          "Issue " + issueId + " has no readable status");
      }
    }

    if (!status.equals("open") && !status.equals("in-progress"))
    {
      if (status.equals("closed"))
        return new DiscoveryResult.AlreadyComplete(issueId);
      return new DiscoveryResult.NotExecutable(issueId,
        "Issue status is " + status + " (not open/in-progress)");
    }

    // Check if decomposed parent task with open sub-issues
    if (isDecomposedParent(indexContent, indexPath) && !allSubissuesClosed(indexPath))
      return new DiscoveryResult.Decomposed(issueId);

    // Check dependencies
    List<String> dependencies = getDependencies(indexContent, indexPath);
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

    boolean isDecomposedComplete = isDecomposedParent(indexContent, indexPath);
    return new DiscoveryResult.Found(issueId, major, minor, patch, issueName, issueDir.toString(),
      "issue", createIndexJson, isCorrupt, isDecomposedComplete);
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
      case VERSION ->
      {
        int firstDot = target.indexOf('.');
        if (firstDot < 0)
          return List.of(issuesDir.resolve("v" + target));
        int secondDot = target.indexOf('.', firstDot + 1);
        if (secondDot >= 0)
        {
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
      Path versionIndexPath = minorDir.resolve("index.json");
      if (Files.isRegularFile(versionIndexPath))
      {
        List<String> versionDeps = getDependencies(Files.readString(versionIndexPath), versionIndexPath);
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
    for (Path issueDir : listIssueDirsByAge(minorDir))
    {
      Path indexPath = issueDir.resolve("index.json");
      if (!Files.isRegularFile(indexPath))
        return true;
      try
      {
        String status = getIssueStatus(indexPath);
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
      for (Path issueDir : listIssueDirsByAge(patchDir))
      {
        Path indexPath = issueDir.resolve("index.json");
        if (!Files.isRegularFile(indexPath))
          return true;
        try
        {
          String status = getIssueStatus(indexPath);
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

    for (Path issueDir : listIssueDirsByAge(searchDir))
    {
      String issueName = issueDir.getFileName().toString();
      Path indexPath = issueDir.resolve("index.json");
      Path planPath = issueDir.resolve("plan.md");
      boolean indexJsonMissing = !Files.isRegularFile(indexPath);
      boolean isCorrupt = !Files.isRegularFile(planPath);

      // Read index.json once and reuse across all checks to avoid repeated I/O.
      // If index.json is absent, treat the issue as open with no content.
      String indexContent;
      String status;
      if (indexJsonMissing)
      {
        indexContent = "";
        status = "open";
      }
      else
      {
        try
        {
          indexContent = Files.readString(indexPath);
        }
        catch (IOException _)
        {
          continue;
        }

        try
        {
          status = getIssueStatus(indexContent, indexPath, warnings, mapper);
        }
        catch (IOException _)
        {
          continue;
        }
      }

      if (!status.equals("open") && !status.equals("in-progress"))
        continue;

      // Skip decomposed parent tasks with open sub-issues
      if (isDecomposedParent(indexContent, indexPath) && !allSubissuesClosed(indexPath))
        continue;

      String issueId = buildIssueId(major, minor, patch, issueName);

      // Skip if matches exclude pattern (matched against fully-qualified issue ID)
      if (!options.excludePattern().isEmpty() && GlobMatcher.matches(options.excludePattern(), issueId))
      {
        excludedCount.incrementAndGet();
        continue;
      }

      // Check dependencies
      List<String> dependencies = getDependencies(indexContent, indexPath);
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

      boolean isDecomposedComplete = isDecomposedParent(indexContent, indexPath);
      return new DiscoveryResult.Found(issueId, major, minor, patch, issueName, issueDir.toString(),
        scopeName, indexJsonMissing, isCorrupt, isDecomposedComplete);
    }

    return null;
  }

  /**
   * Reads and validates the status from an index.json file.
   *
   * @param indexPath the path to the index.json file
   * @return the normalized status string, or {@code "open"} if the status field is absent
   * @throws IOException if reading the file fails or the status value is non-canonical
   */
  private String getIssueStatus(Path indexPath) throws IOException
  {
    String content = Files.readString(indexPath);
    return getIssueStatus(content, indexPath, warnings, mapper);
  }

  /**
   * Reads and validates the status from pre-read index.json content.
   * <p>
   * When the status field is missing, returns {@code "open"}. If {@code warningsSink} is non-null,
   * appends a warning message describing the missing field; otherwise the warning is silently dropped.
   *
   * @param content the JSON content of the index.json file
   * @param indexPath the path to the index.json file (used in error messages only)
   * @param warningsSink a list to collect warnings into, or {@code null} to discard warnings
   * @param mapper the JSON mapper to use for parsing
   * @return the normalized status string, or {@code "open"} if the status field is absent
   * @throws IOException if the status value is present but non-canonical
   */
  private static String getIssueStatus(String content, Path indexPath,
    List<String> warningsSink, JsonMapper mapper) throws IOException
  {
    if (content.isBlank())
    {
      if (warningsSink != null)
        warningsSink.add("index.json is empty in " + indexPath + ". Treating as open.");
      return "open";
    }

    JsonNode root;
    try
    {
      root = mapper.readTree(content);
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + indexPath + ": " + e.getMessage(), e);
    }

    JsonNode statusNode = root.get("status");
    if (statusNode == null || !statusNode.isString())
    {
      if (warningsSink != null)
        warningsSink.add("Status field missing in " + indexPath +
          ". index.json has no 'status' field. Treating as open.");
      return "open";
    }

    String status = statusNode.asString().strip();

    // Validate against allowed canonical status values only (no aliases).
    // Legacy alias values must be migrated using plugin/migrations/2.1.sh before reading.
    if (IssueStatus.fromString(status) == null)
      throw new IOException("Unknown status '" + status + "' in " + indexPath +
        ". Valid values: " + IssueStatus.asCommaSeparated() + ".\n" +
        "Legacy statuses (pending, completed, complete, done, in_progress, active) must be migrated:\n" +
        "  Run: plugin/migrations/2.1.sh");

    return status;
  }

  /**
   * Parses index.json content for the status field. Used via SharedSecrets.
   *
   * @param content the JSON content of the index.json file
   * @param indexPath the path to the index.json file (used in error messages only)
   * @param mapper the JSON mapper to use for parsing
   * @return the validated status string, or {@code "open"} if absent
   * @throws IOException if the status value is present but non-canonical
   */
  private static String parseIssueStatus(String content, Path indexPath, JsonMapper mapper) throws IOException
  {
    return getIssueStatus(content, indexPath, null, mapper);
  }

  /**
   * Checks if pre-read index.json content describes a decomposed parent task.
   *
   * @param content the JSON content of the index.json file
   * @param indexPath the path to the index.json file (used in error messages)
   * @return true if the content has a non-empty {@code decomposedInto} array
   * @throws IOException if the content is not valid JSON
   */
  private boolean isDecomposedParent(String content, Path indexPath) throws IOException
  {
    if (content.isBlank())
      return false;
    try
    {
      JsonNode root = mapper.readTree(content);
      JsonNode decomposedInto = root.get("decomposedInto");
      return decomposedInto != null && decomposedInto.isArray() && !decomposedInto.isEmpty();
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + indexPath + ": " + e.getMessage(), e);
    }
  }

  /**
   * Checks if all sub-issues of a decomposed parent task are closed.
   *
   * @param indexPath the path to the parent's index.json file
   * @return true if all sub-issues are closed (or no sub-issues listed)
   * @throws IOException if file operations fail
   */
  private boolean allSubissuesClosed(Path indexPath) throws IOException
  {
    String content = Files.readString(indexPath);
    JsonNode root;
    try
    {
      root = mapper.readTree(content);
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + indexPath + ": " + e.getMessage(), e);
    }

    // Extract sub-issue names from decomposedInto JSON array
    List<String> subissueNames = new ArrayList<>();
    JsonNode decomposedInto = root.get("decomposedInto");
    if (decomposedInto == null || !decomposedInto.isArray())
      return true;
    for (JsonNode item : decomposedInto)
    {
      if (item.isString())
      {
        String name = item.asString().strip();
        if (!name.isEmpty())
          subissueNames.add(name);
      }
    }

    if (subissueNames.isEmpty())
      return true;

    // Check each sub-issue
    Path parentVersionDir = indexPath.getParent().getParent();
    if (parentVersionDir == null)
      return false;

    for (String subissueName : subissueNames)
    {
      // Resolve the directory name for the sub-issue.
      // Names are fully-qualified (e.g., "2.1-fix-bug") — strip the version prefix to get the
      // directory name (e.g., "fix-bug"). Skip bare names and malformed references.
      Matcher qualifiedMatcher = QUALIFIED_NAME_PATTERN.matcher(subissueName);
      if (!qualifiedMatcher.matches())
        continue;
      String dirName = qualifiedMatcher.group(2);
      Path subissueIndexPath = parentVersionDir.resolve(dirName).resolve("index.json");
      if (!Files.isRegularFile(subissueIndexPath))
        return false;
      String subStatus = getIssueStatus(subissueIndexPath);
      if (!"closed".equals(subStatus))
        return false;
    }

    return true;
  }

  /**
   * Parses the dependencies list from pre-read index.json content.
   *
   * @param content the JSON content of the index.json file
   * @param indexPath the path to the index.json file (used in error messages)
   * @return list of dependency issue IDs, empty if none
   * @throws IOException if the content is not valid JSON
   */
  private List<String> getDependencies(String content, Path indexPath) throws IOException
  {
    if (content.isBlank())
      return Collections.emptyList();
    try
    {
      JsonNode root = mapper.readTree(content);
      JsonNode depsNode = root.get("dependencies");
      if (depsNode == null || !depsNode.isArray())
        return Collections.emptyList();
      List<String> deps = new ArrayList<>();
      for (JsonNode item : depsNode)
      {
        if (item.isString())
        {
          String dep = item.asString().strip();
          if (!dep.isEmpty())
            deps.add(dep);
        }
      }
      return deps;
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse " + indexPath + ": " + e.getMessage(), e);
    }
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
        resolve("index.json");
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
          filter(p -> p.getFileName().toString().equals("index.json")).
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

    String status = getIssueStatus(depStatePath);
    return "closed".equals(status);
  }

  /**
   * Checks if a specific issue is a post-condition issue for its version.
   *
   * @param minorDir the minor version directory
   * @param issueName the bare issue name
   * @return true if the issue is listed as a post-condition issue in the version's plan.md
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
    for (Path issueDir : listIssueDirsByAge(minorDir))
    {
      String dirName = issueDir.getFileName().toString();
      // Skip post-condition issues
      if (postconditionIssues.contains(dirName))
        continue;

      Path indexPath = issueDir.resolve("index.json");
      if (!Files.isRegularFile(indexPath))
        return false;

      try
      {
        String status = getIssueStatus(indexPath);
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
   * Derives the relative {@code index.json} path for an issue from a branch name.
   * <p>
   * The branch name encodes the version and issue name in a structured format such as
   * {@code 2.1-my-issue}, {@code 2.1.3-my-issue}, or {@code 2-my-issue}. This method parses
   * the branch name using {@link #QUALIFIED_ISSUE_ID_PATTERN} and constructs the corresponding
   * relative path (e.g., {@code .cat/issues/v2/v2.1/my-issue/index.json}).
   *
   * @param branchName the git branch name
   * @return the relative {@code index.json} path, or {@code null} if the branch name does not
   *   match the expected format
   */
  public static String branchToIndexJsonPath(String branchName)
  {
    Matcher matcher = QUALIFIED_ISSUE_ID_PATTERN.matcher(branchName);
    if (!matcher.matches())
      return null;

    String major = matcher.group(1);
    String minor = matcher.group(2);
    String patch = matcher.group(3);
    String issueName = matcher.group(4);

    if (minor == null)
      return Config.CAT_DIR_NAME + "/issues/v" + major + "/" + issueName + "/index.json";
    if (patch == null)
    {
      return Config.CAT_DIR_NAME + "/issues/v" + major +
        "/v" + major + "." + minor +
        "/" + issueName + "/index.json";
    }
    return Config.CAT_DIR_NAME + "/issues/v" + major +
      "/v" + major + "." + minor +
      "/v" + major + "." + minor + "." + patch +
      "/" + issueName + "/index.json";
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
    return scope.getCatWorkPath().resolve("worktrees").resolve(issueId);
  }

  /**
   * Parses post-condition issue names from a version's plan.md file.
   *
   * @param minorDir the minor version directory containing the plan.md
   * @return list of bare issue names that are post-condition issues, empty if no plan.md or no such issues
   * @throws IOException if reading the file fails
   */
  private List<String> parsePostconditionIssues(Path minorDir) throws IOException
  {
    Path planPath = minorDir.resolve("plan.md");
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
   * Lists issue directories (non-version directories) under a version directory, sorted by creation
   * time (oldest first), with alphabetical ordering as a tiebreaker.
   * <p>
   * Closed issues are filtered out before invoking {@code git log} subprocesses, and the remaining
   * creation-time lookups are parallelized using virtual threads. Results are cached per version
   * directory so repeated calls with the same directory return immediately.
   *
   * @param versionDir the version directory
   * @return sorted list of open/in-progress issue directories (excluding closed issues), empty if
   *   none found
   * @throws IOException if listing the directory fails
   */
  public List<Path> listIssueDirsByAge(Path versionDir) throws IOException
  {
    List<Path> cached = sortedDirCache.get(versionDir);
    if (cached != null)
      return cached;
    if (!Files.isDirectory(versionDir))
    {
      sortedDirCache.put(versionDir, Collections.emptyList());
      return Collections.emptyList();
    }
    List<Path> allDirs;
    try (Stream<Path> stream = Files.list(versionDir))
    {
      allDirs = stream.
        filter(Files::isDirectory).
        filter(d -> !VERSION_DIR_PATTERN.matcher(d.getFileName().toString()).matches()).
        toList();
    }

    // Filter out closed issues before paying the git subprocess cost.
    // Issues without an index.json are treated as open.
    List<Path> openDirs = new ArrayList<>(allDirs.size());
    for (Path dir : allDirs)
    {
      Path indexPath = dir.resolve("index.json");
      if (!Files.isRegularFile(indexPath))
      {
        openDirs.add(dir);
        continue;
      }
      try
      {
        String status = getIssueStatus(indexPath);
        if ("open".equals(status) || "in-progress".equals(status))
          openDirs.add(dir);
        // closed (and any other non-open status) are silently excluded
      }
      catch (IOException e)
      {
        warnings.add("Failed to read status from " + indexPath + ": " + e.getMessage());
        // Unreadable status — include the dir so it is not silently lost
        openDirs.add(dir);
      }
    }

    // Parallelize git log calls for the remaining open/in-progress directories.
    Map<Path, Long> creationTimes = new ConcurrentHashMap<>(openDirs.size());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
    {
      List<Future<?>> futures = new ArrayList<>(openDirs.size());
      for (Path dir : openDirs)
      {
        futures.add(executor.submit(() ->
        {
          creationTimes.put(dir, getIssueCreationTime(dir));
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
          // Restore interrupt status and stop waiting for remaining futures.
          Thread.currentThread().interrupt();
          break;
        }
        catch (ExecutionException e)
        {
          Throwable cause;
          if (e.getCause() != null)
            cause = e.getCause();
          else
            cause = e;
          warnings.add("Failed to get creation time for issue directory: " + cause.getMessage());
          // Individual git failures are non-fatal; getIssueCreationTime already returns MAX_VALUE
          // on failure, so nothing to propagate here.
        }
      }
    }

    Comparator<Path> byAge = Comparator.comparingLong(
      dir -> creationTimes.getOrDefault(dir, Long.MAX_VALUE));
    List<Path> sorted = openDirs.stream().
      sorted(byAge.thenComparing(Comparator.naturalOrder())).
      toList();
    sortedDirCache.put(versionDir, sorted);
    return sorted;
  }

  /**
   * Lists all issue directories (non-version directories) under a version directory sorted by git
   * creation time (oldest first), with alphabetical ordering as a tiebreaker. Includes all issues
   * regardless of status (open, in-progress, closed, etc.).
   * <p>
   * Git log subprocess calls are parallelized using virtual threads. This method does not use a
   * cache because callers that need all issues (e.g., for display) are typically called once per
   * version directory, so caching provides less benefit than for the issue-discovery path.
   * <p>
   * Use this method when all issue directories are needed for display or summary purposes. Use
   * {@link #listIssueDirsByAge(Path)} when only open/in-progress issues are needed (e.g., for
   * issue discovery — it skips git calls for closed issues).
   *
   * @param versionDir the version directory
   * @return sorted list of all issue directories, empty if none found or the directory does not exist
   * @throws IOException if listing the directory fails
   */
  public List<Path> listAllIssueDirsByAge(Path versionDir) throws IOException
  {
    if (!Files.isDirectory(versionDir))
      return Collections.emptyList();
    List<Path> dirs;
    try (Stream<Path> stream = Files.list(versionDir))
    {
      dirs = stream.
        filter(Files::isDirectory).
        filter(d -> !VERSION_DIR_PATTERN.matcher(d.getFileName().toString()).matches()).
        toList();
    }

    Map<Path, Long> creationTimes = new ConcurrentHashMap<>(dirs.size());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
    {
      List<Future<?>> futures = new ArrayList<>(dirs.size());
      for (Path dir : dirs)
      {
        futures.add(executor.submit(() ->
        {
          creationTimes.put(dir, getIssueCreationTime(dir));
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
          Throwable cause;
          if (e.getCause() != null)
            cause = e.getCause();
          else
            cause = e;
          warnings.add("Failed to get creation time for issue directory: " + cause.getMessage());
          // Individual git failures are non-fatal; getIssueCreationTime returns MAX_VALUE on failure.
        }
      }
    }

    Comparator<Path> byAge = Comparator.comparingLong(
      dir -> creationTimes.getOrDefault(dir, Long.MAX_VALUE));
    return dirs.stream().
      sorted(byAge.thenComparing(Comparator.naturalOrder())).
      toList();
  }

  /**
   * Returns the Unix timestamp (seconds since epoch) of the oldest git commit that added the issue's
   * {@code index.json} file.
   * <p>
   * The {@code --reverse} flag ensures git log output is in oldest-first order, so the first line
   * is the original add commit.
   * <p>
   * Results are cached per issue directory to avoid redundant git subprocess calls.
   *
   * @param issueDir the issue directory
   * @return the Unix timestamp of the first commit that added {@code index.json}, or {@code Long.MAX_VALUE}
   *   if the timestamp cannot be determined (command failure, no output, non-git directory, or
   *   non-numeric output)
   * @throws NullPointerException if {@code issueDir} is null
   */
  private long getIssueCreationTime(Path issueDir)
  {
    Long cached = creationTimeCache.get(issueDir);
    if (cached != null)
      return cached;
    Path indexPath = issueDir.resolve("index.json");
    Path relativePath = projectPath.relativize(indexPath);
    String firstLine = ProcessRunner.runAndCaptureFirstLine(List.of("git", "-C", projectPath.toString(),
      "log", "--diff-filter=A", "--format=%at", "--reverse", "--", relativePath.toString()));
    long timestamp;
    if (firstLine == null || firstLine.isBlank())
    {
      timestamp = Long.MAX_VALUE;
    }
    else
    {
      try
      {
        timestamp = Long.parseLong(firstLine.strip());
      }
      catch (NumberFormatException _)
      {
        timestamp = Long.MAX_VALUE;
      }
    }
    creationTimeCache.put(issueDir, timestamp);
    return timestamp;
  }
}
