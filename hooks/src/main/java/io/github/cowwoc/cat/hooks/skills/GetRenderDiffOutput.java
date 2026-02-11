package io.github.cowwoc.cat.hooks.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for render-diff skill.
 *
 * Pre-computes rendered diff output for approval gates so the agent
 * can display it directly without visible Bash tool invocations.
 *
 * Parses unified diff format and renders a 4-column table with:
 * Old line | Symbol | New line | Content
 */
public final class GetRenderDiffOutput
{
  private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+)-");
  private static final Pattern VERSION_BRANCH_PATTERN = Pattern.compile("^v\\d+\\.\\d+$");
  private static final Pattern STAT_FILES_PATTERN = Pattern.compile("(\\d+) files? changed");
  private static final Pattern STAT_INS_PATTERN = Pattern.compile("(\\d+) insertions?\\(\\+\\)");
  private static final Pattern STAT_DEL_PATTERN = Pattern.compile("(\\d+) deletions?\\(-\\)");

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetRenderDiffOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetRenderDiffOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Pre-compute rendered diff using project root from environment.
   *
   * This method supports direct preprocessing pattern - it collects all
   * necessary data from the environment without requiring LLM-provided arguments.
   *
   * @return the formatted diff output, or null if CLAUDE_PROJECT_DIR not set or on error
   */
  public String getOutput()
  {
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isBlank())
      return null;
    return getOutput(Path.of(projectDir));
  }

  /**
   * Pre-compute rendered diff for approval gates.
   *
   * @param projectRoot the project root path
   * @return the formatted diff output, or null on error
   * @throws NullPointerException if projectRoot is null
   */
  public String getOutput(Path projectRoot)
  {
    requireThat(projectRoot, "projectRoot").isNotNull();

    // Load config for terminal width
    Config config = Config.load(scope.getJsonMapper(), projectRoot);
    int terminalWidth = config.getInt("terminalWidth", 50);

    // Detect base branch
    String baseBranch = detectBaseBranch(projectRoot);
    if (baseBranch == null)
      return null;

    // Check if base branch exists
    if (!branchExists(projectRoot, baseBranch))
    {
      // Try with origin/ prefix
      baseBranch = "origin/" + baseBranch;
      if (!branchExists(projectRoot, baseBranch))
        return null;
    }

    // Get changed files list
    List<String> changedFiles = getChangedFiles(projectRoot, baseBranch);
    if (changedFiles.isEmpty())
      return "No changes detected between " + baseBranch + " and HEAD.";

    // Get diff stats
    DiffStats stats = getDiffStats(projectRoot, baseBranch);

    // Get raw diff output
    String rawDiff = runGitCommand(projectRoot, "diff", baseBranch + "..HEAD");
    if (rawDiff == null || rawDiff.isEmpty())
      return null;

    // Parse and render diff in Java
    DiffParser parser = new DiffParser();
    ParsedDiff diff = parser.parse(rawDiff);

    if (diff.hunks.isEmpty() && diff.binaryFiles.isEmpty() && diff.renamedFiles.isEmpty())
      return "No parseable changes found.";

    DiffRenderer renderer = new DiffRenderer(terminalWidth, scope.getDisplayUtils());
    String rendered = renderer.render(diff);

    // Build file summary
    StringBuilder fileList = new StringBuilder(64);
    int count = 0;
    for (String file : changedFiles)
    {
      if (count >= 20)
      {
        fileList.append("\n  ... and ").append(changedFiles.size() - 20).append(" more files");
        break;
      }
      if (count > 0)
        fileList.append('\n');
      fileList.append("  - ").append(file);
      ++count;
    }

    return "## Diff Summary\n" +
           "- **Base branch:** " + baseBranch + "\n" +
           "- **Files changed:** " + stats.filesChanged + "\n" +
           "- **Insertions:** +" + stats.insertions + "\n" +
           "- **Deletions:** -" + stats.deletions + "\n" +
           "\n" +
           "## Changed Files\n" + fileList + "\n" +
           "\n" +
           "## Rendered Diff (4-column format)\n" +
           "\n" + rendered;
  }

  /**
   * Detects the base branch for diff comparison.
   *
   * @param projectRoot the project root path
   * @return the base branch name, or null if not detected
   */
  private String detectBaseBranch(Path projectRoot)
  {
    try
    {
      // Get current directory name (might be worktree)
      Path cwd = Path.of(System.getProperty("user.dir"));
      String worktreeName = cwd.getFileName().toString();

      // Check if we're in a worktrees directory
      if (cwd.getParent() != null && "worktrees".equals(cwd.getParent().getFileName().toString()))
      {
        // Extract version from worktree name (e.g., "2.0-issue-name" -> "v2.0")
        Matcher match = VERSION_PATTERN.matcher(worktreeName);
        if (match.find())
          return "v" + match.group(1);
      }

      // Get current branch name
      String branch = runGitCommand(projectRoot, "rev-parse", "--abbrev-ref", "HEAD");
      if (branch != null)
      {
        // Extract version from branch name
        Matcher match = VERSION_PATTERN.matcher(branch);
        if (match.find())
          return "v" + match.group(1);

        // Check if branch is a version branch itself
        if (VERSION_BRANCH_PATTERN.matcher(branch).matches())
          return "main";
      }

      // Try to get tracking branch
      String upstream = runGitCommand(projectRoot, "rev-parse", "--abbrev-ref", "@{upstream}");
      if (upstream != null && upstream.contains("/"))
        return upstream.substring(upstream.indexOf('/') + 1);
      else if (upstream != null)
        return upstream;
    }
    catch (Exception _)
    {
    }

    // Default fallback
    return "main";
  }

  /**
   * Checks if a branch exists in the repository.
   *
   * @param projectRoot the project root path
   * @param branch the branch name to check
   * @return true if the branch exists
   */
  private boolean branchExists(Path projectRoot, String branch)
  {
    String result = runGitCommand(projectRoot, "rev-parse", "--verify", branch);
    return result != null;
  }

  /**
   * Gets the list of changed files between base and HEAD.
   *
   * @param projectRoot the project root path
   * @param baseBranch the base branch for comparison
   * @return the list of changed file paths
   */
  private List<String> getChangedFiles(Path projectRoot, String baseBranch)
  {
    List<String> result = new ArrayList<>();
    String output = runGitCommand(projectRoot, "diff", "--name-only", baseBranch + "..HEAD");
    if (output != null)
    {
      for (String line : output.split("\n"))
      {
        if (!line.isEmpty())
          result.add(line);
      }
    }
    return result;
  }

  /**
   * Gets diff statistics between base and HEAD.
   *
   * @param projectRoot the project root path
   * @param baseBranch the base branch for comparison
   * @return the diff statistics
   */
  private DiffStats getDiffStats(Path projectRoot, String baseBranch)
  {
    DiffStats stats = new DiffStats();
    String output = runGitCommand(projectRoot, "diff", "--stat", baseBranch + "..HEAD");
    if (output != null)
    {
      String[] lines = output.split("\n");
      if (lines.length > 0)
      {
        String summary = lines[lines.length - 1];
        Matcher filesMatcher = STAT_FILES_PATTERN.matcher(summary);
        Matcher insMatcher = STAT_INS_PATTERN.matcher(summary);
        Matcher delMatcher = STAT_DEL_PATTERN.matcher(summary);

        if (filesMatcher.find())
          stats.filesChanged = Integer.parseInt(filesMatcher.group(1));
        if (insMatcher.find())
          stats.insertions = Integer.parseInt(insMatcher.group(1));
        if (delMatcher.find())
          stats.deletions = Integer.parseInt(delMatcher.group(1));
      }
    }
    return stats;
  }

  /**
   * Runs a git command and returns its output.
   *
   * @param projectRoot the project root path
   * @param args the git command arguments
   * @return the command output, or null on error
   */
  private String runGitCommand(Path projectRoot, String... args)
  {
    try
    {
      String[] command = new String[args.length + 1];
      command[0] = "git";
      System.arraycopy(args, 0, command, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(projectRoot.toFile());
      pb.redirectErrorStream(false);
      Process process = pb.start();

      String output;
      try (BufferedReader reader = new BufferedReader(
             new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        output = reader.lines().collect(Collectors.joining("\n"));
      }

      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      if (!finished)
      {
        process.destroyForcibly();
        return null;
      }

      if (process.exitValue() != 0)
        return null;

      return output.trim();
    }
    catch (IOException | InterruptedException _)
    {
      return null;
    }
  }

  /**
   * Holds diff statistics.
   */
  private static final class DiffStats
  {
    int filesChanged;
    int insertions;
    int deletions;
  }

  /**
   * Represents a git commit.
   */
  private static class Commit
  {
    final String hash;
    String subject = "";
    String body = "";

    /**
     * Creates a commit with the given hash.
     *
     * @param hash the commit hash
     */
    Commit(String hash)
    {
      this.hash = hash;
    }
  }

  /**
   * Represents a single diff hunk.
   */
  private static class DiffHunk
  {
    final String file;
    final int oldStart;
    final int newStart;
    final String context;
    final List<String> lines = new ArrayList<>();
    Commit commit;

    /**
     * Creates a diff hunk.
     *
     * @param file the file name
     * @param oldStart the old file start line
     * @param newStart the new file start line
     * @param context the hunk context (function name)
     */
    DiffHunk(String file, int oldStart, int newStart, String context)
    {
      this.file = file;
      this.oldStart = oldStart;
      this.newStart = newStart;
      this.context = context;
    }
  }

  /**
   * Parsed diff data.
   */
  private static final class ParsedDiff
  {
    final List<DiffHunk> hunks = new ArrayList<>();
    final List<String> binaryFiles = new ArrayList<>();
    final Map<String, String> renamedFiles = new HashMap<>();
    final List<Commit> commits = new ArrayList<>();
    final Map<String, Commit> fileToCommit = new HashMap<>();
  }

  /**
   * Tracks which symbols are used for dynamic legend.
   */
  private static final class UsedSymbols
  {
    boolean minus;
    boolean plus;
    boolean space;
    boolean tab;
    boolean wrap;
  }

  /**
   * Parses unified diff format.
   */
  private static class DiffParser
  {
    private static final Pattern FILE_HEADER = Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)$");
    private static final Pattern RENAME_FROM = Pattern.compile("^rename from (.+)$");
    private static final Pattern RENAME_TO = Pattern.compile("^rename to (.+)$");
    private static final Pattern BINARY = Pattern.compile("^Binary files");
    private static final Pattern COMMIT_HEADER = Pattern.compile("^commit ([a-f0-9]{7,40})$");

    /**
     * Creates a diff parser.
     */
    DiffParser()
    {
    }

    /**
     * Parses diff text into structured data.
     *
     * @param diffText the raw diff text
     * @return the parsed diff structure
     */
    ParsedDiff parse(String diffText)
    {
      ParsedDiff result = new ParsedDiff();
      String currentFile = "";
      DiffHunk currentHunk = null;
      Commit currentCommit = null;
      String renameFrom = "";
      boolean inCommitMessage = false;
      boolean inDiffContent = false;
      List<String> commitMessageLines = new ArrayList<>();

      String[] lines = diffText.split("\n");
      int i = 0;
      while (i < lines.length)
      {
        String line = lines[i];

        // Commit header (from git log -p or git show)
        Matcher match = COMMIT_HEADER.matcher(line);
        if (match.matches())
        {
          // Finalize previous commit message if any
          if (currentCommit != null && !commitMessageLines.isEmpty())
          {
            finalizeCommitMessage(currentCommit, commitMessageLines);
            commitMessageLines.clear();
          }

          if (currentHunk != null)
          {
            result.hunks.add(currentHunk);
            currentHunk = null;
          }

          String commitHash = match.group(1);
          currentCommit = new Commit(commitHash);
          result.commits.add(currentCommit);
          inCommitMessage = false;
          inDiffContent = false;
          ++i;
          continue;
        }

        // Skip Author/Date/Merge lines after commit header
        if (currentCommit != null && !inCommitMessage && !inDiffContent &&
            (line.startsWith("Author:") || line.startsWith("Date:") ||
             line.startsWith("Merge:") || line.isBlank()))
        {
          if (line.isBlank() && commitMessageLines.isEmpty())
            inCommitMessage = true;
          ++i;
          continue;
        }

        // Collect commit message lines
        if (inCommitMessage && currentCommit != null && !inDiffContent)
        {
          if (line.startsWith("    "))
          {
            commitMessageLines.add(line.substring(4));
            ++i;
            continue;
          }
          else if (line.isBlank())
          {
            commitMessageLines.add("");
            ++i;
            continue;
          }
          finalizeCommitMessage(currentCommit, commitMessageLines);
          commitMessageLines.clear();
          inCommitMessage = false;
        }

        // New file
        match = FILE_HEADER.matcher(line);
        if (match.matches())
        {
          inDiffContent = true;
          if (currentHunk != null)
          {
            result.hunks.add(currentHunk);
            currentHunk = null;
          }
          currentFile = match.group(2);
          renameFrom = "";
          if (currentCommit != null)
            result.fileToCommit.put(currentFile, currentCommit);
          ++i;
          continue;
        }

        // Rename detection
        match = RENAME_FROM.matcher(line);
        if (match.matches())
        {
          renameFrom = match.group(1);
          ++i;
          continue;
        }

        match = RENAME_TO.matcher(line);
        if (match.matches() && !renameFrom.isEmpty())
        {
          result.renamedFiles.put(currentFile, renameFrom);
          ++i;
          continue;
        }

        // Binary file
        if (BINARY.matcher(line).find())
        {
          result.binaryFiles.add(currentFile);
          ++i;
          continue;
        }

        // Skip metadata
        if (line.startsWith("index ") || line.startsWith("--- ") ||
            line.startsWith("+++ ") || line.startsWith("new file") ||
            line.startsWith("deleted file") || line.startsWith("similarity"))
        {
          ++i;
          continue;
        }

        // Hunk header
        match = HUNK_HEADER.matcher(line);
        if (match.matches())
        {
          if (currentHunk != null)
            result.hunks.add(currentHunk);
          currentHunk = new DiffHunk(
            currentFile,
            Integer.parseInt(match.group(1)),
            Integer.parseInt(match.group(2)),
            match.group(3).trim());
          currentHunk.commit = currentCommit;
          ++i;
          continue;
        }

        // Content lines
        if (currentHunk != null)
        {
          if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" "))
            currentHunk.lines.add(line);
          else if (line.equals("\\ No newline at end of file"))
            currentHunk.lines.add(line);
        }

        ++i;
      }

      // Finalize any remaining commit message
      if (currentCommit != null && !commitMessageLines.isEmpty())
        finalizeCommitMessage(currentCommit, commitMessageLines);

      // Save final hunk
      if (currentHunk != null)
        result.hunks.add(currentHunk);

      return result;
    }

    /**
     * Extracts subject and body from commit message lines.
     *
     * @param commit the commit to update
     * @param lines the message lines
     */
    private void finalizeCommitMessage(Commit commit, List<String> lines)
    {
      // Remove leading/trailing empty lines
      while (!lines.isEmpty() && lines.get(0).isBlank())
        lines.remove(0);
      while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank())
        lines.remove(lines.size() - 1);

      if (lines.isEmpty())
        return;

      // First non-empty line is subject
      commit.subject = lines.get(0).trim();

      // Rest is body
      if (lines.size() > 1)
      {
        List<String> bodyLines = new ArrayList<>(lines.subList(1, lines.size()));
        while (!bodyLines.isEmpty() && bodyLines.get(0).isBlank())
          bodyLines.remove(0);
        commit.body = String.join("\n", bodyLines);
      }
    }
  }

  /**
   * Renders diff in 4-column table format.
   */
  private static class DiffRenderer
  {
    // Box drawing characters
    private static final char BOX_TOP_LEFT = '\u256D';
    private static final char BOX_TOP_RIGHT = '\u256E';
    private static final char BOX_BOTTOM_LEFT = '\u2570';
    private static final char BOX_BOTTOM_RIGHT = '\u256F';
    private static final char BOX_HORIZONTAL = '\u2500';
    private static final char BOX_VERTICAL = '\u2502';
    private static final char BOX_T_DOWN = '\u252C';
    private static final char BOX_T_UP = '\u2534';
    private static final char BOX_RIGHT_INTERSECTION = '\u251C';
    private static final char BOX_LEFT_INTERSECTION = '\u2524';
    private static final char BOX_CROSS = '\u253C';

    // Column widths
    private static final int OLD_LINE_WIDTH = 4;
    private static final int SYMBOL_WIDTH = 3;
    private static final int NEW_LINE_WIDTH = 4;

    private final int width;
    private final int contentWidth;
    private final DisplayUtils display;
    private final UsedSymbols used = new UsedSymbols();
    private final List<String> output = new ArrayList<>();

    /**
     * Creates a diff renderer.
     *
     * @param width the total box width
     * @param display the display utilities
     */
    DiffRenderer(int width, DisplayUtils display)
    {
      this.width = width;
      this.display = display;
      // Calculate content width: total - borders - col widths - internal padding
      // |Old |sym |New | Content | = 17 fixed chars
      this.contentWidth = width - 17;
    }

    /**
     * Renders the complete diff.
     *
     * @param diff the parsed diff
     * @return the rendered output
     */
    String render(ParsedDiff diff)
    {
      output.clear();
      boolean firstBox = true;
      Commit currentCommit = null;

      // Render binary files first
      for (String file : diff.binaryFiles)
      {
        Commit commit = diff.fileToCommit.get(file);
        if (commit != null && commit != currentCommit)
        {
          if (!firstBox)
            output.add("");
          currentCommit = commit;
          printCommitHeader(commit);
          firstBox = false;
        }
        if (!firstBox)
          output.add("");
        firstBox = false;
        printBinaryFile(file);
      }

      // Render renamed files without content changes
      Set<String> filesWithHunks = new HashSet<>();
      for (DiffHunk hunk : diff.hunks)
        filesWithHunks.add(hunk.file);

      for (Map.Entry<String, String> entry : diff.renamedFiles.entrySet())
      {
        String newPath = entry.getKey();
        String oldPath = entry.getValue();
        if (!filesWithHunks.contains(newPath))
        {
          Commit commit = diff.fileToCommit.get(newPath);
          if (commit != null && commit != currentCommit)
          {
            if (!firstBox)
              output.add("");
            currentCommit = commit;
            printCommitHeader(commit);
            firstBox = false;
          }
          if (!firstBox)
            output.add("");
          firstBox = false;
          printRenamedFile(oldPath, newPath);
        }
      }

      // Render each hunk
      for (DiffHunk hunk : diff.hunks)
      {
        if (diff.binaryFiles.contains(hunk.file))
          continue;

        if (hunk.commit != null && hunk.commit != currentCommit)
        {
          if (!firstBox)
            output.add("");
          currentCommit = hunk.commit;
          printCommitHeader(hunk.commit);
          firstBox = false;
        }

        if (!firstBox)
          output.add("");
        firstBox = false;

        printHunkTop(hunk.file);
        printColumnHeader(hunk.context);
        renderHunkContent(hunk);
        printHunkBottom();
      }

      // Print legend
      printLegend();

      return String.join("\n", output);
    }

    /**
     * Prints commit header box.
     *
     * @param commit the commit
     */
    private void printCommitHeader(Commit commit)
    {
      String shortHash;
      if (commit.hash.length() >= 7)
        shortHash = commit.hash.substring(0, 7);
      else
        shortHash = commit.hash;
      String headerText = "COMMIT " + shortHash + ": " + commit.subject;
      int headerLen = display.displayWidth(headerText);

      // Truncate if too long
      if (headerLen > width - 4)
      {
        int maxSubjectLen = width - 4 - ("COMMIT " + shortHash + ": ").length() - 3;
        String truncatedSubject = commit.subject.substring(0, Math.max(0, maxSubjectLen)) + "...";
        headerText = "COMMIT " + shortHash + ": " + truncatedSubject;
        headerLen = display.displayWidth(headerText);
      }

      int padding = width - 4 - headerLen;

      output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
      output.add(BOX_VERTICAL + " " + headerText + " ".repeat(Math.max(0, padding)) + " " + BOX_VERTICAL);
      output.add(BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_BOTTOM_RIGHT);
    }

    /**
     * Prints hunk box top with file header.
     *
     * @param filename the file name
     */
    private void printHunkTop(String filename)
    {
      String fileText = "FILE: " + filename;
      int fileLen = display.displayWidth(fileText);
      int padding = width - 4 - fileLen;

      output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
      output.add(BOX_VERTICAL + " " + fileText + " ".repeat(Math.max(0, padding)) + " " + BOX_VERTICAL);
    }

    /**
     * Prints column header row with hunk context.
     *
     * @param context the hunk context
     */
    private void printColumnHeader(String context)
    {
      // Separator after file header
      output.add(
        BOX_RIGHT_INTERSECTION + fillChar(BOX_HORIZONTAL, OLD_LINE_WIDTH) + BOX_T_DOWN +
        fillChar(BOX_HORIZONTAL, SYMBOL_WIDTH) + BOX_T_DOWN +
        fillChar(BOX_HORIZONTAL, NEW_LINE_WIDTH) + BOX_T_DOWN +
        fillChar(BOX_HORIZONTAL, contentWidth + 1) + BOX_LEFT_INTERSECTION);

      // Header row with context
      String contextText;
      if (!context.isEmpty())
        contextText = "⎁ " + context;
      else
        contextText = "";
      int ctxLen = display.displayWidth(contextText);
      if (ctxLen > contentWidth)
        contextText = truncateToWidth(contextText, contentWidth - 1) + "…";

      output.add(
        BOX_VERTICAL + padNum("Old", OLD_LINE_WIDTH) + BOX_VERTICAL + "   " +
        BOX_VERTICAL + padNum("New", NEW_LINE_WIDTH) + BOX_VERTICAL + " " +
        padRight(contextText, contentWidth) + BOX_VERTICAL);

      // Separator after headers
      output.add(
        BOX_RIGHT_INTERSECTION + fillChar(BOX_HORIZONTAL, OLD_LINE_WIDTH) + BOX_CROSS +
        fillChar(BOX_HORIZONTAL, SYMBOL_WIDTH) + BOX_CROSS +
        fillChar(BOX_HORIZONTAL, NEW_LINE_WIDTH) + BOX_CROSS +
        fillChar(BOX_HORIZONTAL, contentWidth + 1) + BOX_LEFT_INTERSECTION);
    }

    /**
     * Prints a content row, handling wrapping for long lines.
     *
     * @param oldNum the old line number
     * @param symbol the change symbol
     * @param newNum the new line number
     * @param content the line content
     */
    private void printRow(String oldNum, String symbol, String newNum, String content)
    {
      int contentLen = display.displayWidth(content);

      if (contentLen <= contentWidth)
      {
        String paddedContent = content + " ".repeat(contentWidth - contentLen);
        output.add(
          BOX_VERTICAL + padNum(oldNum, OLD_LINE_WIDTH) + BOX_VERTICAL + " " + symbol + " " +
          BOX_VERTICAL + padNum(newNum, NEW_LINE_WIDTH) + BOX_VERTICAL + " " +
          paddedContent + BOX_VERTICAL);
      }
      else
      {
        // Wrap long lines
        used.wrap = true;
        String firstPart = content.substring(0, Math.min(content.length(), contentWidth - 1));
        output.add(
          BOX_VERTICAL + padNum(oldNum, OLD_LINE_WIDTH) + BOX_VERTICAL + " " + symbol + " " +
          BOX_VERTICAL + padNum(newNum, NEW_LINE_WIDTH) + BOX_VERTICAL + " " +
          firstPart + "↩" + BOX_VERTICAL);

        String remaining;
        if (content.length() > contentWidth - 1)
          remaining = content.substring(contentWidth - 1);
        else
          remaining = "";
        while (!remaining.isEmpty())
        {
          int partLen = display.displayWidth(remaining);
          if (partLen <= contentWidth)
          {
            String padded = remaining + " ".repeat(contentWidth - partLen);
            output.add(
              BOX_VERTICAL + " ".repeat(OLD_LINE_WIDTH) + BOX_VERTICAL + "   " +
              BOX_VERTICAL + " ".repeat(NEW_LINE_WIDTH) + BOX_VERTICAL + " " +
              padded + BOX_VERTICAL);
            remaining = "";
          }
          else
          {
            String nextPart = remaining.substring(0, Math.min(remaining.length(), contentWidth - 1));
            output.add(
              BOX_VERTICAL + " ".repeat(OLD_LINE_WIDTH) + BOX_VERTICAL + "   " +
              BOX_VERTICAL + " ".repeat(NEW_LINE_WIDTH) + BOX_VERTICAL + " " +
              nextPart + "↩" + BOX_VERTICAL);
            if (remaining.length() > contentWidth - 1)
              remaining = remaining.substring(contentWidth - 1);
            else
              remaining = "";
          }
        }
      }
    }

    /**
     * Prints hunk box bottom.
     */
    private void printHunkBottom()
    {
      output.add(
        BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, OLD_LINE_WIDTH) + BOX_T_UP +
        fillChar(BOX_HORIZONTAL, SYMBOL_WIDTH) + BOX_T_UP +
        fillChar(BOX_HORIZONTAL, NEW_LINE_WIDTH) + BOX_T_UP +
        fillChar(BOX_HORIZONTAL, contentWidth + 1) + BOX_BOTTOM_RIGHT);
    }

    /**
     * Prints binary file box.
     *
     * @param filename the file name
     */
    private void printBinaryFile(String filename)
    {
      String fileText = "FILE: " + filename + " (binary)";
      int fileLen = display.displayWidth(fileText);
      int padding = width - 4 - fileLen;

      output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
      output.add(BOX_VERTICAL + " " + fileText + " ".repeat(Math.max(0, padding)) + " " + BOX_VERTICAL);
      output.add(BOX_RIGHT_INTERSECTION + fillChar(BOX_HORIZONTAL, width - 2) + BOX_LEFT_INTERSECTION);
      String content = "Binary file changed";
      output.add(BOX_VERTICAL + " " + padRight(content, width - 4) + " " + BOX_VERTICAL);
      output.add(BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_BOTTOM_RIGHT);
    }

    /**
     * Prints renamed file box.
     *
     * @param oldPath the old file path
     * @param newPath the new file path
     */
    private void printRenamedFile(String oldPath, String newPath)
    {
      String fileText = "FILE: " + oldPath + " " + DisplayUtils.ARROW_RIGHT + " " + newPath + " (renamed)";
      int fileLen = display.displayWidth(fileText);
      int padding = width - 4 - fileLen;

      output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
      if (fileLen > width - 4)
      {
        String truncated = fileText.substring(0, Math.max(0, width - 7)) + "...";
        output.add(BOX_VERTICAL + " " + padRight(truncated, width - 4) + " " + BOX_VERTICAL);
      }
      else
        output.add(BOX_VERTICAL + " " + fileText + " ".repeat(Math.max(0, padding)) + " " + BOX_VERTICAL);
      output.add(BOX_RIGHT_INTERSECTION + fillChar(BOX_HORIZONTAL, width - 2) + BOX_LEFT_INTERSECTION);
      String content = "File renamed (no content changes)";
      output.add(BOX_VERTICAL + " " + padRight(content, width - 4) + " " + BOX_VERTICAL);
      output.add(BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_BOTTOM_RIGHT);
    }

    /**
     * Prints legend box showing used symbols.
     */
    private void printLegend()
    {
      List<String> legendItems = new ArrayList<>();

      if (used.minus)
        legendItems.add("-  del");
      if (used.plus)
        legendItems.add("+  add");
      if (used.space)
        legendItems.add("·  space");
      if (used.tab)
        legendItems.add(DisplayUtils.ARROW_RIGHT + "  tab");
      if (used.wrap)
        legendItems.add("↩  wrap");

      if (legendItems.isEmpty())
        return;

      output.add("");
      output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
      output.add(BOX_VERTICAL + " " + padRight("Legend", width - 4) + " " + BOX_VERTICAL);
      output.add(BOX_RIGHT_INTERSECTION + fillChar(BOX_HORIZONTAL, width - 2) + BOX_LEFT_INTERSECTION);

      String legendLine = String.join("    ", legendItems);
      output.add(BOX_VERTICAL + "  " + padRight(legendLine, width - 4) + BOX_VERTICAL);
      output.add(BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_BOTTOM_RIGHT);
    }

    /**
     * Renders the content lines of a hunk.
     *
     * @param hunk the diff hunk
     */
    private void renderHunkContent(DiffHunk hunk)
    {
      List<String> lineTypes = new ArrayList<>();
      List<String> lineContents = new ArrayList<>();

      for (String line : hunk.lines)
      {
        if (line.isEmpty())
          continue;
        if (line.startsWith("+"))
        {
          lineTypes.add("add");
          lineContents.add(line.substring(1));
        }
        else if (line.startsWith("-"))
        {
          lineTypes.add("del");
          lineContents.add(line.substring(1));
        }
        else if (line.startsWith(" "))
        {
          lineTypes.add("ctx");
          lineContents.add(line.substring(1));
        }
        else if (line.equals("\\ No newline at end of file"))
          continue;
      }

      int oldLine = hunk.oldStart;
      int newLine = hunk.newStart;
      int i = 0;
      int numLines = lineTypes.size();

      while (i < numLines)
      {
        String ltype = lineTypes.get(i);
        String lcontent = lineContents.get(i);

        if (ltype.equals("ctx"))
        {
          printRow(String.valueOf(oldLine), " ", String.valueOf(newLine), lcontent);
          ++oldLine;
          ++newLine;
          ++i;
        }
        else if (ltype.equals("del"))
        {
          used.minus = true;

          // Check for adjacent add for whitespace highlighting
          if (i + 1 < numLines && lineTypes.get(i + 1).equals("add"))
          {
            String delContent = lcontent;
            String addContent = lineContents.get(i + 1);

            // Check if whitespace-only change
            if (isWhitespaceOnlyChange(delContent, addContent))
            {
              String delVis = visualizeWhitespace(delContent);
              String addVis = visualizeWhitespace(addContent);
              printRow(String.valueOf(oldLine), "-", "", delVis);
              ++oldLine;
              ++i;
              used.plus = true;
              printRow("", "+", String.valueOf(newLine), addVis);
              ++newLine;
              ++i;
            }
            else
            {
              // Show full lines without inline highlighting
              printRow(String.valueOf(oldLine), "-", "", delContent);
              ++oldLine;
              ++i;
              used.plus = true;
              printRow("", "+", String.valueOf(newLine), addContent);
              ++newLine;
              ++i;
            }
          }
          else
          {
            // Just a deletion
            printRow(String.valueOf(oldLine), "-", "", lcontent);
            ++oldLine;
            ++i;
          }
        }
        else if (ltype.equals("add"))
        {
          used.plus = true;
          printRow("", "+", String.valueOf(newLine), lcontent);
          ++newLine;
          ++i;
        }
        else
          ++i;
      }
    }

    /**
     * Checks if a change is whitespace-only.
     *
     * @param oldLine the old line
     * @param newLine the new line
     * @return true if only whitespace changed
     */
    private boolean isWhitespaceOnlyChange(String oldLine, String newLine)
    {
      return oldLine.replace(" ", "").replace("\t", "").
               equals(newLine.replace(" ", "").replace("\t", ""));
    }

    /**
     * Makes whitespace visible with markers.
     *
     * @param line the line to process
     * @return the line with visible whitespace
     */
    private String visualizeWhitespace(String line)
    {
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < line.length(); ++i)
      {
        char c = line.charAt(i);
        if (c == '\t')
        {
          result.append(DisplayUtils.ARROW_RIGHT);
          used.tab = true;
        }
        else if (c == ' ')
        {
          result.append('·');
          used.space = true;
        }
        else
          result.append(c);
      }
      return result.toString();
    }

    /**
     * Generates repeated characters.
     *
     * @param ch the character to repeat
     * @param count the repeat count
     * @return the repeated string
     */
    private String fillChar(char ch, int count)
    {
      if (count <= 0)
        return "";
      return String.valueOf(ch).repeat(count);
    }

    /**
     * Pads number/string to width (right-aligned).
     *
     * @param num the value to pad
     * @param width the target width
     * @return the padded string
     */
    private String padNum(String num, int width)
    {
      int len = num.length();
      if (len >= width)
        return num;
      return " ".repeat(width - len) + num;
    }

    /**
     * Pads string to width (left-aligned).
     *
     * @param text the text to pad
     * @param width the target width
     * @return the padded string
     */
    private String padRight(String text, int width)
    {
      int textWidth = display.displayWidth(text);
      if (textWidth >= width)
        return text;
      return text + " ".repeat(width - textWidth);
    }

    /**
     * Truncates string to target display width.
     *
     * @param text the text to truncate
     * @param targetWidth the target width
     * @return the truncated string
     */
    private String truncateToWidth(String text, int targetWidth)
    {
      StringBuilder result = new StringBuilder();
      int width = 0;
      for (int i = 0; i < text.length(); ++i)
      {
        char c = text.charAt(i);
        int charWidth = 1;
        if (width + charWidth > targetWidth)
          break;
        result.append(c);
        width += charWidth;
      }
      return result.toString();
    }
  }

  /**
   * Entry point for command-line invocation.
   * <p>
   * Generates rendered diff output for the current project directory.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetRenderDiffOutput generator = new GetRenderDiffOutput(scope);
      String output = generator.getOutput();
      if (output != null)
        System.out.print(output);
    }
    catch (RuntimeException e)
    {
      System.err.println("Error generating diff: " + e.getMessage());
      System.exit(1);
    }
  }
}
