package io.github.cowwoc.cat.hooks.ask;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AskHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Warn when presenting approval gate with unsquashed commits (M199/M224).
 * <p>
 * This handler detects when an approval gate is being presented during /cat:work
 * and warns if commits haven't been squashed yet.
 * <p>
 * M224: Also checks main workspace for recent task commits that should be squashed.
 */
public final class WarnUnsquashedApproval implements AskHandler
{
  /**
   * Creates a new WarnUnsquashedApproval handler.
   */
  public WarnUnsquashedApproval()
  {
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    String toolInputText = toolInput.toString();
    if (!containsApprove(toolInputText))
      return Result.allow();

    Path gitDir;
    try
    {
      gitDir = GitCommands.getGitDir();
    }
    catch (IOException _)
    {
      return Result.withContext("Failed to determine git directory for unsquashed commit check.");
    }

    Path catBaseFile = gitDir.resolve("cat-base");
    if (Files.exists(catBaseFile))
      return checkWorktreeCommits(catBaseFile);

    return checkMainWorkspaceCommits();
  }

  /**
   * Check if the tool input contains "approve" (case-insensitive).
   *
   * @param toolInputText the tool input as text
   * @return true if "approve" is found
   */
  private boolean containsApprove(String toolInputText)
  {
    return toolInputText.toLowerCase(Locale.ROOT).contains("approve");
  }


  /**
   * Check commits in a task worktree.
   *
   * @param catBaseFile the cat-base file containing the base branch name
   * @return the check result
   * @throws RuntimeException if reading cat-base fails or git output is invalid
   */
  private Result checkWorktreeCommits(Path catBaseFile)
  {
    try
    {
      String baseBranch = Files.readString(catBaseFile).trim();
      if (baseBranch.isEmpty())
        return Result.allow();

      ProcessRunner.Result result = ProcessRunner.run(
        "git", "rev-list", "--count", baseBranch + "..HEAD");

      if (result.exitCode() == 0 && !result.stdout().isEmpty())
      {
        int commitCount = Integer.parseInt(result.stdout().trim());
        if (commitCount > 2)
        {
          String warning = "⚠️ PRE-APPROVAL CHECK FAILED: UNSQUASHED COMMITS (M199)\n" +
                           "\n" +
                           "Found " + commitCount + " commits on task branch (expected 1-2 after squashing).\n" +
                           "\n" +
                           "BLOCKING: Run /cat:git-squash BEFORE presenting approval gate.";
          return Result.withContext(warning);
        }
      }
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }
    return Result.allow();
  }

  /**
   * Check commits in main workspace (M224).
   * <p>
   * If last 2+ commits are related (same task or config/planning pair), they should be squashed.
   *
   * @return the check result
   */
  private Result checkMainWorkspaceCommits()
  {
    ProcessRunner.Result result = ProcessRunner.run("git", "log", "--oneline", "-2");
    if (result.exitCode() != 0)
      return Result.allow();

    String[] lines = result.stdout().split("\n");
    if (lines.length < 2)
      return Result.allow();

    String firstCommit = lines[0];
    String secondCommit = lines[1];

    if (isConfigOrPlanningWithProgress(firstCommit) && isImplementationCommit(secondCommit))
    {
      String warning = "⚠️ PRE-APPROVAL CHECK: RELATED COMMITS SHOULD BE SQUASHED (M224)\n" +
                       "\n" +
                       "Found separate commits that should be combined:\n" +
                       "  " + firstCommit + "\n" +
                       "  " + secondCommit + "\n" +
                       "\n" +
                       "The implementation commit and STATE.md update should be in the SAME commit.\n" +
                       "Squash these commits before approval.";
      return Result.withContext(warning);
    }
    return Result.allow();
  }

  /**
   * Check if commit message is a config/planning commit with "progress".
   *
   * @param commitLine the commit line from git log --oneline
   * @return true if it matches the pattern
   */
  private boolean isConfigOrPlanningWithProgress(String commitLine)
  {
    return commitLine.matches("^[a-f0-9]+ (config|planning):.*progress.*");
  }

  /**
   * Check if commit message is an implementation commit.
   *
   * @param commitLine the commit line from git log --oneline
   * @return true if it matches the pattern
   */
  private boolean isImplementationCommit(String commitLine)
  {
    return commitLine.matches("^[a-f0-9]+ (feature|bugfix|refactor|test|docs):.*");
  }
}
