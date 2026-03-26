/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;

import static io.github.cowwoc.cat.hooks.Strings.block;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:cleanup skill.
 * <p>
 * Generates box displays for survey results, cleanup plan, and verification.
 * Also provides data gathering methods for worktrees, locks, branches, and stale remotes.
 */
public final class GetCleanupOutput implements SkillOutput
{
  private static final Pattern CAT_BRANCH_PATTERN = Pattern.compile("(release/|worktree|\\d+\\.\\d+-)");
  private static final Pattern STALE_REMOTE_PATTERN = Pattern.compile("origin/\\d+\\.\\d+-");
  private static final Pattern VERSION_DIR_PATTERN = Pattern.compile("^v\\d+(\\.\\d+){0,2}$");
  private static final Duration MIN_STALE_AGE = Duration.ofDays(1);
  private static final Duration MAX_STALE_AGE = Duration.ofDays(7);

  /**
   * The JVM scope for accessing shared services.
   */
  private final ClaudeTool scope;

  /**
   * Creates a GetCleanupOutput instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetCleanupOutput(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Represents a worktree entry for display.
   *
   * @param path the worktree path
   * @param branch the branch name
   * @param state the worktree state (may be empty)
   */
  public record Worktree(String path, String branch, String state)
  {
    /**
     * Creates a worktree entry.
     *
     * @param path the worktree path
     * @param branch the branch name
     * @param state the worktree state (may be empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if path or branch is blank
     */
    public Worktree
    {
      requireThat(path, "path").isNotBlank();
      requireThat(branch, "branch").isNotBlank();
      requireThat(state, "state").isNotNull();
    }
  }

  /**
   * Represents a lock entry for display.
   *
   * @param issueId the task ID
   * @param session the session ID
   * @param age the age of the branch's last commit
   */
  public record Lock(String issueId, String session, Duration age)
  {
    /**
     * Creates a lock entry.
     *
     * @param issueId the task ID
     * @param session the session ID
     * @param age the age of the branch's last commit
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if issueId or session is blank, or age is negative
     */
    public Lock
    {
      requireThat(issueId, "issueId").isNotBlank();
      requireThat(session, "session").isNotBlank();
      requireThat(age, "age").isGreaterThanOrEqualTo(Duration.ZERO);
    }
  }

  /**
   * Represents a stale remote for display.
   *
   * @param branch the branch name
   * @param author the author name
   * @param relative the relative time description
   * @param staleness the staleness description (for plan display)
   */
  public record StaleRemote(String branch, String author, String relative, String staleness)
  {
    /**
     * Creates a stale remote entry.
     *
     * @param branch the branch name
     * @param author the author name
     * @param relative the relative time description
     * @param staleness the staleness description (for plan display)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if branch, author, or relative is blank
     */
    public StaleRemote
    {
      requireThat(branch, "branch").isNotBlank();
      requireThat(author, "author").isNotBlank();
      requireThat(relative, "relative").isNotBlank();
      requireThat(staleness, "staleness").isNotNull();
    }
  }

  /**
   * Represents a worktree to remove for plan display.
   *
   * @param path the worktree path
   * @param branch the branch name
   * @param age the age of the branch's last commit
   */
  public record WorktreeToRemove(String path, String branch, Duration age)
  {
    /**
     * Creates a worktree-to-remove entry.
     *
     * @param path the worktree path
     * @param branch the branch name
     * @param age the age of the branch's last commit
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if path or branch is blank, or age is negative
     */
    public WorktreeToRemove
    {
      requireThat(path, "path").isNotBlank();
      requireThat(branch, "branch").isNotBlank();
      requireThat(age, "age").isGreaterThanOrEqualTo(Duration.ZERO);
    }
  }

  /**
   * Represents a corrupt issue directory entry for display.
   *
   * @param issueId the issue ID
   * @param issuePath the absolute path to the issue directory
   * @param reason human-readable description of why the directory is corrupt
   */
  public record CorruptIssue(String issueId, String issuePath, String reason)
  {
    /**
     * Creates a corrupt issue entry.
     *
     * @param issueId the issue ID
     * @param issuePath the absolute path to the issue directory
     * @param reason human-readable description of why the directory is corrupt
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if {@code issueId}, {@code issuePath}, or {@code reason} is blank
     */
    public CorruptIssue
    {
      requireThat(issueId, "issueId").isNotBlank();
      requireThat(issuePath, "issuePath").isNotBlank();
      requireThat(reason, "reason").isNotBlank();
    }
  }

