/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.licensing.LicenseResult;
import io.github.cowwoc.cat.hooks.licensing.LicenseValidator;
import io.github.cowwoc.cat.hooks.licensing.Tier;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Output generator for /cat:status skill.
 * <p>
 * Renders comprehensive project status display including:
 * - Overall progress bar with task completion percentage
 * - Active agents working on issues
 * - Major version sections with nested minor versions
 * - Task lists for active minors (in-progress, blocked, open, and recent completed tasks)
 * - Actionable footer showing current/next task commands
 */
public final class GetStatusOutput implements SkillOutput
{
  private static final int MAX_VISIBLE_COMPLETED = 5;
  private static final Set<String> VALID_STATUSES = Set.of("open", "in-progress", "closed", "blocked");

  private final DisplayUtils display;
  private final JvmScope scope;
  private Map<String, String> branchStatusCache;

  /**
   * Creates a GetStatusOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetStatusOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.display = scope.getDisplayUtils();
  }

  /**
   * Generates the complete status display for the project using the configured project directory.
   *
   * @return the formatted status display, or error message if CAT not initialized
   * @throws AssertionError if the project directory is not configured
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput() throws IOException
  {
    Path projectDir = scope.getClaudeProjectDir();

    Path catDir = projectDir.resolve(".claude/cat");
    if (!Files.isDirectory(catDir))
      return "No CAT project found. Run /cat:init to initialize.";

    Path pluginRoot = scope.getClaudePluginRoot();
    LicenseValidator validator = new LicenseValidator(pluginRoot, scope.getJsonMapper());
    LicenseResult licenseResult = validator.validate(projectDir);

    Path issuesDir = catDir.resolve("issues");

    if (licenseResult.tier().compareTo(Tier.TEAM) >= 0)
      branchStatusCache = loadBranchStatuses(projectDir);
    else
      branchStatusCache = Map.of();
    StatusData statusData = collectStatusData(issuesDir, catDir, licenseResult);

    if (statusData.error != null)
      return statusData.error;

    return generateStatusDisplay(statusData, catDir);
  }

  /**
   * Collects project status data from CAT directory structure.
   *
   * @param issuesDir the issues directory
   * @param catDir the CAT root directory
   * @param licenseResult the license validation result
   * @return the collected status data
   * @throws IOException if an I/O error occurs
   */
  private StatusData collectStatusData(Path issuesDir, Path catDir, LicenseResult licenseResult)
    throws IOException
  {
    if (!Files.isDirectory(issuesDir))
    {
      StatusData data = new StatusData();
      data.error = "No planning structure found. Run /cat:init to initialize.";
      return data;
    }

    StatusData data = new StatusData();

    Path projectFile = catDir.resolve("PROJECT.md");
    data.projectName = "Unknown Project";
    if (Files.exists(projectFile))
    {
      String content = Files.readString(projectFile);
      Pattern pattern = Pattern.compile("^# (.+)$", Pattern.MULTILINE);
      Matcher matcher = pattern.matcher(content);
      if (matcher.find())
        data.projectName = matcher.group(1);
    }

    Path roadmapFile = catDir.resolve("ROADMAP.md");
    String roadmapContent = "";
    if (Files.exists(roadmapFile))
      roadmapContent = Files.readString(roadmapFile);

    int totalCompleted = 0;
    int totalTasks = 0;
    String currentMinor = "";
    String inProgressTask = "";
    String nextTask = "";

    try (Stream<Path> majorStream = Files.list(issuesDir))
    {
      List<Path> majorDirs = majorStream.
        filter(Files::isDirectory).
        filter(p -> p.getFileName().toString().matches("v[0-9]+")).
        sorted().
        toList();

      for (Path majorDir : majorDirs)
      {
        String majorId = majorDir.getFileName().toString();
        String majorNum = majorId.substring(1);

        String majorName = "Version " + majorNum;
        Pattern versionPattern = Pattern.compile("^## Version " + Pattern.quote(majorNum) + ": (.+)$",
          Pattern.MULTILINE);
        Matcher versionMatcher = versionPattern.matcher(roadmapContent);
        if (versionMatcher.find())
        {
          String matched = versionMatcher.group(1);
          int parenIndex = matched.indexOf('(');
          if (parenIndex >= 0)
            majorName = matched.substring(0, parenIndex).trim();
          else
            majorName = matched.trim();
        }

        Path majorStateFile = majorDir.resolve("STATE.md");
        String majorStatus = "open";
        if (Files.exists(majorStateFile))
        {
          String content = Files.readString(majorStateFile);
          majorStatus = parseStatusFromContent(content);
        }

        MajorVersion major = new MajorVersion();
        major.id = majorId;
        major.name = majorName;
        major.status = majorStatus;
        data.majors.add(major);

        try (Stream<Path> minorStream = Files.list(majorDir))
        {
          List<Path> minorDirs = minorStream.
            filter(Files::isDirectory).
            filter(p -> p.getFileName().toString().matches("v[0-9]+\\.[0-9]+")).
            sorted(Comparator.comparing(p ->
            {
              String name = p.getFileName().toString().substring(1);
              String[] parts = name.split("\\.");
              return Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
            })).
            toList();

          for (Path minorDir : minorDirs)
          {
            String minorId = minorDir.getFileName().toString();
            String minorNum = minorId.substring(1);

            int localCompleted = 0;
            int localTotal = 0;
            String localInprog = "";
            List<Task> tasks = new ArrayList<>();
            Map<String, String> allTaskStatuses = new HashMap<>();

            try (Stream<Path> taskStream = Files.list(minorDir))
            {
              List<Path> taskDirs = taskStream.
                filter(Files::isDirectory).
                sorted().
                toList();

              // Two-pass loop is intentional for dependency resolution:
              // Pass 1: Collect all task statuses into allTaskStatuses map
              // Pass 2: Build task objects with blockedBy lists resolved from the map
              // This ensures dependencies can reference tasks defined later in the directory listing
              for (Path taskDir : taskDirs)
              {
                String taskName = taskDir.getFileName().toString();
                Path stateFile = taskDir.resolve("STATE.md");
                Path planFile = taskDir.resolve("PLAN.md");

                if (!Files.exists(stateFile) && !Files.exists(planFile))
                  continue;

                String status = getTaskStatus(stateFile, catDir, minorNum, taskName, licenseResult);
                allTaskStatuses.put(taskName, status);
              }

              TaskStats stats = buildMinorVersionTasks(taskDirs, catDir, minorNum, licenseResult,
                allTaskStatuses, tasks);
              localTotal = stats.total;
              localCompleted = stats.completed;
              localInprog = stats.inProgress;
            }

            String desc = "";
            Pattern minorPattern = Pattern.compile("^- \\*\\*" + Pattern.quote(minorNum) + ":\\*\\*\\s+([^(]+)",
              Pattern.MULTILINE);
            Matcher minorMatcher = minorPattern.matcher(roadmapContent);
            if (minorMatcher.find())
              desc = minorMatcher.group(1).trim();

            totalCompleted += localCompleted;
            totalTasks += localTotal;

            if (currentMinor.isEmpty())
            {
              if (!localInprog.isEmpty())
              {
                currentMinor = minorId;
                inProgressTask = localInprog;
              }
              else if (localCompleted < localTotal)
              {
                currentMinor = minorId;
                for (Task task : tasks)
                {
                  if (task.status.equals("open") && task.blockedBy.isEmpty())
                  {
                    nextTask = task.name;
                    break;
                  }
                }
                if (nextTask.isEmpty())
                {
                  for (Task task : tasks)
                  {
                    if (task.status.equals("open"))
                    {
                      nextTask = task.name;
                      break;
                    }
                  }
                }
              }
            }

            MinorVersion minor = new MinorVersion();
            minor.id = minorId;
            minor.major = majorId;
            minor.description = desc;
            minor.completed = localCompleted;
            minor.total = localTotal;
            minor.inProgress = localInprog;
            minor.tasks = tasks;
            data.minors.add(minor);
          }
        }
      }
    }

    if (totalTasks == 0)
      totalTasks = 1;
    int percent = totalCompleted * 100 / totalTasks;

    data.overallPercent = percent;
    data.overallCompleted = totalCompleted;
    data.overallTotal = totalTasks;
    data.currentMinor = currentMinor;
    data.inProgressTask = inProgressTask;
    data.nextTask = nextTask;

    return data;
  }

