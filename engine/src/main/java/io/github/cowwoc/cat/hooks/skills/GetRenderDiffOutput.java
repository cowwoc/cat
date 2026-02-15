/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.GitCommands;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Output generator for render-diff skill.
 * <p>
 * Pre-computes rendered diff output for approval gates so the agent
 * can display it directly without visible Bash tool invocations.
 * <p>
 * Parses unified diff format and renders a 2-column table with:
 * Line number | Indicator + Content
 * <p>
 * <b>Known Limitations:</b>
 * <ul>
 *   <li>Modification pairing assumes single-line changes. When multiple consecutive deletions
 *   are followed by multiple consecutive additions, the pairing is sequential (first deletion
 *   with first addition, etc.), which may not reflect the actual semantic relationship.</li>
 * </ul>
 */
public final class GetRenderDiffOutput
{
  /**
   * Maximum number of files to display in the summary.
   */
  private static final int MAX_FILES_DISPLAYED = 20;
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetRenderDiffOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetRenderDiffOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Git helper methods for diff operations.
   */
  private static final class GitHelper
  {
    private static final Pattern STAT_FILES_PATTERN = Pattern.compile("(\\d+) files? changed");
    private static final Pattern STAT_INS_PATTERN = Pattern.compile("(\\d+) insertions?\\(\\+\\)");
    private static final Pattern STAT_DEL_PATTERN = Pattern.compile("(\\d+) deletions?\\(-\\)");

    /**
     * Checks if a branch exists in the repository.
     *
     * @param projectRoot the project root path
     * @param branch the branch name to check
     * @return true if the branch exists
     * @throws NullPointerException if {@code projectRoot} or {@code branch} are null
     */
    static boolean branchExists(Path projectRoot, String branch)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();
      requireThat(branch, "branch").isNotNull();

      try
      {
        GitCommands.runGitCommandInDirectory(projectRoot.toString(), "rev-parse", "--verify", branch);
        return true;
      }
      catch (IOException _)
      {
        return false;
      }
    }

    /**
     * Gets the list of changed files between base and HEAD.
     *
     * @param projectRoot the project root path
     * @param baseBranch the base branch for comparison
     * @return the list of changed file paths
     * @throws NullPointerException if {@code projectRoot} or {@code baseBranch} are null
     */
    static List<String> getChangedFiles(Path projectRoot, String baseBranch)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();
      requireThat(baseBranch, "baseBranch").isNotNull();