  /**
   * Represents removed counts for verification display.
   *
   * @param locks the number of locks removed
   * @param worktrees the number of worktrees removed
   * @param branches the number of branches removed
   */
  public record RemovedCounts(int locks, int worktrees, int branches)
  {
    /**
     * Creates a removed counts entry.
     *
     * @param locks the number of locks removed
     * @param worktrees the number of worktrees removed
     * @param branches the number of branches removed
     * @throws IllegalArgumentException if any count is negative
     */
    public RemovedCounts
    {
      requireThat(locks, "locks").isNotNegative();
      requireThat(worktrees, "worktrees").isNotNegative();
      requireThat(branches, "branches").isNotNegative();
    }
  }

  /**
   * Generates the survey output for this skill.
   * <p>
   * Parses {@code --project-dir PATH} from {@code args} if present; otherwise falls back to
   * {@code scope.getProjectPath()}.
   *
   * @param args the arguments from the preprocessor directive
   * @return the formatted survey display
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if {@code --project-dir} flag is present but lacks a PATH value
   * @throws IOException              if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    Path projectPath = null;
    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--project-dir"))
      {
        if (i + 1 >= args.length)
          throw new IllegalArgumentException("Missing PATH argument for --project-dir");
        projectPath = Path.of(args[i + 1]);
        ++i;
      }
      else
        throw new IllegalArgumentException("Unknown argument: " + args[i]);
    }
    if (projectPath == null)
      projectPath = scope.getProjectPath();
    return gatherAndFormatSurveyOutput(projectPath);
  }

  /**
   * Gathers all survey data and generates formatted output.
   * <p>
   * This is the main entry point that orchestrates data gathering and display formatting.
   *
   * @param projectPath the project root directory
   * @return the formatted survey display
   * @throws NullPointerException if {@code projectPath} is null
   * @throws IOException if data gathering fails
   */
  public String gatherAndFormatSurveyOutput(Path projectPath) throws IOException
  {
    requireThat(projectPath, "projectPath").isNotNull();

    List<Worktree> worktrees = gatherWorktrees(projectPath);
    List<Lock> locks = gatherLocks(projectPath);
    List<String> branches = gatherBranches(projectPath);
    List<StaleRemote> staleRemotes = gatherStaleRemotes(projectPath);
    String contextFile = gatherContextFile(projectPath);
    List<CorruptIssue> corruptIssues = gatherCorruptIssues(projectPath);

    return getSurveyOutput(worktrees, locks, branches, staleRemotes, contextFile, corruptIssues);
  }

  /**
   * Scans all issue directories under {@code .cat/issues/} for corrupt conditions: plan.md absent,
   * index.json empty, or index.json containing invalid JSON.
   *
   * @param projectPath the project root directory
   * @return list of corrupt issue entries (empty if none found or directory does not exist)
   * @throws NullPointerException if {@code projectPath} is null
   */
  public List<CorruptIssue> gatherCorruptIssues(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();

    Path issuesRoot = projectPath.resolve(Config.CAT_DIR_NAME).resolve("issues");
    if (!Files.isDirectory(issuesRoot))
      return List.of();

    List<CorruptIssue> result = new ArrayList<>();
    scanForCorruptIssues(issuesRoot, result);
    return result;
  }

  /**
   * Recursively scans issue directories for corrupt conditions.
   *
   * @param dir the directory to scan
   * @param result the list to append corrupt issue entries to
   */
  private void scanForCorruptIssues(Path dir, List<CorruptIssue> result)
  {
    try (Stream<Path> entries = Files.list(dir))
    {
      for (Path entry : entries.toList())
      {
        if (!Files.isDirectory(entry))
          continue;

        String dirName = entry.getFileName().toString();
        if (VERSION_DIR_PATTERN.matcher(dirName).matches())
        {
          // Version directory (e.g. v2, v2.1) — recurse into it
          scanForCorruptIssues(entry, result);
          continue;
        }

        Path planMd = entry.resolve("plan.md");
        if (!Files.isRegularFile(planMd))
        {
          // Issue directory with plan.md absent — corrupt
          result.add(new CorruptIssue(dirName, entry.toString(), "plan.md is missing"));
          continue;
        }

        Path indexJson = entry.resolve("index.json");
        if (!Files.isRegularFile(indexJson))
          continue;

        // index.json exists — check that it is non-empty and contains a valid JSON object
        try
        {
          String content = Files.readString(indexJson);
          if (content.isBlank())
          {
            result.add(new CorruptIssue(dirName, entry.toString(), "index.json is empty"));
            continue;
          }
          JsonMapper mapper = scope.getJsonMapper();
          JsonNode node;
          try
          {
            node = mapper.readTree(content);
          }
          catch (JacksonException _)
          {
            result.add(new CorruptIssue(dirName, entry.toString(),
              "index.json does not contain a JSON object"));
            continue;
          }
          if (!node.isObject())
            result.add(new CorruptIssue(dirName, entry.toString(),
              "index.json does not contain a JSON object"));
        }
        catch (IOException e)
        {
          result.add(new CorruptIssue(dirName, entry.toString(),
            "index.json could not be read: " + e.getMessage()));
        }
      }
    }
    catch (IOException _)
    {
      // Skip unreadable directories
    }
  }