  /**
   * Gets task status from STATE.md file.
   * <p>
   * If STATE.md status is "open", checks for lock files (Indie tier) and git branches (Team tier)
   * to determine if the task is actually in-progress.
   *
   * @param stateFile the STATE.md file path
   * @param catDir the CAT directory (for lock file lookup)
   * @param minorNum the minor version number (e.g., "2.1")
   * @param taskName the task name
   * @param licenseResult the license validation result
   * @return the normalized status
   * @throws IOException if an I/O error occurs
   */
  private String getTaskStatus(Path stateFile, Path catDir, String minorNum, String taskName,
    LicenseResult licenseResult) throws IOException
  {
    if (!Files.exists(stateFile))
      return "open";

    String content = Files.readString(stateFile);
    String status = parseStatusFromContent(content);

    if (status.equals("open"))
    {
      String lockFileName = minorNum + "-" + taskName + ".lock";
      Path lockFile = catDir.resolve("locks").resolve(lockFileName);
      if (Files.exists(lockFile))
        return "in-progress";

      if (licenseResult.tier().compareTo(Tier.TEAM) >= 0)
      {
        Path issueRelPath = stateFile.getParent();
        Path projectRoot = catDir.getParent().getParent();
        String relPath = projectRoot.relativize(issueRelPath).toString();
        String branchStatus = branchStatusCache.get(relPath);
        if ("in-progress".equals(branchStatus))
          return "in-progress";
      }
    }

    return status;
  }

