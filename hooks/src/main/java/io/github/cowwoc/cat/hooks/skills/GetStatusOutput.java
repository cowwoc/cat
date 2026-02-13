package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;
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
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

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

    Path issuesDir = catDir.resolve("issues");
    StatusData statusData = collectStatusData(issuesDir, catDir);

    if (statusData.error != null)
      return statusData.error;

    return generateStatusDisplay(statusData, catDir);
  }

  /**
   * Collects project status data from CAT directory structure.
   *
   * @param issuesDir the issues directory
   * @param catDir the CAT root directory
   * @return the collected status data
   * @throws IOException if an I/O error occurs
   */
  private StatusData collectStatusData(Path issuesDir, Path catDir) throws IOException
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
          majorStatus = getTaskStatus(majorStateFile);

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

              for (Path taskDir : taskDirs)
              {
                String taskName = taskDir.getFileName().toString();
                Path stateFile = taskDir.resolve("STATE.md");
                Path planFile = taskDir.resolve("PLAN.md");

                if (!Files.exists(stateFile) && !Files.exists(planFile))
                  continue;

                String status = getTaskStatus(stateFile);
                allTaskStatuses.put(taskName, status);
              }

              for (Path taskDir : taskDirs)
              {
                String taskName = taskDir.getFileName().toString();
                Path stateFile = taskDir.resolve("STATE.md");
                Path planFile = taskDir.resolve("PLAN.md");

                if (!Files.exists(stateFile) && !Files.exists(planFile))
                  continue;

                String status = getTaskStatus(stateFile);
                List<String> dependencies = getTaskDependencies(stateFile);
                ++localTotal;

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
                  ++localCompleted;
                else if (status.equals("in-progress"))
                  localInprog = taskName;
              }
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
   *
   * @param stateFile the STATE.md file path
   * @return the normalized status
   * @throws IOException if an I/O error occurs
   */
  private String getTaskStatus(Path stateFile) throws IOException
  {
    if (!Files.exists(stateFile))
      return "open";

    String content = Files.readString(stateFile);
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
  }
}