  /**
   * Gathers worktree information from git.
   * <p>
   * Parses {@code git worktree list --porcelain} output to extract worktree paths, branches, and states.
   *
   * @param projectPath the project root directory
   * @return list of worktrees (empty if git command fails)
   * @throws NullPointerException if {@code projectPath} is null
   */
  public List<Worktree> gatherWorktrees(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();

    ProcessRunner.Result result = ProcessRunner.run("git", "-C", projectPath.toString(),
      "worktree", "list", "--porcelain");

    if (result.exitCode() != 0)
      return List.of();

    return parseWorktreesPorcelain(result.stdout());
  }

  /**
   * Parses git worktree list --porcelain output.
   * <p>
   * Each worktree entry consists of multiple lines separated by blank lines.
   * Lines start with keywords: worktree, HEAD, branch, bare, detached.
   *
   * @param porcelainOutput the porcelain format output from git worktree list
   * @return list of parsed worktrees
   */
  public List<Worktree> parseWorktreesPorcelain(String porcelainOutput)
  {
    requireThat(porcelainOutput, "porcelainOutput").isNotNull();

    List<Worktree> worktrees = new ArrayList<>();
    String currentPath = null;
    String currentBranch = null;
    String currentState = "";

    String[] lines = porcelainOutput.split("\n");
    for (String line : lines)
    {
      String trimmed = line.strip();

      if (trimmed.isEmpty())
      {
        if (currentPath != null && currentBranch != null)
        {
          worktrees.add(new Worktree(currentPath, currentBranch, currentState));
          currentPath = null;
          currentBranch = null;
          currentState = "";
        }
        continue;
      }

      if (trimmed.startsWith("worktree "))
        currentPath = trimmed.substring(9);
      else if (trimmed.startsWith("branch "))
      {
        String fullBranch = trimmed.substring(7);
        String[] parts = fullBranch.split("/");
        currentBranch = parts[parts.length - 1];
      }
      else if (trimmed.equals("bare"))
        currentState = "bare";
      else if (trimmed.equals("detached"))
        currentState = "detached";
    }

    if (currentPath != null && currentBranch != null)
      worktrees.add(new Worktree(currentPath, currentBranch, currentState));

    return worktrees;
  }

  /**
   * Gathers lock information from the external CAT locks directory.
   * <p>
   * Reads lock files for issue and session IDs, then derives age as
   * {@code now - max(branch_last_commit_time, lock_file_mtime)} so that a recently-locked worktree
   * with old branch commits is not misclassified as stale.
   *
   * @param projectPath the project root directory
   * @return list of locks (empty if directory does not exist or on error)
   * @throws NullPointerException if {@code projectPath} is null
   */
  public List<Lock> gatherLocks(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();

    try
    {
      IssueLock lockManager = new IssueLock(scope);
      List<IssueLock.LockListEntry> entries = lockManager.list();
      Instant now = Instant.now();

      List<Lock> locks = new ArrayList<>();
      for (IssueLock.LockListEntry entry : entries)
      {
        Duration branchAge = getBranchAge(projectPath, entry.issue(), now);
        Path lockFile = lockManager.getLockFile(entry.issue());
        Duration lockFileAge = getLockFileAge(lockFile, now);
        Duration age;
        if (branchAge.compareTo(lockFileAge) <= 0)
          age = branchAge;
        else
          age = lockFileAge;
        locks.add(new Lock(entry.issue(), entry.session(), age));
      }
      return locks;
    }
    catch (IOException | IllegalArgumentException _)
    {
      return List.of();
    }
  }

  /**
   * Derives the age of a branch from its last commit time.
   *
   * @param projectPath the project root directory
   * @param branch the branch name
   * @param now the current instant
   * @return the duration since the branch's last commit, or {@link Duration#ZERO} on error
   */
  private static Duration getBranchAge(Path projectPath, String branch, Instant now)
  {
    ProcessRunner.Result result = ProcessRunner.run("git", "-C", projectPath.toString(),
      "log", "-1", "--format=%ct", branch);
    if (result.exitCode() != 0)
      return Duration.ZERO;
    try
    {
      Instant commitTime = Instant.ofEpochSecond(Long.parseLong(result.stdout().strip()));
      return Duration.between(commitTime, now);
    }
    catch (NumberFormatException _)
    {
      return Duration.ZERO;
    }
  }

