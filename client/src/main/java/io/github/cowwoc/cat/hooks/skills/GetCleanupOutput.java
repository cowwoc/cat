/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
  private static final int SECONDS_PER_DAY = 86_400;
  private static final int MIN_STALE_DAYS = 1;
  private static final int MAX_STALE_DAYS = 7;

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetCleanupOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetCleanupOutput(JvmScope scope)
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
   * @param age the lock age in seconds
   */
  public record Lock(String issueId, String session, int age)
  {
    /**
     * Creates a lock entry.
     *
     * @param issueId the task ID
     * @param session the session ID
     * @param age the lock age in seconds
     * @throws NullPointerException if issueId or session is null
     * @throws IllegalArgumentException if issueId or session is blank, or age is negative
     */
    public Lock
    {
      requireThat(issueId, "issueId").isNotBlank();
      requireThat(session, "session").isNotBlank();
      requireThat(age, "age").isNotNegative();
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
   */
  public record WorktreeToRemove(String path, String branch)
  {
    /**
     * Creates a worktree-to-remove entry.
     *
     * @param path the worktree path
     * @param branch the branch name
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if path or branch is blank
     */
    public WorktreeToRemove
    {
      requireThat(path, "path").isNotBlank();
      requireThat(branch, "branch").isNotBlank();
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
   * {@code scope.getClaudeProjectDir()}.
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
    Path projectDir = null;
    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--project-dir"))
      {
        if (i + 1 >= args.length)
          throw new IllegalArgumentException("Missing PATH argument for --project-dir");
        projectDir = Path.of(args[i + 1]);
        ++i;
      }
      else
        throw new IllegalArgumentException("Unknown argument: " + args[i]);
    }
    if (projectDir == null)
      projectDir = scope.getClaudeProjectDir();
    return gatherAndFormatSurveyOutput(projectDir);
  }

  /**
   * Gathers all survey data and generates formatted output.
   * <p>
   * This is the main entry point that orchestrates data gathering and display formatting.
   *
   * @param projectDir the project root directory
   * @return the formatted survey display
   * @throws NullPointerException if {@code projectDir} is null
   * @throws IOException if data gathering fails
   */
  public String gatherAndFormatSurveyOutput(Path projectDir) throws IOException
  {
    requireThat(projectDir, "projectDir").isNotNull();

    List<Worktree> worktrees = gatherWorktrees(projectDir);
    List<Lock> locks = gatherLocks(projectDir);
    List<String> branches = gatherBranches(projectDir);
    List<StaleRemote> staleRemotes = gatherStaleRemotes(projectDir);
    String contextFile = gatherContextFile(projectDir);

    return getSurveyOutput(worktrees, locks, branches, staleRemotes, contextFile);
  }

  /**
   * Gathers worktree information from git.
   * <p>
   * Parses {@code git worktree list --porcelain} output to extract worktree paths, branches, and states.
   *
   * @param projectDir the project root directory
   * @return list of worktrees (empty if git command fails)
   * @throws NullPointerException if {@code projectDir} is null
   */
  public List<Worktree> gatherWorktrees(Path projectDir)
  {
    requireThat(projectDir, "projectDir").isNotNull();

    ProcessRunner.Result result = ProcessRunner.run("git", "-C", projectDir.toString(),
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
   * Gathers lock information from .claude/cat/locks/ directory.
   * <p>
   * Reads JSON lock files and calculates age in seconds.
   *
   * @param projectDir the project root directory
   * @return list of locks (empty if directory does not exist or on error)
   * @throws NullPointerException if {@code projectDir} is null
   */
  public List<Lock> gatherLocks(Path projectDir)
  {
    requireThat(projectDir, "projectDir").isNotNull();

    try
    {
      IssueLock lockManager = new IssueLock(scope);
      List<IssueLock.LockListEntry> entries = lockManager.list();

      List<Lock> locks = new ArrayList<>();
      for (IssueLock.LockListEntry entry : entries)
      {
        int ageSeconds = (int) entry.ageSeconds();
        locks.add(new Lock(entry.issue(), entry.session(), ageSeconds));
      }
      return locks;
    }
    catch (IOException | IllegalArgumentException _)
    {
      return List.of();
    }
  }

  /**
   * Gathers CAT-related branches from git.
   * <p>
   * Filters branches matching patterns: release/, worktree, or version-prefixed (e.g., 2.1-).
   *
   * @param projectDir the project root directory
   * @return list of branch names (empty if git command fails)
   * @throws NullPointerException if {@code projectDir} is null
   */
  public List<String> gatherBranches(Path projectDir)
  {
    requireThat(projectDir, "projectDir").isNotNull();

    ProcessRunner.Result result = ProcessRunner.run("git", "-C", projectDir.toString(),
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
   * @param projectDir the project root directory
   * @return list of stale remotes (empty if git commands fail)
   * @throws NullPointerException if {@code projectDir} is null
   */
  public List<StaleRemote> gatherStaleRemotes(Path projectDir)
  {
    requireThat(projectDir, "projectDir").isNotNull();

    ProcessRunner.run("git", "-C", projectDir.toString(), "fetch", "--prune");

    ProcessRunner.Result branchResult = ProcessRunner.run("git", "-C", projectDir.toString(),
      "branch", "-r");

    if (branchResult.exitCode() != 0)
      return List.of();

    List<StaleRemote> staleRemotes = new ArrayList<>();
    long nowSeconds = System.currentTimeMillis() / 1000;
    String[] lines = branchResult.stdout().split("\n");

    for (String line : lines)
    {
      String branch = line.strip();
      if (!STALE_REMOTE_PATTERN.matcher(branch).find())
        continue;

      ProcessRunner.Result dateResult = ProcessRunner.run("git", "-C", projectDir.toString(),
        "log", "-1", "--format=%ct", branch);

      if (dateResult.exitCode() != 0)
        continue;

      try
      {
        long commitDate = Long.parseLong(dateResult.stdout().strip());
        long ageDays = (nowSeconds - commitDate) / SECONDS_PER_DAY;

        if (ageDays >= MIN_STALE_DAYS && ageDays <= MAX_STALE_DAYS)
        {
          ProcessRunner.Result authorResult = ProcessRunner.run("git", "-C", projectDir.toString(),
            "log", "-1", "--format=%an", branch);
          ProcessRunner.Result relativeResult = ProcessRunner.run("git", "-C", projectDir.toString(),
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
   * @param projectDir the project root directory
   * @return the context file path if it exists, otherwise null
   * @throws NullPointerException if {@code projectDir} is null
   */
  public String gatherContextFile(Path projectDir)
  {
    requireThat(projectDir, "projectDir").isNotNull();

    Path contextPath = projectDir.resolve(".cat-execution-context");
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
   * @return the formatted survey display
   * @throws NullPointerException if any list parameter is null
   */
  public String getSurveyOutput(List<Worktree> worktrees, List<Lock> locks,
                                List<String> branches, List<StaleRemote> staleRemotes,
                                String contextFile)
  {
    requireThat(worktrees, "worktrees").isNotNull();
    requireThat(locks, "locks").isNotNull();
    requireThat(branches, "branches").isNotNull();
    requireThat(staleRemotes, "staleRemotes").isNotNull();

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
      lockItems.add(lock.issueId() + ": session=" + session + ", age=" + lock.age() + "s");
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
                    " locks, " + branches.size() + " branches, " + staleRemotes.size() + " stale remotes";

    return finalBox + "\n" +
           "\n" +
           counts;
  }

  /**
   * Generate output display for plan phase.
   *
   * @param locksToRemove the list of lock IDs to remove
   * @param worktreesToRemove the list of worktrees to remove
   * @param branchesToRemove the list of branch names to remove
   * @param staleRemotes the list of stale remotes (for reporting)
   * @return the formatted plan display
   * @throws NullPointerException if any parameter is null
   */
  public String getPlanOutput(List<String> locksToRemove, List<WorktreeToRemove> worktreesToRemove,
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
      for (String lock : locksToRemove)
        contentItems.add("   " + DisplayUtils.BULLET + " " + lock);
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
        String wtLine = "   " + DisplayUtils.BULLET + " " + wt.path() + " " +
                        DisplayUtils.ARROW_RIGHT + " " + wt.branch();
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

    return finalBox + "\n" +
           "\n" +
           "Total items to remove: " + total + "\n" +
           "\n" +
           "Confirm cleanup? (yes/no)";
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
    String projectDirArg = "";
    String phase = "survey";
    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--project-dir"))
      {
        if (i + 1 >= args.length)
        {
          System.err.println("Error: --project-dir flag requires a PATH argument.");
          System.exit(1);
        }
        projectDirArg = args[i + 1];
        ++i;
      }
      else if (args[i].equals("--phase"))
      {
        if (i + 1 >= args.length)
        {
          System.err.println("Error: --phase flag requires a value (survey, plan, or verify).");
          System.exit(1);
        }
        phase = args[i + 1];
        ++i;
      }
    }

    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput output = new GetCleanupOutput(scope);
      String result;
      switch (phase)
      {
        case "survey" ->
        {
          Path projectDir;
          if (!projectDirArg.isEmpty())
            projectDir = Path.of(projectDirArg);
          else
            projectDir = scope.getClaudeProjectDir();
          result = output.gatherAndFormatSurveyOutput(projectDir);
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
        {
          System.err.println("Error: unknown --phase value '" + phase +
            "'. Expected: survey, plan, or verify.");
          System.exit(1);
          return;
        }
      }
      System.out.println(result);
    }
    catch (IOException e)
    {
      System.err.println("Error generating cleanup output: " + e.getMessage());
      System.exit(1);
    }
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

    List<String> locksToRemove = new ArrayList<>();
    for (JsonNode lock : context.path("locks_to_remove"))
      locksToRemove.add(lock.asString());

    List<WorktreeToRemove> worktreesToRemove = new ArrayList<>();
    for (JsonNode wt : context.path("worktrees_to_remove"))
      worktreesToRemove.add(new WorktreeToRemove(wt.path("path").asString(),
        wt.path("branch").asString()));

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