  /**
   * Parses the status field from STATE.md content.
   *
   * @param content the STATE.md file content
   * @return the normalized status, or "open" if not found or invalid
   */
  public String parseStatusFromContent(String content)
  {
    Pattern pattern = Pattern.compile("^- \\*\\*Status:\\*\\*\\s*(.+)$", Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(content);
    if (!matcher.find())
      return "open";

    String rawStatus = matcher.group(1).strip().toLowerCase(Locale.ROOT);
    if (VALID_STATUSES.contains(rawStatus))
      return rawStatus;
    return "open";
  }

  /**
   * Executes a git command and returns the output lines.
   * <p>
   * Handles process lifecycle, stream reading, and cleanup.
   *
   * @param projectDir the project root directory
   * @param args the git command arguments
   * @return list of output lines (empty list on failure)
   * @throws IOException if an I/O error occurs
   */
  private List<String> executeGitCommand(Path projectDir, String... args) throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder(args);
    pb.directory(projectDir.toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();
    List<String> lines = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)))
    {
      String line = reader.readLine();
      while (line != null)
      {
        lines.add(line);
        line = reader.readLine();
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        return List.of();

      return lines;
    }
    catch (InterruptedException _)
    {
      return List.of();
    }
    finally
    {
      process.destroy();
    }
  }

  /**
   * Gets task dependencies from STATE.md file.
   *
   * @param stateFile the STATE.md file path
   * @return the list of dependency issue IDs
   * @throws IOException if an I/O error occurs
   */
  private List<String> getTaskDependencies(Path stateFile) throws IOException
  {
    if (!Files.exists(stateFile))
      return List.of();

    String content = Files.readString(stateFile);

    Pattern sectionPattern = Pattern.compile("^## Dependencies\\s*\\n((?:- .+\\n?)+)", Pattern.MULTILINE);
    Matcher sectionMatcher = sectionPattern.matcher(content);
    if (sectionMatcher.find())
    {
      List<String> deps = new ArrayList<>();
      String section = sectionMatcher.group(1).strip();
      for (String line : section.split("\n"))
      {
        Pattern depPattern = Pattern.compile("^- ([a-zA-Z0-9_-]+)");
        Matcher depMatcher = depPattern.matcher(line);
        if (depMatcher.find())
        {
          String depName = depMatcher.group(1);
          if (!depName.equalsIgnoreCase("none"))
            deps.add(depName);
        }
      }
      return deps;
    }

    Pattern inlinePattern = Pattern.compile("^- \\*\\*Dependencies:\\*\\*\\s*\\[([^\\]]*)\\]",
      Pattern.MULTILINE);
    Matcher inlineMatcher = inlinePattern.matcher(content);
    if (inlineMatcher.find())
    {
      String depStr = inlineMatcher.group(1).strip();
      if (!depStr.isEmpty())
      {
        List<String> deps = new ArrayList<>();
        for (String dep : depStr.split(","))
        {
          String trimmed = dep.strip();
          if (!trimmed.isEmpty())
            deps.add(trimmed);
        }
        return deps;
      }
    }

    return List.of();
  }

  /**
   * Generates the complete status display from collected data.
   *
   * @param data the status data
   * @param catDir the CAT directory
   * @return the formatted display string
   * @throws IOException if an I/O error occurs
   */
  private String generateStatusDisplay(StatusData data, Path catDir) throws IOException
  {
    List<String> contentItems = new ArrayList<>();

    String progressBar = display.buildProgressBar(data.overallPercent);
    contentItems.add("📊 Overall: [" + progressBar + "] " + data.overallPercent + "% · " +
                     data.overallCompleted + "/" + data.overallTotal + " tasks");
    contentItems.add("");

    String currentSession = System.getenv("CLAUDE_SESSION_ID");
    if (currentSession == null)
      currentSession = "";
    List<Agent> agents = getActiveAgents(catDir, currentSession);

    if (!agents.isEmpty())
    {
      contentItems.add("🤖 Active Agents:");
      for (Agent agent : agents)
      {
        String ageStr = formatAge(agent.ageSeconds);
        String sessionStr = formatSessionId(agent.sessionId);
        contentItems.add("   • " + agent.issueId + " (session: " + sessionStr + ", " + ageStr + ")");
      }
      contentItems.add("");
    }

    for (MajorVersion major : data.majors)
    {
      List<MinorVersion> majorMinors = data.minors.stream().
        filter(m -> m.major.equals(major.id)).
        toList();

      boolean allComplete = true;
      if (!majorMinors.isEmpty())
      {
        allComplete = majorMinors.stream().
          allMatch(m -> m.completed == m.total && m.total > 0);
      }

      if (major.status.equals("closed") || major.status.equals("done"))
        allComplete = true;

      List<String> innerContent = new ArrayList<>();

      if (allComplete && !majorMinors.isEmpty())
      {
        innerContent.add(collapseCompletedMinors(majorMinors));
      }
      else
      {
        boolean firstMinor = true;
        for (MinorVersion minor : majorMinors)
        {
          if (!firstMinor)
            innerContent.add("");
          firstMinor = false;

          boolean isComplete = minor.completed == minor.total && minor.total > 0;
          boolean isActive = minor.id.equals(data.currentMinor);

          String emoji;
          if (isComplete)
            emoji = "☑️";
          else if (isActive)
            emoji = "🔄";
          else
            emoji = "🔳";

          String line;
          if (!minor.description.isEmpty())
            line = emoji + " " + minor.id + ": " + minor.description + " (" + minor.completed + "/" +
                   minor.total + ")";
          else
            line = emoji + " " + minor.id + ": (" + minor.completed + "/" + minor.total + ")";
          innerContent.add(line);

          if (isActive)
          {
            if (!minor.tasks.isEmpty())
              innerContent.add("");

            List<Task> completedTasks = new ArrayList<>();
            List<Task> nonCompletedTasks = new ArrayList<>();

            for (Task task : minor.tasks)
            {
              if (task.status.equals("closed") || task.status.equals("done"))
                completedTasks.add(task);
              else
                nonCompletedTasks.add(task);
            }

            completedTasks.sort(Comparator.comparingLong((Task t) -> t.mtime).reversed());

            for (Task task : nonCompletedTasks)
            {
              String taskEmoji;
              if (task.status.equals("in-progress") || task.status.equals("active") ||
                  task.status.equals("in_progress"))
                taskEmoji = "🔄";
              else if (!task.blockedBy.isEmpty())
                taskEmoji = "🚫";
              else
                taskEmoji = "🔳";

              if (!task.blockedBy.isEmpty())
              {
                String blockedStr = String.join(", ", task.blockedBy);
                innerContent.add("   " + taskEmoji + " " + task.name + " (blocked by: " + blockedStr + ")");
              }
              else
              {
                innerContent.add("   " + taskEmoji + " " + task.name);
              }
            }

            List<Task> visibleCompleted = completedTasks.stream().
              limit(MAX_VISIBLE_COMPLETED).
              toList();
            for (Task task : visibleCompleted)
              innerContent.add("   ☑️ " + task.name);

            int remainingCompleted = completedTasks.size() - visibleCompleted.size();
            if (remainingCompleted > 0)
              innerContent.add("   ☑️ ... and " + remainingCompleted + " more completed");

            if (!minor.tasks.isEmpty())
              innerContent.add("");
          }
        }
      }

      String header = "📦 " + major.id + ": " + major.name;
      List<String> innerBoxLines = display.buildInnerBox(header, innerContent);
      contentItems.addAll(innerBoxLines);
      contentItems.add("");
    }

    if (!data.inProgressTask.isEmpty())
    {
      contentItems.add("📋 Current: /cat:work " + data.currentMinor + "-" + data.inProgressTask);
    }
    else if (!data.nextTask.isEmpty())
    {
      contentItems.add("📋 Next: /cat:work " + data.currentMinor + "-" + data.nextTask);
    }
    else
    {
      contentItems.add("📋 No open tasks available");
    }

    int maxContentWidth = 0;
    for (String item : contentItems)
    {
      int width = display.displayWidth(item);
      if (width > maxContentWidth)
        maxContentWidth = width;
    }

    StringBuilder result = new StringBuilder();
    result.append(display.buildTopBorder(maxContentWidth)).append('\n');
    for (String item : contentItems)
      result.append(display.buildLine(item, maxContentWidth)).append('\n');
    result.append(display.buildBottomBorder(maxContentWidth));

    return result.toString();
  }

  /**
   * Collapses completed minors into a range string.
   *
   * @param minors the list of completed minors
   * @return the collapsed string
   */
  private String collapseCompletedMinors(List<MinorVersion> minors)
  {
    if (minors.isEmpty())
      return "";

    List<MinorVersion> sorted = new ArrayList<>(minors);
    sorted.sort(Comparator.comparing((MinorVersion m) ->
    {
      String name = m.id.substring(1);
      String[] parts = name.split("\\.");
      return Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
    }));

    String first = sorted.get(0).id;
    String last = sorted.get(sorted.size() - 1).id;
    int totalCompleted = sorted.stream().mapToInt(m -> m.completed).sum();
    int totalTasks = sorted.stream().mapToInt(m -> m.total).sum();

    if (first.equals(last))
      return "☑️ " + first + " (" + totalCompleted + "/" + totalTasks + ")";
    return "☑️ " + first + " - " + last + " (" + totalCompleted + "/" + totalTasks + ")";
  }

  /**
   * Loads the status of issues from git branches by scanning for STATE.md changes.
   * <p>
   * For each branch, finds STATE.md files that differ from the base branch,
   * reads their status, and builds a map of issue path to status.
   *
   * @param projectDir the project root directory
   * @return map from issue relative path to status on branch
   * @throws IOException if git command fails
   */
  private Map<String, String> loadBranchStatuses(Path projectDir) throws IOException
  {
    Map<String, String> statusMap = new HashMap<>();

    String baseBranch = getBaseBranch(projectDir);
    if (baseBranch.isEmpty())
      return Map.of();

    List<String> branches = getAllBranches(projectDir);

    for (String branch : branches)
    {
      if (branch.equals(baseBranch))
        continue;

      List<String> changedStateFiles = getChangedStateFiles(projectDir, baseBranch, branch);

      for (String stateFilePath : changedStateFiles)
      {
        String status = getStatusFromBranch(projectDir, branch, stateFilePath);
        if (!status.isEmpty())
        {
          String issueRelPath = stateFilePath.replace("/STATE.md", "");
          statusMap.putIfAbsent(issueRelPath, status);
        }
      }
    }

    return statusMap;
  }

  /**
   * Gets the base branch for the current repository.
   *
   * @param projectDir the project root directory
   * @return the base branch name, or empty string if not found
   * @throws IOException if git command fails
   */
  private String getBaseBranch(Path projectDir) throws IOException
  {
    List<String> lines = executeGitCommand(projectDir, "git", "rev-parse", "--abbrev-ref", "HEAD");
    if (lines.isEmpty())
      return "";

    String branch = lines.get(0).strip();
    if (branch.matches("v[0-9]+\\.[0-9]+"))
      return branch;

    return "";
  }

  /**
   * Gets all branches in the repository.
   * <p>
   * Branch names are validated to ensure they match the expected pattern.
   * Invalid branch names are skipped.
   *
   * @param projectDir the project root directory
   * @return list of valid branch names
   * @throws IOException if git command fails
   */
  private List<String> getAllBranches(Path projectDir) throws IOException
  {
    List<String> lines = executeGitCommand(projectDir, "git", "branch", "--format=%(refname:short)");
    List<String> branches = new ArrayList<>();

    Pattern validBranchPattern = Pattern.compile("[a-zA-Z0-9._/-]+");
    for (String line : lines)
    {
      String branch = line.strip();
      if (!branch.isEmpty() && validBranchPattern.matcher(branch).matches())
        branches.add(branch);
    }

    return branches;
  }

  /**
   * Gets the list of STATE.md files that changed between base branch and the given branch.
   * <p>
   * File paths are validated to ensure they are within the expected directory
   * and do not contain path traversal sequences.
   *
   * @param projectDir the project root directory
   * @param baseBranch the base branch name
   * @param branch the branch to compare
   * @return list of valid changed STATE.md file paths relative to project root
   * @throws IOException if git command fails
   */
  private List<String> getChangedStateFiles(Path projectDir, String baseBranch, String branch)
    throws IOException
  {
    List<String> lines = executeGitCommand(projectDir, "git", "diff", "--name-only",
      baseBranch + "..." + branch, "--", ".claude/cat/issues/**/STATE.md");

    List<String> changedFiles = new ArrayList<>();
    for (String line : lines)
    {
      String filePath = line.strip();
      if (isValidStateFilePath(filePath))
        changedFiles.add(filePath);
    }

    return changedFiles;
  }

  /**
   * Validates a STATE.md file path from git output.
   * <p>
   * Valid paths must:
   * - Not contain ".." sequences (path traversal)
   * - Start with ".claude/cat/issues/"
   * - End with "/STATE.md"
   *
   * @param filePath the file path to validate
   * @return true if the path is valid
   */
  public boolean isValidStateFilePath(String filePath)
  {
    if (filePath.isEmpty())
      return false;
    if (filePath.contains(".."))
      return false;
    if (!filePath.startsWith(".claude/cat/issues/"))
      return false;
    if (!filePath.endsWith("/STATE.md"))
      return false;
    return true;
  }

  /**
   * Reads the status from a STATE.md file on a specific branch.
   * <p>
   * Branch names are validated before use in git commands.
   *
   * @param projectDir the project root directory
   * @param branch the branch name
   * @param filePath the file path relative to project root
   * @return the normalized status, or empty string if not found
   * @throws IOException if git command fails
   */
  private String getStatusFromBranch(Path projectDir, String branch, String filePath) throws IOException
  {
    if (!isValidBranchName(branch))
      return "";

    List<String> lines = executeGitCommand(projectDir, "git", "show", branch + ":" + filePath);
    if (lines.isEmpty())
      return "";

    StringBuilder content = new StringBuilder();
    for (String line : lines)
      content.append(line).append('\n');

    return parseStatusFromContent(content.toString());
  }

  /**
   * Validates a branch name.
   * <p>
   * Valid branch names must match: [a-zA-Z0-9._/-]+
   *
   * @param branch the branch name to validate
   * @return true if the branch name is valid
   */
  public boolean isValidBranchName(String branch)
  {
    if (branch == null || branch.isEmpty())
      return false;
    return branch.matches("[a-zA-Z0-9._/-]+");
  }

  /**
   * Gets list of active agents from lock files.
   *
   * @param catDir the CAT directory
   * @param currentSession the current session ID to exclude
   * @return the list of active agents
   * @throws IOException if an I/O error occurs
   */
  private List<Agent> getActiveAgents(Path catDir, String currentSession) throws IOException
  {
    Path locksDir = catDir.resolve("locks");
    if (!Files.isDirectory(locksDir))
      return List.of();

    List<Agent> agents = new ArrayList<>();
    long now = System.currentTimeMillis() / 1000;

    try (Stream<Path> lockStream = Files.list(locksDir))
    {
      List<Path> lockFiles = lockStream.
        filter(p -> p.getFileName().toString().endsWith(".lock")).
        toList();

      for (Path lockFile : lockFiles)
      {
        String issueId = lockFile.getFileName().toString();
        issueId = issueId.substring(0, issueId.length() - 5);

        String sessionId = "";
        long createdAt = 0;
        String worktree = "";

        String content = Files.readString(lockFile);
        for (String line : content.split("\n"))
        {
          if (line.startsWith("session_id="))
            sessionId = line.substring("session_id=".length());
          else if (line.startsWith("created_at="))
          {
            try
            {
              createdAt = Long.parseLong(line.substring("created_at=".length()));
            }
            catch (NumberFormatException _)
            {
            }
          }
          else if (line.startsWith("worktree="))
            worktree = line.substring("worktree=".length());
        }

        if (sessionId.equals(currentSession))
          continue;

        if (!sessionId.isEmpty())
        {
          long ageSeconds;
          if (createdAt > 0)
            ageSeconds = now - createdAt;
          else
            ageSeconds = 0;
          Agent agent = new Agent();
          agent.issueId = issueId;
          agent.sessionId = sessionId;
          agent.ageSeconds = ageSeconds;
          agent.worktree = worktree;
          agents.add(agent);
        }
      }
    }

    agents.sort(Comparator.comparingLong(a -> a.ageSeconds));
    return agents;
  }

  /**
   * Formats age in human-readable form.
   *
   * @param seconds the age in seconds
   * @return the formatted age string
   */
  private String formatAge(long seconds)
  {
    if (seconds < 60)
      return seconds + "s ago";
    if (seconds < 3600)
      return seconds / 60 + "m ago";
    return seconds / 3600 + "h ago";
  }

  /**
   * Formats session ID for display.
   *
   * @param sessionId the session ID
   * @return the formatted session ID
   */
  private String formatSessionId(String sessionId)
  {
    return sessionId;
  }

  /**
   * Builds the task list for a minor version by processing task directories.
   * <p>
   * Creates Task objects with resolved dependency information, tracking completion
   * counts and in-progress status.
   *
   * @param taskDirs the list of task directories to process
   * @param catDir the CAT root directory
   * @param minorNum the minor version number (e.g., "2.1")
   * @param licenseResult the license validation result
   * @param allTaskStatuses map of task name to status (from first pass)
   * @param tasks the list to populate with Task objects
   * @return task statistics including total, completed, and in-progress task name
   * @throws IOException if an I/O error occurs
   */
  private TaskStats buildMinorVersionTasks(List<Path> taskDirs, Path catDir, String minorNum,
    LicenseResult licenseResult, Map<String, String> allTaskStatuses, List<Task> tasks) throws IOException
  {
    int total = 0;
    int completed = 0;
    String inProgress = "";

    for (Path taskDir : taskDirs)
    {
      String taskName = taskDir.getFileName().toString();
      Path stateFile = taskDir.resolve("STATE.md");
      Path planFile = taskDir.resolve("PLAN.md");

      if (!Files.exists(stateFile) && !Files.exists(planFile))
        continue;

      String status = getTaskStatus(stateFile, catDir, minorNum, taskName, licenseResult);
      List<String> dependencies = getTaskDependencies(stateFile);
      ++total;

      List<String> blockedBy = new ArrayList<>();
      for (String dep : dependencies)
      {
        String depStatus = allTaskStatuses.getOrDefault(dep, "open");
        if (!depStatus.equals("closed"))
          blockedBy.add(dep);
      }

      long mtime = Files.getLastModifiedTime(taskDir).toMillis();

      Task task = new Task();
      task.name = taskName;
      task.status = status;
      task.dependencies = dependencies;
      task.blockedBy = blockedBy;
      task.mtime = mtime;
      tasks.add(task);

      if (status.equals("closed"))
        ++completed;
      else if (status.equals("in-progress"))
        inProgress = taskName;
    }

    return new TaskStats(total, completed, inProgress);
  }

  /**
   * Holds task statistics for a minor version.
   *
   * @param total the total number of tasks
   * @param completed the number of completed tasks
   * @param inProgress the name of the in-progress task, or empty string if none
   */
  private record TaskStats(int total, int completed, String inProgress)
  {
  }

  /**
   * Holds collected status data.
   */
  private static final class StatusData
  {
    String error;
    String projectName = "";
    int overallPercent;
    int overallCompleted;
    int overallTotal;
    String currentMinor = "";
    String inProgressTask = "";
    String nextTask = "";
    final List<MajorVersion> majors = new ArrayList<>();
    final List<MinorVersion> minors = new ArrayList<>();
  }

  /**
   * Represents a major version.
   */
  private static final class MajorVersion
  {
    String id = "";
    String name = "";
    String status = "";
  }

  /**
   * Represents a minor version.
   */
  private static final class MinorVersion
  {
    String id = "";
    String major = "";
    String description = "";
    int completed;
    int total;
    String inProgress = "";
    List<Task> tasks = new ArrayList<>();
  }

  /**
   * Represents a task within a minor version.
   */
  private static final class Task
  {
    String name = "";
    String status = "";
    List<String> dependencies = new ArrayList<>();
    List<String> blockedBy = new ArrayList<>();
    long mtime;
  }

  /**
   * Represents an active agent.
   */
  private static final class Agent
  {
    String issueId = "";
    String sessionId = "";
    long ageSeconds;
    String worktree = "";
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetStatusOutput generator = new GetStatusOutput(scope);
      String output = generator.getOutput();
      System.out.println(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating status: " + e.getMessage());
      System.exit(1);
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetStatusOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}