  /**
   * Derives the age of a lock file from its last-modified time.
   *
   * @param lockFile the lock file path
   * @param now the current instant
   * @return the duration since the lock file was last modified, or {@link Duration#ZERO} if the file
   *   does not exist or an I/O error occurs
   */
  public static Duration getLockFileAge(Path lockFile, Instant now)
  {
    try
    {
      Instant mtime = Files.getLastModifiedTime(lockFile).toInstant();
      Duration age = Duration.between(mtime, now);
      if (age.isNegative())
        return Duration.ZERO;
      return age;
    }
    catch (IOException _)
    {
      return Duration.ZERO;
    }
  }

  /**
   * Gathers CAT-related branches from git.
   * <p>
   * Filters branches matching patterns: release/, worktree, or version-prefixed (e.g., 2.1-).
   *
   * @param projectPath the project root directory
   * @return list of branch names (empty if git command fails)
   * @throws NullPointerException if {@code projectPath} is null
   */
  public List<String> gatherBranches(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();

    ProcessRunner.Result result = ProcessRunner.run("git", "-C", projectPath.toString(),
      "branch", "-a");

    if (result.exitCode() != 0)
      return List.of();

    List<String> branches = new ArrayList<>();
    String[] lines = result.stdout().split("\n");

    for (String line : lines)
    {
      String branch = line.strip().replaceFirst("^\\* ", "");
      if (CAT_BRANCH_PATTERN.matcher(branch).find())
        branches.add(branch);
    }

    return branches;
  }

  /**
   * Gathers stale remote branches (idle for 1-7 days).
   * <p>
   * Fetches with --prune, then checks commit dates for origin/ branches matching version pattern.
   *
   * @param projectPath the project root directory
   * @return list of stale remotes (empty if git commands fail)
   * @throws NullPointerException if {@code projectPath} is null
   */
  public List<StaleRemote> gatherStaleRemotes(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();

    ProcessRunner.run("git", "-C", projectPath.toString(), "fetch", "--prune");

    ProcessRunner.Result branchResult = ProcessRunner.run("git", "-C", projectPath.toString(),
      "branch", "-r");

    if (branchResult.exitCode() != 0)
      return List.of();

    List<StaleRemote> staleRemotes = new ArrayList<>();
    Instant now = Instant.now();
    String[] lines = branchResult.stdout().split("\n");

    for (String line : lines)
    {
      String branch = line.strip();
      if (!STALE_REMOTE_PATTERN.matcher(branch).find())
        continue;

      ProcessRunner.Result dateResult = ProcessRunner.run("git", "-C", projectPath.toString(),
        "log", "-1", "--format=%ct", branch);

      if (dateResult.exitCode() != 0)
        continue;

      try
      {
        Instant commitTime = Instant.ofEpochSecond(Long.parseLong(dateResult.stdout().strip()));
        Duration age = Duration.between(commitTime, now);

        if (age.compareTo(MIN_STALE_AGE) >= 0 && age.compareTo(MAX_STALE_AGE) <= 0)
        {
          ProcessRunner.Result authorResult = ProcessRunner.run("git", "-C", projectPath.toString(),
            "log", "-1", "--format=%an", branch);
          ProcessRunner.Result relativeResult = ProcessRunner.run("git", "-C", projectPath.toString(),
            "log", "-1", "--format=%cr", branch);

          String author = "";
          if (authorResult.exitCode() == 0)
            author = authorResult.stdout().strip();

          String relative = "";
          if (relativeResult.exitCode() == 0)
            relative = relativeResult.stdout().strip();

          if (!author.isEmpty() && !relative.isEmpty())
            staleRemotes.add(new StaleRemote(branch, author, relative, ""));
        }
      }
      catch (NumberFormatException _)
      {
        // Skip invalid timestamp
      }
    }

    return staleRemotes;
  }

  /**
   * Checks for execution context file.
   *
   * @param projectPath the project root directory
   * @return the context file path if it exists, otherwise null
   * @throws NullPointerException if {@code projectPath} is null
   */
  public String gatherContextFile(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();

    Path contextPath = projectPath.resolve(".cat-execution-context");
    if (Files.exists(contextPath))
      return contextPath.toString();
    return null;
  }