      try
      {
        String output = GitCommands.runGitCommandInDirectory(projectRoot.toString(), "diff", "--name-only",
          baseBranch + "..HEAD");
        List<String> result = new ArrayList<>();
        for (String line : output.split("\n"))
        {
          if (!line.isEmpty())
            result.add(line);
        }
        return result;
      }
      catch (IOException _)
      {
        return List.of();
      }
    }

    /**
     * Gets diff statistics between base and HEAD.
     *
     * @param projectRoot the project root path
     * @param baseBranch the base branch for comparison
     * @return the diff statistics
     * @throws NullPointerException if {@code projectRoot} or {@code baseBranch} are null
     */
    static DiffStats getDiffStats(Path projectRoot, String baseBranch)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();
      requireThat(baseBranch, "baseBranch").isNotNull();

      int filesChanged = 0;
      int insertions = 0;
      int deletions = 0;

      try
      {
        String output = GitCommands.runGitCommandInDirectory(projectRoot.toString(), "diff", "--stat",
          baseBranch + "..HEAD");
        if (!output.isEmpty())
        {
          String[] lines = output.split("\n");
          if (lines.length > 0)
          {
            String summary = lines[lines.length - 1];
            Matcher filesMatcher = STAT_FILES_PATTERN.matcher(summary);
            Matcher insMatcher = STAT_INS_PATTERN.matcher(summary);
            Matcher delMatcher = STAT_DEL_PATTERN.matcher(summary);

            if (filesMatcher.find())
              filesChanged = Integer.parseInt(filesMatcher.group(1));
            if (insMatcher.find())
              insertions = Integer.parseInt(insMatcher.group(1));
            if (delMatcher.find())
              deletions = Integer.parseInt(delMatcher.group(1));
          }
        }
      }
      catch (IOException _)
      {
        // Return empty stats on error
      }
      return new DiffStats(filesChanged, insertions, deletions);
    }

    /**
     * Gets the current branch name for a directory.
     *
     * @param projectRoot the project root path
     * @return the branch name, or null on error
     * @throws NullPointerException if {@code projectRoot} is null
     */
    static String getCurrentBranch(Path projectRoot)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();

      try
      {
        return GitCommands.runGitCommandSingleLineInDirectory(projectRoot.toString(), "rev-parse", "--abbrev-ref",
          "HEAD");
      }
      catch (IOException _)
      {
        return null;
      }
    }

    /**
     * Gets the upstream tracking branch for a directory.
     *
     * @param projectRoot the project root path
     * @return the upstream branch name, or null on error
     * @throws NullPointerException if {@code projectRoot} is null
     */
    static String getUpstreamBranch(Path projectRoot)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();

      try
      {
        return GitCommands.runGitCommandSingleLineInDirectory(projectRoot.toString(), "rev-parse", "--abbrev-ref",
          "@{upstream}");
      }
      catch (IOException _)
      {
        return null;
      }
    }

    /**
     * Gets the raw diff output between base and HEAD.
     *
     * @param projectRoot the project root path
     * @param baseBranch the base branch for comparison
     * @return the raw diff output, or null on error
     * @throws NullPointerException if {@code projectRoot} or {@code baseBranch} are null
     */
    static String getRawDiff(Path projectRoot, String baseBranch)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();
      requireThat(baseBranch, "baseBranch").isNotNull();

      try
      {
        return GitCommands.runGitCommandInDirectory(projectRoot.toString(), "diff", baseBranch + "..HEAD");
      }
      catch (IOException _)
      {
        return null;
      }
    }
  }

  /**
   * Handles base branch detection logic.
   */
  private static final class BaseBranchDetector
  {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+)-");
    private static final Pattern VERSION_BRANCH_PATTERN = Pattern.compile("^v\\d+\\.\\d+$");

    /**
     * Detects the base branch for diff comparison.
     *
     * @param projectRoot the project root path
     * @return the base branch name, or null if not detected
     * @throws NullPointerException if {@code projectRoot} is null
     */
    static String detectBaseBranch(Path projectRoot)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();

      Logger log = LoggerFactory.getLogger(GetRenderDiffOutput.class);
      try
      {
        String worktreeBase = detectFromWorktreePath();
        if (worktreeBase != null)
          return worktreeBase;

        String branchBase = detectFromBranchName(projectRoot);
        if (branchBase != null)
          return branchBase;

        String upstreamBase = detectFromUpstream(projectRoot);
        if (upstreamBase != null)
          return upstreamBase;
      }
      catch (Exception e)
      {
        log.debug("Failed to detect base branch", e);
      }

      return "main";
    }

    /**
     * Detects base branch from worktree directory path.
     *
     * @return the base branch name, or null if not in a worktree
     */
    private static String detectFromWorktreePath()
    {
      Path cwd = Path.of(System.getProperty("user.dir"));
      String worktreeName = cwd.getFileName().toString();

      if (cwd.getParent() != null && "worktrees".equals(cwd.getParent().getFileName().toString()))
      {
        Matcher match = VERSION_PATTERN.matcher(worktreeName);
        if (match.find())
          return "v" + match.group(1);
      }
      return null;
    }

    /**
     * Detects base branch from current branch name.
     *
     * @param projectRoot the project root path
     * @return the base branch name, or null if not detected
     * @throws NullPointerException if {@code projectRoot} is null
     */
    private static String detectFromBranchName(Path projectRoot)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();

      String branch = GitHelper.getCurrentBranch(projectRoot);
      if (branch != null)
      {
        Matcher match = VERSION_PATTERN.matcher(branch);
        if (match.find())
          return "v" + match.group(1);

        if (VERSION_BRANCH_PATTERN.matcher(branch).matches())
          return "main";
      }
      return null;
    }

    /**
     * Detects base branch from upstream tracking branch.
     *
     * @param projectRoot the project root path
     * @return the base branch name, or null if not detected
     * @throws NullPointerException if {@code projectRoot} is null
     */
    private static String detectFromUpstream(Path projectRoot)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();

      String upstream = GitHelper.getUpstreamBranch(projectRoot);
      if (upstream != null && upstream.contains("/"))
        return upstream.substring(upstream.indexOf('/') + 1);
      if (upstream != null)
        return upstream;
      return null;
    }
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
    return getOutput(scope.getClaudeProjectDir());
  }

  /**
   * Pre-compute rendered diff for approval gates.
   *
   * @param projectRoot the project root path
   * @return the formatted diff output, or null on error
   * @throws NullPointerException if {@code projectRoot} is null
   */
  public String getOutput(Path projectRoot)
  {
    requireThat(projectRoot, "projectRoot").isNotNull();

    // Load config for terminal width
    Config config = Config.load(scope.getJsonMapper(), projectRoot);
    int terminalWidth = config.getInt("terminalWidth", 50);

    // Detect base branch
    String baseBranch = BaseBranchDetector.detectBaseBranch(projectRoot);
    if (baseBranch == null)
      return "Base branch could not be detected from current directory or branch name.";

    // Check if base branch exists
    if (!GitHelper.branchExists(projectRoot, baseBranch))
    {
      // Try with origin/ prefix
      baseBranch = "origin/" + baseBranch;
      if (!GitHelper.branchExists(projectRoot, baseBranch))
        return "Base branch not found in repository (tried local and origin/ prefix).";
    }

    // Get changed files list
    List<String> changedFiles = GitHelper.getChangedFiles(projectRoot, baseBranch);
    if (changedFiles.isEmpty())
      return "No changes detected between " + baseBranch + " and HEAD.";

    // Get diff stats
    DiffStats stats = GitHelper.getDiffStats(projectRoot, baseBranch);

    // Get raw diff output
    String rawDiff = GitHelper.getRawDiff(projectRoot, baseBranch);
    if (rawDiff == null || rawDiff.isEmpty())
      return "No diff output available (git diff command failed or returned empty).";


    // Parse and render diff in Java
    DiffParser parser = new DiffParser();
    ParsedDiff diff = parser.parse(rawDiff);

    if (diff.hunks.isEmpty() && diff.binaryFiles.isEmpty() && diff.renamedFiles.isEmpty())
      return "No parseable changes found.";

    DiffRenderer renderer = new DiffRenderer(terminalWidth, scope.getDisplayUtils());
    String rendered = renderer.render(diff);

    String fileList = buildFileSummary(changedFiles);
    return buildOutputString(baseBranch, stats, rendered, fileList);
  }

  /**
   * Builds a summary list of changed files.
   *
   * @param changedFiles the list of changed file paths
   * @return the formatted file list
   */
  private String buildFileSummary(List<String> changedFiles)
  {
    StringBuilder fileList = new StringBuilder(64);
    int count = 0;
    for (String file : changedFiles)
    {
      if (count >= MAX_FILES_DISPLAYED)
      {
        fileList.append("\n  ... and ").append(changedFiles.size() - MAX_FILES_DISPLAYED).append(" more files");
        break;
      }
      if (count > 0)
        fileList.append('\n');
      fileList.append("  - ").append(file);
      ++count;
    }
    return fileList.toString();
  }

  /**
   * Builds the final output string with summary, file list, and rendered diff.
   *
   * @param baseBranch the base branch name
   * @param stats the diff statistics
   * @param rendered the rendered diff content
   * @param fileList the formatted file list
   * @return the complete output string
   */
  private String buildOutputString(String baseBranch, DiffStats stats, String rendered, String fileList)
  {
    return "## Diff Summary\n" +
           "- **Base branch:** " + baseBranch + "\n" +
           "- **Files changed:** " + stats.filesChanged + "\n" +
           "- **Insertions:** +" + stats.insertions + "\n" +
           "- **Deletions:** -" + stats.deletions + "\n" +
           "\n" +
           "## Changed Files\n" + fileList + "\n" +
           "\n" +
           "## Rendered Diff (2-column format)\n" +
           "\n" + rendered;
  }


  /**
   * Holds diff statistics.
   *
   * @param filesChanged the number of files changed
   * @param insertions the number of insertions
   * @param deletions the number of deletions
   */
  private record DiffStats(int filesChanged, int insertions, int deletions)
  {
  }

  /**
   * Represents a git commit.
   *
   * @param hash the commit hash
   * @param subject the commit subject line
   * @param body the commit body
   */
  private record Commit(String hash, String subject, String body)
  {
    /**
     * Creates a commit.
     *
     * @param hash the commit hash
     * @param subject the commit subject line
     * @param body the commit body
     * @throws NullPointerException if {@code hash}, {@code subject}, or {@code body} are null
     */
    Commit
    {
      requireThat(hash, "hash").isNotNull();
      requireThat(subject, "subject").isNotNull();
      requireThat(body, "body").isNotNull();
    }
  }

  /**
   * Represents a single diff hunk.
   *
   * @param file the file name
   * @param oldStart the old file start line
   * @param newStart the new file start line
   * @param context the hunk context (function name)
   * @param lines the parsed lines in this hunk
   * @param commit the commit this hunk belongs to (may be null)
   */
  private record DiffHunk(String file, int oldStart, int newStart, String context, List<ParsedLine> lines,
    Commit commit)
  {
    /**
     * Creates a diff hunk.
     *
     * @param file the file name
     * @param oldStart the old file start line
     * @param newStart the new file start line
     * @param context the hunk context (function name)
     * @param lines the parsed lines in this hunk
     * @param commit the commit this hunk belongs to (may be null)
     * @throws NullPointerException if {@code file}, {@code context}, or {@code lines} are null
     */
    DiffHunk
    {
      requireThat(file, "file").isNotNull();
      requireThat(context, "context").isNotNull();
      requireThat(lines, "lines").isNotNull();
      lines = List.copyOf(lines);
    }
  }

  /**
   * Parsed diff data.
   *
   * @param hunks the diff hunks
   * @param binaryFiles the binary file paths
   * @param renamedFiles mapping from new path to old path for renamed files
   * @param commits the commits in the diff
   * @param fileToCommit mapping from file path to commit
   */
  private record ParsedDiff(List<DiffHunk> hunks, List<String> binaryFiles, Map<String, String> renamedFiles,
    List<Commit> commits, Map<String, Commit> fileToCommit)
  {
    /**
     * Creates parsed diff data.
     *
     * @param hunks the diff hunks
     * @param binaryFiles the binary file paths
     * @param renamedFiles mapping from new path to old path for renamed files
     * @param commits the commits in the diff
     * @param fileToCommit mapping from file path to commit
     * @throws NullPointerException if any parameter is null
     */
    ParsedDiff
    {
      requireThat(hunks, "hunks").isNotNull();
      requireThat(binaryFiles, "binaryFiles").isNotNull();
      requireThat(renamedFiles, "renamedFiles").isNotNull();
      requireThat(commits, "commits").isNotNull();
      requireThat(fileToCommit, "fileToCommit").isNotNull();
      hunks = List.copyOf(hunks);
      binaryFiles = List.copyOf(binaryFiles);
      renamedFiles = Map.copyOf(renamedFiles);
      commits = List.copyOf(commits);
      fileToCommit = Map.copyOf(fileToCommit);
    }
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
     * @throws NullPointerException if {@code diffText} is null
     */
    ParsedDiff parse(String diffText)
    {
      requireThat(diffText, "diffText").isNotNull();

      ParserState state = new ParserState();

      String[] lines = diffText.split("\n");
      for (String line : lines)
      {
        if (parseCommitLine(line, state))
          continue;
        if (parseDiffHeader(line, state))
          continue;
        parseHunkContent(line, state);
      }

      // Finalize any remaining commit message
      if (state.currentCommitHash != null && !state.commitMessageLines.isEmpty())
      {
        CommitMessageParts parts = extractCommitMessage(state.commitMessageLines);
        Commit commit = new Commit(state.currentCommitHash, parts.subject(), parts.body());
        state.commits.add(commit);
      }

      // Save final hunk (with pairing)
      if (state.currentHunkFile != null && !state.currentHunkLines.isEmpty())
      {
        List<ParsedLine> parsedLines = parseAndPairLines(state.currentHunkLines);
        DiffHunk hunk = new DiffHunk(state.currentHunkFile, state.currentHunkOldStart,
          state.currentHunkNewStart, state.currentHunkContext, parsedLines, state.lastCommit);
        state.hunks.add(hunk);
      }

      return new ParsedDiff(state.hunks, state.binaryFiles, state.renamedFiles, state.commits,
        state.fileToCommit);
    }

    /**
     * Extracts subject and body from commit message lines.
     *
     * @param subject the commit subject line
     * @param body the commit body
     */
    private record CommitMessageParts(String subject, String body)
    {
    }

    /**
     * Extracts subject and body from commit message lines.
     *
     * @param lines the message lines
     * @return the commit message parts
     */
    private CommitMessageParts extractCommitMessage(List<String> lines)
    {
      List<String> trimmedLines = new ArrayList<>(lines);

      // Remove leading/trailing empty lines
      while (!trimmedLines.isEmpty() && trimmedLines.get(0).isBlank())
        trimmedLines.remove(0);
      while (!trimmedLines.isEmpty() && trimmedLines.get(trimmedLines.size() - 1).isBlank())
        trimmedLines.remove(trimmedLines.size() - 1);

      if (trimmedLines.isEmpty())
        return new CommitMessageParts("", "");

      // First non-empty line is subject
      String subject = trimmedLines.get(0).strip();

      // Rest is body
      String body = "";
      if (trimmedLines.size() > 1)
      {
        List<String> bodyLines = new ArrayList<>(trimmedLines.subList(1, trimmedLines.size()));
        while (!bodyLines.isEmpty() && bodyLines.get(0).isBlank())
          bodyLines.remove(0);
        body = String.join("\n", bodyLines);
      }

      return new CommitMessageParts(subject, body);
    }

    /**
     * Parses raw diff lines and pairs adjacent DELETION+ADDITION into MODIFICATION.
     *
     * @param rawLines the raw diff lines (with +/- prefixes)
     * @return the parsed and paired lines
     */
    private List<ParsedLine> parseAndPairLines(List<String> rawLines)
    {
      List<ParsedLine> firstPass = new ArrayList<>();

      for (String line : rawLines)
      {
        if (line.isEmpty())
          continue;
        if (line.startsWith("+"))
          firstPass.add(new ParsedLine(LineType.ADDITION, line.substring(1)));
        else if (line.startsWith("-"))
          firstPass.add(new ParsedLine(LineType.DELETION, line.substring(1)));
        else if (line.startsWith(" "))
          firstPass.add(new ParsedLine(LineType.CONTEXT, line.substring(1)));
        else if (line.equals("\\ No newline at end of file"))
          continue;
      }

      // Second pass: pair adjacent DELETION+ADDITION into MODIFICATION
      List<ParsedLine> result = new ArrayList<>();
      int i = 0;
      while (i < firstPass.size())
      {
        ParsedLine current = firstPass.get(i);
        if (current.type() == LineType.DELETION && i + 1 < firstPass.size())
        {
          ParsedLine next = firstPass.get(i + 1);
          if (next.type() == LineType.ADDITION)
          {
            // Pair them as a modification
            ModificationPair pair = new ModificationPair(current.content(), next.content());
            result.add(new ParsedLine(pair));
            i += 2;
            continue;
          }
        }
        result.add(current);
        ++i;
      }

      return result;
    }

    /**
     * Parses commit-related lines.
     *
     * @param line the current line
     * @param state the parser state
     * @return true if line was handled
     */
    private boolean parseCommitLine(String line, ParserState state)
    {
      Matcher match = COMMIT_HEADER.matcher(line);
      if (match.matches())
        return handleCommitHeader(match, state);

      if (shouldSkipMetadataLine(line, state))
        return skipMetadataLine(line, state);

      return shouldCollectCommitMessage(state) && collectCommitMessageLine(line, state);
    }

    /**
     * Handles a commit header line.
     *
     * @param match the regex match result
     * @param state the parser state
     * @return true (line was handled)
     */
    private boolean handleCommitHeader(Matcher match, ParserState state)
    {
      // Finalize previous commit message if any
      if (state.currentCommitHash != null && !state.commitMessageLines.isEmpty())
      {
        CommitMessageParts parts = extractCommitMessage(state.commitMessageLines);
        Commit commit = new Commit(state.currentCommitHash, parts.subject(), parts.body());
        state.commits.add(commit);
        state.lastCommit = commit;
        state.commitMessageLines.clear();
      }

      // Save previous hunk if any
      if (state.currentHunkFile != null && !state.currentHunkLines.isEmpty())
      {
        List<ParsedLine> parsedLines = parseAndPairLines(state.currentHunkLines);
        DiffHunk hunk = new DiffHunk(state.currentHunkFile, state.currentHunkOldStart,
          state.currentHunkNewStart, state.currentHunkContext, parsedLines, state.lastCommit);
        state.hunks.add(hunk);
        state.currentHunkFile = null;
        state.currentHunkLines.clear();
      }

      String commitHash = match.group(1);
      state.currentCommitHash = commitHash;
      state.inCommitMessage = false;
      state.inDiffContent = false;
      return true;
    }

    /**
     * Checks if a line should be skipped as metadata.
     *
     * @param line the current line
     * @param state the parser state
     * @return true if line should be skipped
     */
    private boolean shouldSkipMetadataLine(String line, ParserState state)
    {
      return state.currentCommitHash != null && !state.inCommitMessage && !state.inDiffContent &&
             (line.startsWith("Author:") || line.startsWith("Date:") ||
              line.startsWith("Merge:") || line.isBlank());
    }

    /**
     * Skips a metadata line after commit header.
     *
     * @param line the current line
     * @param state the parser state
     * @return true (line was handled)
     */
    private boolean skipMetadataLine(String line, ParserState state)
    {
      if (line.isBlank() && state.commitMessageLines.isEmpty())
        state.inCommitMessage = true;
      return true;
    }

    /**
     * Checks if we should collect this line as part of commit message.
     *
     * @param state the parser state
     * @return true if we're collecting commit message
     */
    private boolean shouldCollectCommitMessage(ParserState state)
    {
      return state.inCommitMessage && state.currentCommitHash != null && !state.inDiffContent;
    }

    /**
     * Collects a commit message line.
     *
     * @param line the current line
     * @param state the parser state
     * @return true if line was collected
     */
    private boolean collectCommitMessageLine(String line, ParserState state)
    {
      if (line.startsWith("    "))
      {
        state.commitMessageLines.add(line.substring(4));
        return true;
      }
      if (line.isBlank())
      {
        state.commitMessageLines.add("");
        return true;
      }

      // End of commit message - finalize it
      if (state.currentCommitHash != null && !state.commitMessageLines.isEmpty())
      {
        CommitMessageParts parts = extractCommitMessage(state.commitMessageLines);
        Commit commit = new Commit(state.currentCommitHash, parts.subject(), parts.body());
        state.commits.add(commit);
        state.lastCommit = commit;
        state.commitMessageLines.clear();
      }

      state.inCommitMessage = false;
      return false;
    }

    /**
     * Parses diff header lines (file header, rename, binary).
     *
     * @param line the current line
     * @param state the parser state
     * @return true if line was handled
     */
    private boolean parseDiffHeader(String line, ParserState state)
    {
      Matcher match = FILE_HEADER.matcher(line);
      if (match.matches())
        return handleFileHeader(match, state);

      if (handleRenameDetection(line, state))
        return true;

      if (handleBinaryFile(line, state))
        return true;

      if (shouldSkipMetadata(line))
        return true;

      match = HUNK_HEADER.matcher(line);
      return match.matches() && handleHunkHeader(match, state);
    }

    /**
     * Handles a file header line.
     *
     * @param match the regex match result
     * @param state the parser state
     * @return true (line was handled)
     */
    private boolean handleFileHeader(Matcher match, ParserState state)
    {
      state.inDiffContent = true;

      saveCurrentHunk(state);

      state.currentFile = match.group(2);
      state.renameFrom = "";
      if (state.lastCommit != null)
        state.fileToCommit.put(state.currentFile, state.lastCommit);
      return true;
    }

    /**
     * Handles rename detection lines.
     *
     * @param line the current line
     * @param state the parser state
     * @return true if line was a rename line
     */
    private boolean handleRenameDetection(String line, ParserState state)
    {
      Matcher match = RENAME_FROM.matcher(line);
      if (match.matches())
      {
        state.renameFrom = match.group(1);
        return true;
      }

      match = RENAME_TO.matcher(line);
      if (match.matches() && !state.renameFrom.isEmpty())
      {
        state.renamedFiles.put(state.currentFile, state.renameFrom);
        return true;
      }

      return false;
    }

    /**
     * Handles binary file detection.
     *
     * @param line the current line
     * @param state the parser state
     * @return true if line indicates binary file
     */
    private boolean handleBinaryFile(String line, ParserState state)
    {
      if (BINARY.matcher(line).find())
      {
        state.binaryFiles.add(state.currentFile);
        return true;
      }
      return false;
    }

    /**
     * Checks if a line should be skipped as metadata.
     *
     * @param line the current line
     * @return true if line is metadata
     */
    private boolean shouldSkipMetadata(String line)
    {
      return line.startsWith("index ") || line.startsWith("--- ") ||
             line.startsWith("+++ ") || line.startsWith("new file") ||
             line.startsWith("deleted file") || line.startsWith("similarity");
    }

    /**
     * Handles a hunk header line.
     *
     * @param match the regex match result
     * @param state the parser state
     * @return true (line was handled)
     */
    private boolean handleHunkHeader(Matcher match, ParserState state)
    {
      saveCurrentHunk(state);

      state.currentHunkFile = state.currentFile;
      state.currentHunkOldStart = Integer.parseInt(match.group(1));
      state.currentHunkNewStart = Integer.parseInt(match.group(2));
      state.currentHunkContext = match.group(3).strip();
      state.currentHunkLines = new ArrayList<>();
      return true;
    }

    /**
     * Saves the current hunk to the hunks list if present.
     *
     * @param state the parser state
     */
    private void saveCurrentHunk(ParserState state)
    {
      if (state.currentHunkFile != null && !state.currentHunkLines.isEmpty())
      {
        List<ParsedLine> parsedLines = parseAndPairLines(state.currentHunkLines);
        DiffHunk hunk = new DiffHunk(state.currentHunkFile, state.currentHunkOldStart,
          state.currentHunkNewStart, state.currentHunkContext, parsedLines, state.lastCommit);
        state.hunks.add(hunk);
        state.currentHunkFile = null;
        state.currentHunkLines.clear();
      }
    }

    /**
     * Parses hunk content lines.
     *
     * @param line the current line
     * @param state the parser state
     */
    private void parseHunkContent(String line, ParserState state)
    {
      if (state.currentHunkFile != null)
      {
        if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" "))
          state.currentHunkLines.add(line);
        else if (line.equals("\\ No newline at end of file"))
          state.currentHunkLines.add(line);
      }
    }

    /**
     * Mutable state for diff parser.
     */
    private static final class ParserState
    {
      String currentFile = "";
      String currentCommitHash;
      Commit lastCommit;
      String renameFrom = "";
      boolean inCommitMessage;
      boolean inDiffContent;
      final List<String> commitMessageLines = new ArrayList<>();

      // Current hunk being built
      String currentHunkFile;
      int currentHunkOldStart;
      int currentHunkNewStart;
      String currentHunkContext = "";
      List<String> currentHunkLines = new ArrayList<>();

      // Accumulated results
      final List<DiffHunk> hunks = new ArrayList<>();
      final List<String> binaryFiles = new ArrayList<>();
      final Map<String, String> renamedFiles = new HashMap<>();
      final List<Commit> commits = new ArrayList<>();
      final Map<String, Commit> fileToCommit = new HashMap<>();
    }
  }

  /**
   * Represents a parsed line type with its content.
   *
   * @param type the line type
   * @param content the line content (used for CONTEXT, DELETION, ADDITION)
   * @param modificationPair the modification pair (used for MODIFICATION, null otherwise)
   */
  private record ParsedLine(LineType type, String content, ModificationPair modificationPair)
  {
    /**
     * Creates a parsed line.
     *
     * @param type the line type
     * @param content the line content (used for CONTEXT, DELETION, ADDITION)
     * @param modificationPair the modification pair (used for MODIFICATION, null otherwise)
     * @throws NullPointerException if {@code type} is null
     * @throws NullPointerException if {@code content} is null and type is not MODIFICATION
     * @throws NullPointerException if {@code modificationPair} is null and type is MODIFICATION
     */
    ParsedLine
    {
      requireThat(type, "type").isNotNull();
      if (type == LineType.MODIFICATION)
        requireThat(modificationPair, "modificationPair").isNotNull();
      else
        requireThat(content, "content").isNotNull();
    }

    /**
     * Creates a non-modification parsed line.
     *
     * @param type the line type (CONTEXT, DELETION, or ADDITION)
     * @param content the line content
     */
    ParsedLine(LineType type, String content)
    {
      this(type, content, null);
    }

    /**
     * Creates a modification parsed line.
     *
     * @param modificationPair the modification pair
     */
    ParsedLine(ModificationPair modificationPair)
    {
      this(LineType.MODIFICATION, "", modificationPair);
    }
  }

  /**
   * Line type indicator for diff content.
   */
  private enum LineType
  {
    /**
     * Context line (unchanged).
     */
    CONTEXT,
    /**
     * Deletion line.
     */
    DELETION,
    /**
     * Addition line.
     */
    ADDITION,
    /**
     * Modification (paired deletion and addition).
     */
    MODIFICATION
  }

  /**
   * Represents a modification pair (deletion + addition).
   *
   * @param oldContent the deleted content
   * @param newContent the added content
   */
  private record ModificationPair(String oldContent, String newContent)
  {
    /**
     * Creates a modification pair.
     *
     * @param oldContent the deleted content
     * @param newContent the added content
     * @throws NullPointerException if {@code oldContent} or {@code newContent} are null
     */
    ModificationPair
    {
      requireThat(oldContent, "oldContent").isNotNull();
      requireThat(newContent, "newContent").isNotNull();
    }
  }

  /**
   * Immutable state for rendering operations.
   *
   * @param firstBox whether this is the first box being rendered
   * @param currentCommit the current commit being rendered (may be null)
   */
  private record RenderState(boolean firstBox, Commit currentCommit)
  {
  }

  /**
   * Renders diff in 2-column table format.
   * <p>
   * This class is nested within GetRenderDiffOutput to keep its private implementation
   * types (ParsedLine, LineType, DiffHunk, ParsedDiff, UsedSymbols, DisplayUtils)
   * encapsulated. Extracting it to a top-level class would require making all these
   * types package-private, exposing internal implementation details. The nesting is
   * an intentional architectural choice to maintain strong encapsulation.
   */
  private static class DiffRenderer
  {
    // Width of border characters and indicators: left│ (1) + mid│ (1) + 2 indicator chars (2) + right│ (1)
    private static final int BORDER_AND_INDICATOR_WIDTH = 5;

    private final int width;
    private final DisplayUtils display;
    private final UsedSymbols used = new UsedSymbols();
    private final List<String> output = new ArrayList<>();

    // Per-hunk state (set before rendering each hunk)
    private int colLine;
    private int contentWidth;

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
    }

    /**
     * Returns a substring that has at most the specified display width.
     *
     * @param text the text to substring
     * @param maxDisplayWidth the maximum display width
     * @param display the display utilities for width calculation
     * @return the substring with display width at most maxDisplayWidth
     */
    private static String substringByDisplayWidth(String text, int maxDisplayWidth, DisplayUtils display)
    {
      if (text.isEmpty())
        return "";

      int currentWidth = 0;
      int endIndex = 0;

      for (int i = 0; i < text.length(); ++i)
      {
        String nextChar = text.substring(i, i + 1);
        int charWidth = display.displayWidth(nextChar);

        if (currentWidth + charWidth > maxDisplayWidth)
          break;

        currentWidth += charWidth;
        endIndex = i + 1;
      }

      if (endIndex == 0 && !text.isEmpty())
        return text.substring(0, 1);

      return text.substring(0, endIndex);
    }

    /**
     * Handles box drawing operations.
     */
    private static final class BoxDrawer
    {
      private static final char BOX_TOP_LEFT = '╭';
      private static final char BOX_TOP_RIGHT = '╮';
      private static final char BOX_BOTTOM_LEFT = '╰';
      private static final char BOX_BOTTOM_RIGHT = '╯';
      private static final char BOX_HORIZONTAL = '─';
      private static final char BOX_VERTICAL = '│';
      private static final char BOX_T_DOWN = '┬';
      private static final char BOX_T_UP = '┴';
      private static final char BOX_RIGHT_INTERSECTION = '├';
      private static final char BOX_LEFT_INTERSECTION = '┤';
      private static final char BOX_CROSS = '┼';

      private final int width;
      private final DisplayUtils display;
      private final UsedSymbols used;
      private final List<String> output;
      private final int colLine;

      /**
       * Creates a box drawer.
       *
       * @param width the total box width
       * @param display the display utilities
       * @param used the used symbols tracker
       * @param output the output lines list
       * @param colLine the line number column width
       * @param contentWidth the content column width (unused but kept for API consistency)
       */
      BoxDrawer(int width, DisplayUtils display, UsedSymbols used, List<String> output, int colLine,
        int contentWidth)
      {
        this.width = width;
        this.display = display;
        this.used = used;
        this.output = output;
        this.colLine = colLine;
      }

      /**
       * Prints commit header box.
       *
       * @param commit the commit
       */
      void printCommitHeader(Commit commit)
      {
        String shortHash;
        if (commit.hash().length() >= 7)
          shortHash = commit.hash().substring(0, 7);
        else
          shortHash = commit.hash();
        String headerText = "COMMIT " + shortHash + ": " + commit.subject();
        int headerLen = display.displayWidth(headerText);

        // Truncate if too long
        if (headerLen > width - 4)
        {
          int maxSubjectLen = width - 4 - ("COMMIT " + shortHash + ": ").length() - 3;
          String truncatedSubject = commit.subject().substring(0, Math.max(0, maxSubjectLen)) + "...";
          headerText = "COMMIT " + shortHash + ": " + truncatedSubject;
          headerLen = display.displayWidth(headerText);
        }

        int padding = width - 4 - headerLen;

        output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
        output.add(BOX_VERTICAL + " " + headerText + " ".repeat(Math.max(0, padding)) + " " + BOX_VERTICAL);
        output.add(BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_BOTTOM_RIGHT);
      }

      /**
       * Prints hunk box top with filename embedded in border.
       *
       * @param filename the file name
       */
      void printHunkTop(String filename)
      {
        String fileText = " " + filename + " ";
        int available = getAvailableTextWidth();
        output.add(buildBorderWithText(fileText, BOX_TOP_LEFT, BOX_T_DOWN, BOX_TOP_RIGHT, available, "... "));
      }

      /**
       * Prints hunk separator with context.
       *
       * @param context the hunk context
       */
      void printHunkSeparator(String context)
      {
        String contextText;
        if (!context.isEmpty())
          contextText = " ⌁ " + context + " ";
        else
          contextText = " ";
        int available = getAvailableTextWidth();
        output.add(buildBorderWithText(contextText, BOX_RIGHT_INTERSECTION, BOX_CROSS,
          BOX_LEFT_INTERSECTION, available, "… "));
      }

      /**
       * Prints hunk box bottom.
       */
      void printHunkBottom()
      {
        output.add(
          BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, colLine) + BOX_T_UP +
          fillChar(BOX_HORIZONTAL, width - colLine - 2) + BOX_BOTTOM_RIGHT);
      }

      /**
       * Prints binary file box.
       *
       * @param filename the file name
       */
      void printBinaryFile(String filename)
      {
        String headerText = "FILE: " + filename + " (binary)";
        String contentText = "Binary file changed";
        printInfoBox(headerText, contentText);
      }

      /**
       * Prints renamed file box.
       *
       * @param oldPath the old file path
       * @param newPath the new file path
       */
      void printRenamedFile(String oldPath, String newPath)
      {
        String headerText = "FILE: " + oldPath + " " + DisplayUtils.ARROW_RIGHT + " " + newPath + " (renamed)";
        String contentText = "File renamed (no content changes)";
        printInfoBox(headerText, contentText);
      }

      /**
       * Prints legend box showing used symbols.
       */
      void printLegend()
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
       * Builds a border line with embedded text (used for hunk headers).
       *
       * @param text the text to embed
       * @param leftSymbol the left intersection symbol
       * @param midSymbol the middle intersection symbol
       * @param rightSymbol the right intersection symbol
       * @param maxTextWidth maximum display width for text
       * @param ellipsis ellipsis suffix if truncated
       * @return the formatted border line
       */
      private String buildBorderWithText(String text, char leftSymbol, char midSymbol, char rightSymbol,
        int maxTextWidth, String ellipsis)
      {
        int textLen = display.displayWidth(text);

        if (textLen > maxTextWidth)
        {
          String truncated = substringByDisplayWidth(text, maxTextWidth - ellipsis.length(), display);
          text = truncated + ellipsis;
          textLen = display.displayWidth(text);
        }

        String leftPart = leftSymbol + fillChar(BOX_HORIZONTAL, colLine) + midSymbol + BOX_HORIZONTAL;
        int rightDashes = width - leftPart.length() - textLen - 1;

        return leftPart + text + fillChar(BOX_HORIZONTAL, rightDashes) + rightSymbol;
      }


      /**
       * Prints an info box with header and content.
       *
       * @param headerText the header text
       * @param contentText the content text
       */
      private void printInfoBox(String headerText, String contentText)
      {
        int headerLen = display.displayWidth(headerText);
        int padding = width - 4 - headerLen;

        output.add(BOX_TOP_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_TOP_RIGHT);
        if (headerLen > width - 4)
        {
          String truncated = headerText.substring(0, Math.max(0, width - 7)) + "...";
          output.add(BOX_VERTICAL + " " + padRight(truncated, width - 4) + " " + BOX_VERTICAL);
        }
        else
          output.add(BOX_VERTICAL + " " + headerText + " ".repeat(Math.max(0, padding)) + " " + BOX_VERTICAL);
        output.add(BOX_RIGHT_INTERSECTION + fillChar(BOX_HORIZONTAL, width - 2) + BOX_LEFT_INTERSECTION);
        output.add(BOX_VERTICAL + " " + padRight(contentText, width - 4) + " " + BOX_VERTICAL);
        output.add(BOX_BOTTOM_LEFT + fillChar(BOX_HORIZONTAL, width - 2) + BOX_BOTTOM_RIGHT);
      }

      /**
       * Calculates available width for text in hunk headers.
       *
       * @return the available width
       */
      private int getAvailableTextWidth()
      {
        return width - 2 - colLine - 3;
      }

      /**
       * Generates repeated characters.
       *
       * @param ch the character to repeat
       * @param count the repeat count
       * @return the repeated string
       */
      String fillChar(char ch, int count)
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
      String padNum(String num, int width)
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
      String padRight(String text, int width)
      {
        int textWidth = display.displayWidth(text);
        if (textWidth >= width)
          return text;
        return text + " ".repeat(width - textWidth);
      }
    }

    /**
     * Handles content formatting and wrapping.
     */
    private static final class ContentFormatter
    {
      private final int contentWidth;
      private final DisplayUtils display;
      private final UsedSymbols used;
      private final List<String> output;
      private final int colLine;
      private final BoxDrawer boxDrawer;

      /**
       * Creates a content formatter.
       *
       * @param contentWidth the content column width
       * @param display the display utilities
       * @param used the used symbols tracker
       * @param output the output lines list
       * @param colLine the line number column width
       * @param boxDrawer the box drawer for rendering support
       */
      ContentFormatter(int contentWidth, DisplayUtils display, UsedSymbols used, List<String> output,
        int colLine, BoxDrawer boxDrawer)
      {
        this.contentWidth = contentWidth;
        this.display = display;
        this.used = used;
        this.output = output;
        this.colLine = colLine;
        this.boxDrawer = boxDrawer;
      }

      /**
       * Prints a content row, handling wrapping for long lines.
       *
       * @param lineNum Line number to display (0 for continuation lines)
       * @param indicator Two-character indicator ('- ', '+ ', or '  ')
       * @param content Line content
       */
      void printRow(int lineNum, String indicator, String content)
      {
        String lineStr;
        if (lineNum > 0)
          lineStr = String.valueOf(lineNum);
        else
          lineStr = "";

        List<String> wrappedSegments = wrapContent(content);

        // First segment with line number and indicator
        String firstSegment = wrappedSegments.get(0);
        output.add('│' + boxDrawer.padNum(lineStr, colLine) + '│' + indicator +
          firstSegment + '│');

        // Continuation lines (if any)
        for (int i = 1; i < wrappedSegments.size(); ++i)
        {
          output.add('│' + " ".repeat(colLine) + '│' + "  " +
            wrappedSegments.get(i) + '│');
        }
      }

      /**
       * Wraps content into segments that fit within contentWidth.
       *
       * @param content the content to wrap
       * @return list of wrapped segments (each padded or ending with wrap indicator)
       */
      private List<String> wrapContent(String content)
      {
        List<String> segments = new ArrayList<>();
        int contentLen = display.displayWidth(content);

        if (contentLen <= contentWidth)
        {
          // Fits on one line
          String paddedContent = content + " ".repeat(contentWidth - contentLen);
          segments.add(paddedContent);
          return segments;
        }

        // Need to wrap
        used.wrap = true;
        String remaining = content;

        // First segment
        String firstPart = substringByDisplayWidth(remaining, contentWidth - 1, display);
        segments.add(firstPart + "↩");
        remaining = remaining.substring(firstPart.length());

        // Continuation segments
        while (!remaining.isEmpty())
        {
          int partLen = display.displayWidth(remaining);
          if (partLen <= contentWidth)
          {
            String padded = remaining + " ".repeat(contentWidth - partLen);
            segments.add(padded);
            remaining = "";
          }
          else
          {
            String nextPart = substringByDisplayWidth(remaining, contentWidth - 1, display);
            segments.add(nextPart + "↩");
            remaining = remaining.substring(nextPart.length());
          }
        }

        return segments;
      }
    }

    /**
     * Handles whitespace visualization.
     */
    private static final class WhitespaceHandler
    {
      private final UsedSymbols used;

      /**
       * Creates a whitespace handler.
       *
       * @param used the used symbols tracker
       */
      WhitespaceHandler(UsedSymbols used)
      {
        this.used = used;
      }

      /**
       * Checks if a change is whitespace-only.
       *
       * @param oldLine the old line
       * @param newLine the new line
       * @return true if only whitespace changed
       */
      boolean isWhitespaceOnlyChange(String oldLine, String newLine)
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
      String visualizeWhitespace(String line)
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
    }

    /**
     * Calculates the line number column width for a hunk (max 4 digits = 9999).
     *
     * @param hunk the diff hunk
     * @return the column width
     */
    private int getWidth(DiffHunk hunk)
    {
      int maxLine = 0;
      int oldLine = hunk.oldStart();
      int newLine = hunk.newStart();

      for (ParsedLine line : hunk.lines())
      {
        switch (line.type())
        {
          case ADDITION ->
          {
            maxLine = Math.max(maxLine, newLine);
            ++newLine;
          }
          case DELETION ->
          {
            maxLine = Math.max(maxLine, oldLine);
            ++oldLine;
          }
          case CONTEXT ->
          {
            maxLine = Math.max(maxLine, newLine);
            ++oldLine;
            ++newLine;
          }
          case MODIFICATION ->
          {
            maxLine = Math.max(maxLine, Math.max(oldLine, newLine));
            ++oldLine;
            ++newLine;
          }
        }
      }

      // Return digit count, capped at 4
      return Math.min(4, Math.max(2, String.valueOf(maxLine).length()));
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
      RenderState state = new RenderState(true, null);

      state = renderBinaryFiles(diff, state);
      state = renderRenamedFiles(diff, state);
      renderHunks(diff, state);

      BoxDrawer legendDrawer = new BoxDrawer(width, display, used, output, 0, 0);
      legendDrawer.printLegend();

      return String.join("\n", output);
    }

    /**
     * Renders binary files section.
     *
     * @param diff the parsed diff
     * @param state the render state
     * @return the updated render state
     */
    private RenderState renderBinaryFiles(ParsedDiff diff, RenderState state)
    {
      boolean firstBox = state.firstBox();
      Commit currentCommit = state.currentCommit();
      BoxDrawer boxDrawer = new BoxDrawer(width, display, used, output, 0, 0);

      for (String file : diff.binaryFiles())
      {
        Commit commit = diff.fileToCommit().get(file);
        if (commit != null && commit != currentCommit)
        {
          if (!firstBox)
            output.add("");
          currentCommit = commit;
          boxDrawer.printCommitHeader(commit);
          firstBox = false;
        }
        if (!firstBox)
          output.add("");
        firstBox = false;
        boxDrawer.printBinaryFile(file);
      }

      return new RenderState(firstBox, currentCommit);
    }

    /**
     * Renders renamed files without content changes.
     *
     * @param diff the parsed diff
     * @param state the render state
     * @return the updated render state
     */
    private RenderState renderRenamedFiles(ParsedDiff diff, RenderState state)
    {
      boolean firstBox = state.firstBox();
      Commit currentCommit = state.currentCommit();
      BoxDrawer boxDrawer = new BoxDrawer(width, display, used, output, 0, 0);

      Set<String> filesWithHunks = new HashSet<>();
      for (DiffHunk hunk : diff.hunks())
        filesWithHunks.add(hunk.file());

      for (Map.Entry<String, String> entry : diff.renamedFiles().entrySet())
      {
        String newPath = entry.getKey();
        String oldPath = entry.getValue();
        if (!filesWithHunks.contains(newPath))
        {
          Commit commit = diff.fileToCommit().get(newPath);
          if (commit != null && commit != currentCommit)
          {
            if (!firstBox)
              output.add("");
            currentCommit = commit;
            boxDrawer.printCommitHeader(commit);
            firstBox = false;
          }
          if (!firstBox)
            output.add("");
          firstBox = false;
          boxDrawer.printRenamedFile(oldPath, newPath);
        }
      }

      return new RenderState(firstBox, currentCommit);
    }

    /**
     * Renders diff hunks.
     *
     * @param diff the parsed diff
     * @param state the render state
     * @return the updated render state
     */
    private RenderState renderHunks(ParsedDiff diff, RenderState state)
    {
      boolean firstBox = state.firstBox();
      Commit currentCommit = state.currentCommit();

      String currentFile = null;
      for (DiffHunk hunk : diff.hunks())
      {
        if (diff.binaryFiles().contains(hunk.file()))
          continue;

        // Set up per-hunk rendering state
        colLine = getWidth(hunk);
        contentWidth = width - colLine - BORDER_AND_INDICATOR_WIDTH;

        BoxDrawer boxDrawer = new BoxDrawer(width, display, used, output, colLine, contentWidth);
        ContentFormatter formatter = new ContentFormatter(contentWidth, display, used, output, colLine,
          boxDrawer);
        WhitespaceHandler whitespaceHandler = new WhitespaceHandler(used);

        if (hunk.commit() != null && hunk.commit() != currentCommit)
        {
          if (!firstBox)
            output.add("");
          currentCommit = hunk.commit();
          boxDrawer.printCommitHeader(hunk.commit());
          firstBox = false;
        }

        if (!firstBox)
          output.add("");
        firstBox = false;

        // First hunk for this file: print top border with filename
        if (!hunk.file().equals(currentFile))
        {
          currentFile = hunk.file();
          boxDrawer.printHunkTop(hunk.file());
          boxDrawer.printHunkSeparator(hunk.context());
        }
        else
        {
          // Subsequent hunk for same file: just separator
          boxDrawer.printHunkSeparator(hunk.context());
        }

        renderHunkContent(hunk, formatter, whitespaceHandler);
      }

      // Print bottom border for last hunk
      if (!diff.hunks().isEmpty())
      {
        BoxDrawer finalBoxDrawer = new BoxDrawer(width, display, used, output, colLine, contentWidth);
        finalBoxDrawer.printHunkBottom();
      }

      return new RenderState(firstBox, currentCommit);
    }

    /**
     * Renders the content lines of a hunk.
     *
     * @param hunk the diff hunk
     * @param formatter the content formatter
     * @param whitespaceHandler the whitespace handler
     */
    private void renderHunkContent(DiffHunk hunk, ContentFormatter formatter,
      WhitespaceHandler whitespaceHandler)
    {
      List<ParsedLine> parsedLines = hunk.lines();

      int oldLine = hunk.oldStart();
      int newLine = hunk.newStart();
      int i = 0;
      int numLines = parsedLines.size();

      while (i < numLines)
      {
        ParsedLine current = parsedLines.get(i);

        switch (current.type())
        {
          case CONTEXT ->
          {
            i = renderContextLine(parsedLines, i, newLine, formatter);
            ++oldLine;
            ++newLine;
          }
          case DELETION ->
          {
            i = renderDeletionLine(parsedLines, i, oldLine, formatter);
            ++oldLine;
          }
          case ADDITION ->
          {
            i = renderAdditionLine(parsedLines, i, newLine, formatter);
            ++newLine;
          }
          case MODIFICATION ->
          {
            i = renderModificationLine(parsedLines, i, oldLine, newLine, formatter, whitespaceHandler);
            ++oldLine;
            ++newLine;
          }
        }
      }
    }

    /**
     * Renders a context line.
     *
     * @param parsedLines all parsed lines
     * @param index current index
     * @param newLine new file line number
     * @param formatter the content formatter
     * @return the next index to process
     */
    private int renderContextLine(List<ParsedLine> parsedLines, int index, int newLine,
      ContentFormatter formatter)
    {
      ParsedLine line = parsedLines.get(index);
      formatter.printRow(newLine, "  ", line.content());
      return index + 1;
    }

    /**
     * Renders a deletion line.
     *
     * @param parsedLines all parsed lines
     * @param index current index
     * @param oldLine old file line number
     * @param formatter the content formatter
     * @return the next index to process
     */
    private int renderDeletionLine(List<ParsedLine> parsedLines, int index, int oldLine,
      ContentFormatter formatter)
    {
      used.minus = true;
      ParsedLine delLine = parsedLines.get(index);
      String delContent = delLine.content();
      formatter.printRow(oldLine, "- ", delContent);
      return index + 1;
    }

    /**
     * Renders a modification line (paired deletion and addition).
     *
     * @param parsedLines all parsed lines
     * @param index current index
     * @param oldLine old file line number
     * @param newLine new file line number
     * @param formatter the content formatter
     * @param whitespaceHandler the whitespace handler
     * @return the next index to process
     */
    private int renderModificationLine(List<ParsedLine> parsedLines, int index, int oldLine, int newLine,
      ContentFormatter formatter, WhitespaceHandler whitespaceHandler)
    {
      used.minus = true;
      used.plus = true;

      ParsedLine modLine = parsedLines.get(index);
      ModificationPair pair = modLine.modificationPair();
      String delContent = pair.oldContent();
      String addContent = pair.newContent();

      if (whitespaceHandler.isWhitespaceOnlyChange(delContent, addContent))
      {
        String delVis = whitespaceHandler.visualizeWhitespace(delContent);
        String addVis = whitespaceHandler.visualizeWhitespace(addContent);
        formatter.printRow(oldLine, "- ", delVis);
        formatter.printRow(newLine, "+ ", addVis);
      }
      else
      {
        formatter.printRow(oldLine, "- ", delContent);
        formatter.printRow(newLine, "+ ", addContent);
      }

      return index + 1;
    }

    /**
     * Renders an addition line.
     *
     * @param parsedLines all parsed lines
     * @param index current index
     * @param newLine new file line number
     * @param formatter the content formatter
     * @return the next index to process
     */
    private int renderAdditionLine(List<ParsedLine> parsedLines, int index, int newLine,
      ContentFormatter formatter)
    {
      used.plus = true;
      ParsedLine line = parsedLines.get(index);
      formatter.printRow(newLine, "+ ", line.content());
      return index + 1;
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
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetRenderDiffOutput.class);
      log.error("Unexpected error", e);
      System.err.println("Error generating diff: " + e.getMessage());
      System.exit(1);
    }
  }
}