  /**
   * Generate output display for survey phase.
   *
   * @param worktrees the list of worktrees found
   * @param locks the list of locks found
   * @param branches the list of branch names found
   * @param staleRemotes the list of stale remotes found
   * @param contextFile the context file path (may be null)
   * @param corruptIssues the list of corrupt issue directories found
   * @return the formatted survey display
   * @throws NullPointerException if any list parameter is null
   */
  public String getSurveyOutput(List<Worktree> worktrees, List<Lock> locks,
                                List<String> branches, List<StaleRemote> staleRemotes,
                                String contextFile, List<CorruptIssue> corruptIssues)
  {
    requireThat(worktrees, "worktrees").isNotNull();
    requireThat(locks, "locks").isNotNull();
    requireThat(branches, "branches").isNotNull();
    requireThat(staleRemotes, "staleRemotes").isNotNull();
    requireThat(corruptIssues, "corruptIssues").isNotNull();

    DisplayUtils display = scope.getDisplayUtils();
    List<String> allInnerBoxes = new ArrayList<>();

    // Worktrees inner box
    List<String> wtItems = new ArrayList<>();
    for (Worktree wt : worktrees)
    {
      if (wt.state() != null && !wt.state().isEmpty())
        wtItems.add(wt.path() + ": " + wt.branch() + " [" + wt.state() + "]");
      else
        wtItems.add(wt.path() + ": " + wt.branch());
    }
    if (wtItems.isEmpty())
      wtItems.add("None found");
    allInnerBoxes.addAll(display.buildInnerBox(DisplayUtils.EMOJI_FOLDER + " Worktrees", wtItems));
    allInnerBoxes.add("");

    // Locks inner box
    List<String> lockItems = new ArrayList<>();
    for (Lock lock : locks)
    {
      String session = lock.session();
      if (session != null && session.length() > 8)
        session = session.substring(0, 8);
      lockItems.add(lock.issueId() + ": session=" + session + ", age=" + formatAge(lock.age()));
    }
    if (lockItems.isEmpty())
      lockItems.add("None found");
    allInnerBoxes.addAll(display.buildInnerBox(DisplayUtils.EMOJI_LOCK + " Issue Locks", lockItems));
    allInnerBoxes.add("");

    // Branches inner box
    List<String> branchItems;
    if (branches.isEmpty())
      branchItems = List.of("None found");
    else
      branchItems = branches;
    allInnerBoxes.addAll(display.buildInnerBox(DisplayUtils.EMOJI_HERB + " CAT Branches", branchItems));
    allInnerBoxes.add("");

    // Stale remotes inner box
    List<String> remoteItems = new ArrayList<>();
    for (StaleRemote remote : staleRemotes)
      remoteItems.add(remote.branch() + ": " + remote.author() + ", " + remote.relative());
    if (remoteItems.isEmpty())
      remoteItems.add("None found");
    String staleRemotesHeader = DisplayUtils.EMOJI_HOURGLASS + " Stale Remotes (1-7 days)";
    allInnerBoxes.addAll(display.buildInnerBox(staleRemotesHeader, remoteItems));
    allInnerBoxes.add("");

    // Corrupt issues inner box
    List<String> corruptItems = new ArrayList<>();
    for (CorruptIssue corrupt : corruptIssues)
      corruptItems.add(corrupt.issueId() + ": " + corrupt.issuePath() + " (" + corrupt.reason() + ")");
    if (corruptItems.isEmpty())
      corruptItems.add("None found");
    allInnerBoxes.addAll(display.buildInnerBox("⚠ Corrupt Issue Directories", corruptItems));
    allInnerBoxes.add("");

    // Context file line
    if (contextFile != null && !contextFile.isEmpty())
      allInnerBoxes.add(DisplayUtils.EMOJI_MEMO + " Context: " + contextFile);
    else
      allInnerBoxes.add(DisplayUtils.EMOJI_MEMO + " Context: None");

    // Build outer box with header
    String header = DisplayUtils.EMOJI_MAGNIFIER + " Survey Results";
    String finalBox = display.buildHeaderBox(header, allInnerBoxes, List.of(), 50, DisplayUtils.HORIZONTAL_LINE + " ");

    // Summary counts
    String counts = "Found: " + worktrees.size() + " worktrees, " + locks.size() +
                    " locks, " + branches.size() + " branches, " + staleRemotes.size() +
                    " stale remotes, " + corruptIssues.size() + " corrupt issue directories";

    return finalBox + "\n" +
           "\n" +
           counts;
  }

  /**
   * Generate output display for plan phase.
   * <p>
   * Locks and worktrees are classified as stale (age &ge; {@link IssueLock#STALE_LOCK_THRESHOLD}) or recent
   * (age &lt; {@link IssueLock#STALE_LOCK_THRESHOLD}) so the user can choose which scope to clean up.
   *
   * @param locksToRemove the list of locks to remove (with session ID and age)
   * @param worktreesToRemove the list of worktrees to remove (with age)
   * @param branchesToRemove the list of branch names to remove
   * @param staleRemotes the list of stale remotes (for reporting)
   * @return the formatted plan display
   * @throws NullPointerException if any parameter is null
   */
  public String getPlanOutput(List<Lock> locksToRemove, List<WorktreeToRemove> worktreesToRemove,
                              List<String> branchesToRemove, List<StaleRemote> staleRemotes)
  {
    requireThat(locksToRemove, "locksToRemove").isNotNull();
    requireThat(worktreesToRemove, "worktreesToRemove").isNotNull();
    requireThat(branchesToRemove, "branchesToRemove").isNotNull();
    requireThat(staleRemotes, "staleRemotes").isNotNull();

    DisplayUtils display = scope.getDisplayUtils();
    List<String> contentItems = new ArrayList<>();

    // Locks section
    contentItems.add(DisplayUtils.EMOJI_LOCK + " Locks to Remove:");
    if (!locksToRemove.isEmpty())
    {
      for (Lock lock : locksToRemove)
      {
        String session = lock.session();
        if (session != null && session.length() > 8)
          session = session.substring(0, 8);
        String age = formatAge(lock.age());
        String classification;
        if (lock.age().compareTo(IssueLock.STALE_LOCK_THRESHOLD) >= 0)
          classification = "stale";
        else
          classification = "recent";
        contentItems.add("   " + DisplayUtils.BULLET + " " + lock.issueId() +
          " — " + age + ", session " + session + " [" + classification + "]");
      }
    }
    else
    {
      contentItems.add("   (none)");
    }
    contentItems.add("");

    // Worktrees section
    contentItems.add(DisplayUtils.EMOJI_FOLDER + " Worktrees to Remove:");
    if (!worktreesToRemove.isEmpty())
    {
      for (WorktreeToRemove wt : worktreesToRemove)
      {
        String agePart = " — " + formatAge(wt.age());
        String classification;
        if (wt.age().compareTo(IssueLock.STALE_LOCK_THRESHOLD) >= 0)
          classification = " [stale]";
        else
          classification = " [recent]";
        String wtLine = "   " + DisplayUtils.BULLET + " " + wt.path() + " " +
          DisplayUtils.ARROW_RIGHT + " " + wt.branch() + agePart + classification;
        contentItems.add(wtLine);
      }
    }
    else
    {
      contentItems.add("   (none)");
    }
    contentItems.add("");

    // Branches section
    contentItems.add(DisplayUtils.EMOJI_HERB + " Branches to Remove:");
    if (!branchesToRemove.isEmpty())
    {
      for (String branch : branchesToRemove)
        contentItems.add("   " + DisplayUtils.BULLET + " " + branch);
    }
    else
    {
      contentItems.add("   (none)");
    }
    contentItems.add("");

    // Stale remotes section (report only)
    contentItems.add(DisplayUtils.EMOJI_HOURGLASS + " Stale Remotes (report only):");
    if (!staleRemotes.isEmpty())
    {
      for (StaleRemote remote : staleRemotes)
        contentItems.add("   " + DisplayUtils.BULLET + " " + remote.branch() + ": " + remote.staleness());
    }
    else
    {
      contentItems.add("   (none)");
    }

    // Build outer box with header
    String header = DisplayUtils.EMOJI_BROOM + " Cleanup Plan";
    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), 50, DisplayUtils.HORIZONTAL_LINE + " ");

    // Count summary
    int total = locksToRemove.size() + worktreesToRemove.size() + branchesToRemove.size();

    Duration threshold = IssueLock.STALE_LOCK_THRESHOLD;
    long staleLocks = locksToRemove.stream().
      filter(l -> l.age().compareTo(threshold) >= 0).count();
    long staleWorktrees = worktreesToRemove.stream().
      filter(wt -> wt.age().compareTo(threshold) >= 0).count();
    long staleCount = staleLocks + staleWorktrees;
    long recentLocks = locksToRemove.stream().
      filter(l -> l.age().compareTo(threshold) < 0).count();
    long recentWorktrees = worktreesToRemove.stream().
      filter(wt -> wt.age().compareTo(threshold) < 0).count();
    long recentCount = recentLocks + recentWorktrees;

    return finalBox + "\n" +
           "\n" +
           "Total items to remove: " + total + " (" + staleCount + " stale, " + recentCount + " recent)\n" +
           "\n" +
           "Confirm cleanup?";
  }

  /**
   * Formats a duration into a human-readable string.
   * <p>
   * Returns hours and minutes for durations of one hour or more, otherwise seconds.
   *
   * @param age the duration (non-negative)
   * @return the formatted age string (e.g., "4h 23m", "326s")
   * @throws NullPointerException if {@code age} is null
   */
  private static String formatAge(Duration age)
  {
    long totalSeconds = age.toSeconds();
    if (totalSeconds >= 3600)
    {
      long hours = totalSeconds / 3600;
      long minutes = (totalSeconds % 3600) / 60;
      return hours + "h " + minutes + "m";
    }
    return totalSeconds + "s";
  }

  /**
   * Generate output display for verification phase.
   *
   * @param remainingWorktrees the list of remaining worktree paths
   * @param remainingBranches the list of remaining branch names
   * @param remainingLocks the list of remaining lock IDs
   * @param removedCounts the counts of items removed
   * @return the formatted verification display
   * @throws NullPointerException if any parameter is null
   */
  public String getVerifyOutput(List<String> remainingWorktrees, List<String> remainingBranches,
                                List<String> remainingLocks, RemovedCounts removedCounts)
  {
    requireThat(remainingWorktrees, "remainingWorktrees").isNotNull();
    requireThat(remainingBranches, "remainingBranches").isNotNull();
    requireThat(remainingLocks, "remainingLocks").isNotNull();
    requireThat(removedCounts, "removedCounts").isNotNull();

    DisplayUtils display = scope.getDisplayUtils();
    List<String> contentItems = new ArrayList<>();

    // Removed summary
    contentItems.add("Removed:");
    contentItems.add("   " + DisplayUtils.BULLET + " " + removedCounts.locks() + " lock(s)");
    contentItems.add("   " + DisplayUtils.BULLET + " " + removedCounts.worktrees() + " worktree(s)");
    contentItems.add("   " + DisplayUtils.BULLET + " " + removedCounts.branches() + " branch(es)");
    contentItems.add("");

    // Remaining worktrees
    contentItems.add(DisplayUtils.EMOJI_FOLDER + " Remaining Worktrees:");
    if (!remainingWorktrees.isEmpty())
    {
      for (String wt : remainingWorktrees)
        contentItems.add("   " + DisplayUtils.BULLET + " " + wt);
    }
    else
    {
      contentItems.add("   (none)");
    }
    contentItems.add("");

    // Remaining branches
    contentItems.add(DisplayUtils.EMOJI_HERB + " Remaining CAT Branches:");
    if (!remainingBranches.isEmpty())
    {
      for (String branch : remainingBranches)
        contentItems.add("   " + DisplayUtils.BULLET + " " + branch);
    }
    else
    {
      contentItems.add("   (none)");
    }
    contentItems.add("");

    // Remaining locks
    contentItems.add(DisplayUtils.EMOJI_LOCK + " Remaining Locks:");
    if (!remainingLocks.isEmpty())
    {
      for (String lock : remainingLocks)
        contentItems.add("   " + DisplayUtils.BULLET + " " + lock);
    }
    else
    {
      contentItems.add("   (none)");
    }

    // Build outer box with header
    String header = DisplayUtils.EMOJI_CHECKMARK + " Cleanup Complete";
    return display.buildHeaderBox(header, contentItems, List.of(), 50, DisplayUtils.HORIZONTAL_LINE + " ");
  }

  /**
   * Main entry point for the cleanup skill.
   * <p>
   * Handles three phases via command-line arguments:
   * <ul>
   *   <li><b>survey</b> (default): Gathers data from the filesystem and formats the survey box.
   *       Accepts {@code --project-dir PATH}; if omitted, falls back to {@code CLAUDE_PROJECT_DIR}
   *       via scope. This is the only phase that reads the environment automatically.</li>
   *   <li><b>plan</b>: Formats the cleanup plan box from JSON provided on stdin. All data is
   *       supplied by the caller (no filesystem reads). Accepts {@code --phase plan}.</li>
   *   <li><b>verify</b>: Formats the cleanup verification box from JSON provided on stdin. All
   *       data is supplied by the caller (no filesystem reads). Accepts {@code --phase verify}.</li>
   * </ul>
   * <p>
   * Usage:
   * <pre>
   *   get-cleanup-survey-output [--project-dir PATH]
   *   get-cleanup-survey-output --phase plan   &lt; plan.json
   *   get-cleanup-survey-output --phase verify &lt; verify.json
   * </pre>
   *
   * @param args command line arguments
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
        Logger log = LoggerFactory.getLogger(GetCleanupOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the cleanup output logic with a caller-provided output stream.
   * <p>
   * Reads stdin for plan and verify phases.
   *
   * @param scope the JVM scope
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException     if {@code scope}, {@code args} or {@code out} are null
   * @throws IllegalArgumentException if arguments are invalid
   * @throws IOException              if an I/O error occurs
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    String projectPathArg = "";
    String phase = "survey";
    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--project-dir"))
      {
        if (i + 1 >= args.length)
          throw new IllegalArgumentException("--project-dir flag requires a PATH argument.");
        projectPathArg = args[i + 1];
        ++i;
      }
      else if (args[i].equals("--phase"))
      {
        if (i + 1 >= args.length)
          throw new IllegalArgumentException("--phase flag requires a value (survey, plan, or verify).");
        phase = args[i + 1];
        ++i;
      }
      else
      {
        throw new IllegalArgumentException(
          "Unknown argument: " + args[i] + ". Valid arguments: --project-dir, --phase");
      }
    }

    GetCleanupOutput output = new GetCleanupOutput(scope);
    String result;
    switch (phase)
    {
      case "survey" ->
      {
        Path projectPath;
        if (!projectPathArg.isEmpty())
          projectPath = Path.of(projectPathArg);
        else
          projectPath = scope.getProjectPath();
        result = output.gatherAndFormatSurveyOutput(projectPath);
      }
      case "plan" ->
      {
        String json = readStdin();
        result = output.formatPlanFromJson(json);
      }
      case "verify" ->
      {
        String json = readStdin();
        result = output.formatVerifyFromJson(json);
      }
      default ->
        throw new IllegalArgumentException("Unknown --phase value '" + phase +
          "'. Expected: survey, plan, or verify.");
    }
    out.println(result);
  }

  /**
   * Reads all of stdin and returns it as a string.
   *
   * @return the stdin content
   * @throws IOException if an I/O error occurs
   */
  private static String readStdin() throws IOException
  {
    try (InputStream in = System.in)
    {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Formats the cleanup plan box from a JSON string.
   *
   * @param json the JSON input with plan phase data
   * @return the formatted plan output
   * @throws IOException if parsing fails
   * @throws NullPointerException if json is null
   */
  public String formatPlanFromJson(String json) throws IOException
  {
    requireThat(json, "json").isNotNull();
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode context = root.path("context");

    List<Lock> locksToRemove = new ArrayList<>();
    for (JsonNode lock : context.path("locks_to_remove"))
    {
      String issueId = lock.path("issue_id").asString();
      String session = lock.path("session").asString();
      int ageSeconds = lock.path("age_seconds").asInt(0);
      locksToRemove.add(new Lock(issueId, session, Duration.ofSeconds(ageSeconds)));
    }

    List<WorktreeToRemove> worktreesToRemove = new ArrayList<>();
    for (JsonNode wt : context.path("worktrees_to_remove"))
    {
      if (wt.path("age_seconds").isMissingNode())
        throw new IllegalArgumentException("Missing age_seconds for worktree: " + wt.path("path"));
      Duration age = Duration.ofSeconds(wt.path("age_seconds").asInt(0));
      worktreesToRemove.add(new WorktreeToRemove(wt.path("path").asString(),
        wt.path("branch").asString(), age));
    }

    List<String> branchesToRemove = new ArrayList<>();
    for (JsonNode branch : context.path("branches_to_remove"))
      branchesToRemove.add(branch.asString());

    List<StaleRemote> staleRemotes = new ArrayList<>();
    for (JsonNode remote : context.path("stale_remotes"))
    {
      staleRemotes.add(new StaleRemote(
        remote.path("branch").asString(),
        remote.path("author").asString(),
        remote.path("last_updated").asString(),
        remote.path("staleness").asString()));
    }

    return getPlanOutput(locksToRemove, worktreesToRemove, branchesToRemove, staleRemotes);
  }

  /**
   * Formats the cleanup verification box from a JSON string.
   *
   * @param json the JSON input with verify phase data
   * @return the formatted verify output
   * @throws IOException if parsing fails
   * @throws NullPointerException if json is null
   */
  public String formatVerifyFromJson(String json) throws IOException
  {
    requireThat(json, "json").isNotNull();
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(json);
    JsonNode context = root.path("context");

    JsonNode removedCountsNode = context.path("removed_counts");
    RemovedCounts removedCounts = new RemovedCounts(
      removedCountsNode.path("locks").asInt(0),
      removedCountsNode.path("worktrees").asInt(0),
      removedCountsNode.path("branches").asInt(0));

    List<String> remainingWorktrees = new ArrayList<>();
    for (JsonNode wt : context.path("remaining_worktrees"))
      remainingWorktrees.add(wt.asString());

    List<String> remainingBranches = new ArrayList<>();
    for (JsonNode branch : context.path("remaining_branches"))
      remainingBranches.add(branch.asString());

    List<String> remainingLocks = new ArrayList<>();
    for (JsonNode lock : context.path("remaining_locks"))
      remainingLocks.add(lock.asString());

    return getVerifyOutput(remainingWorktrees, remainingBranches, remainingLocks, removedCounts);
  }
}
