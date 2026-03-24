/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandSingleLineInDirectory;
import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Safe git amend with push status verification and TOCTOU race detection.
 * <p>
 * Implements: pre-check push status, perform amend, post-amend TOCTOU detection.
 * Equivalent to git-amend.sh.
 */
public final class GitAmend
{
  private final JvmScope scope;
  private final String directory;

  /**
   * Creates a new GitAmend instance.
   *
   * @param scope     the JVM scope providing JSON mapper
   * @param directory the working directory for git commands
   * @throws NullPointerException     if {@code scope} is null
   * @throws IllegalArgumentException if {@code directory} is blank
   */
  public GitAmend(JvmScope scope, String directory)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(directory, "directory").isNotBlank();
    this.scope = scope;
    this.directory = directory;
  }

  /**
   * Executes the safe amend operation.
   * <p>
   * The process:
   * <ol>
   *   <li>Record OLD_HEAD before amend</li>
   *   <li>Check push status — fail-fast if already pushed</li>
   *   <li>Perform git commit --amend with appropriate flags</li>
   *   <li>Post-amend TOCTOU check: detect if OLD_HEAD was pushed during amend</li>
   *   <li>Return JSON result (OK or RACE_DETECTED on success; ALREADY_PUSHED on conflict; block decision on error)</li>
   * </ol>
   *
   * @param message the new commit message, or empty string to use --no-edit or keep existing message
   * @param noEdit  if true, amend without opening editor (--no-edit flag)
   * @return JSON string with operation result; domain status for OK/RACE_DETECTED/ALREADY_PUSHED,
   *   or standard hook block decision on error
   * @throws NullPointerException if {@code message} is null
   * @throws IOException          if the operation fails
   */
  public String execute(String message, boolean noEdit) throws IOException
  {
    return execute(message, noEdit, () ->
    {
    });
  }

  /**
   * Executes the safe amend operation with an optional hook that runs after the amend completes
   * but before the TOCTOU race check.
   * <p>
   * The hook parameter allows tests to simulate external events (e.g., a concurrent push)
   * that occur during the race window between the amend and the post-amend verification.
   *
   * @param message    the new commit message, or empty string to use --no-edit or keep existing message
   * @param noEdit     if true, amend without opening editor (--no-edit flag)
   * @param afterAmend a hook that runs after the amend completes but before the TOCTOU check;
   *                   production callers pass a no-op
   * @return JSON string with operation result; domain status for OK/RACE_DETECTED/ALREADY_PUSHED,
   *   or standard hook block decision on error
   * @throws NullPointerException if {@code message} or {@code afterAmend} are null
   * @throws IOException          if the operation fails
   */
  public String execute(String message, boolean noEdit, Runnable afterAmend) throws IOException
  {
    requireThat(message, "message").isNotNull();
    requireThat(afterAmend, "afterAmend").isNotNull();

    // Step 1: Record OLD_HEAD before amend
    String oldHead;
    try
    {
      oldHead = runGitCommandSingleLineInDirectory(directory, "rev-parse", "HEAD");
    }
    catch (IOException e)
    {
      return block(scope, "Failed to resolve HEAD: " + e.getMessage());
    }

    // Step 2: Check push status — if commit already pushed, fail-fast
    ProcessRunner.Result statusResult = ProcessRunner.run(
      "git", "-C", directory, "status", "--porcelain", "-b");
    String pushStatus = "";
    if (statusResult.exitCode() == 0 && !statusResult.stdout().isBlank())
    {
      // Get first line (branch tracking info)
      String firstLine = statusResult.stdout().strip().split("\n")[0];
      pushStatus = firstLine;
    }

    // Check if branch is tracking a remote (status line contains "...")
    if (pushStatus.contains("..."))
    {
      if (pushStatus.contains("[ahead"))
      {
        // Branch is ahead of remote — commit not pushed yet, safe to amend
      }
      else
      {
        // Branch is up to date with or behind the remote — commit appears to be pushed
        return buildAlreadyPushedJson(oldHead);
      }
    }

    // Step 3: Perform amend with appropriate flags
    List<String> amendCmd = new ArrayList<>();
    amendCmd.add("git");
    amendCmd.add("-C");
    amendCmd.add(directory);
    amendCmd.add("commit");
    amendCmd.add("--amend");

    if (noEdit && message.isEmpty())
      amendCmd.add("--no-edit");
    if (!message.isEmpty())
    {
      amendCmd.add("-m");
      amendCmd.add(message);
    }

    ProcessRunner.Result amendResult = ProcessRunner.run(amendCmd.toArray(new String[0]));
    if (amendResult.exitCode() != 0)
      return block(scope, "Amend failed: " + amendResult.stdout().strip());

    // Step 4: Record NEW_HEAD after amend
    String newHead = runGitCommandSingleLineInDirectory(directory, "rev-parse", "HEAD");

    // Hook: runs between amend and TOCTOU check (allows tests to simulate concurrent push)
    afterAmend.run();

    // Step 5: Post-amend TOCTOU check — verify OLD_HEAD not pushed during amend
    boolean raceDetected = false;
    ProcessRunner.Result remoteRefResult = ProcessRunner.run(
      "git", "-C", directory, "rev-parse", "@{push}");
    if (remoteRefResult.exitCode() == 0 && !remoteRefResult.stdout().isBlank())
    {
      String remoteRef = remoteRefResult.stdout().strip();
      // Check if OLD_HEAD is an ancestor of remote ref (meaning it was pushed during amend)
      ProcessRunner.Result ancestorResult = ProcessRunner.run(
        "git", "-C", directory, "merge-base", "--is-ancestor", oldHead, remoteRef);
      if (ancestorResult.exitCode() == 0)
        raceDetected = true;
    }

    if (raceDetected)
      return buildRaceDetectedJson(oldHead, newHead);
    return buildOkJson(oldHead, newHead);
  }

  /**
   * Builds an OK JSON response.
   *
   * @param oldHead the commit hash before amend
   * @param newHead the commit hash after amend
   * @return JSON string with OK status
   * @throws IOException if JSON serialization fails
   */
  private String buildOkJson(String oldHead, String newHead) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "OK");
    json.put("old_head", oldHead);
    json.put("new_head", newHead);
    json.put("race_detected", false);
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Builds a RACE_DETECTED JSON response.
   *
   * @param oldHead the commit hash before amend
   * @param newHead the commit hash after amend
   * @return JSON string with RACE_DETECTED status
   * @throws IOException if JSON serialization fails
   */
  private String buildRaceDetectedJson(String oldHead, String newHead) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "RACE_DETECTED");
    json.put("old_head", oldHead);
    json.put("new_head", newHead);
    json.put("message", "Original commit was pushed during amend. Force-with-lease push needed.");
    json.put("recovery", "git push --force-with-lease");
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Builds an ALREADY_PUSHED JSON response.
   *
   * @param oldHead the commit hash that was already pushed
   * @return JSON string with ALREADY_PUSHED status
   * @throws IOException if JSON serialization fails
   */
  private String buildAlreadyPushedJson(String oldHead) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "ALREADY_PUSHED");
    json.put("head", oldHead);
    json.put("message", "Commit already pushed to remote. Amend would create divergent history.");
    return scope.getJsonMapper().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage: git-amend [--message "new message"] [--no-edit] [WORKTREE_PATH]
   * <p>
   * Outputs JSON to stdout on all outcomes (OK, RACE_DETECTED, ALREADY_PUSHED, block decision on error).
   * Always exits with code 0 so Claude Code parses stdout as JSON.
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
        Logger log = LoggerFactory.getLogger(GitAmend.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the git-amend logic with a caller-provided output stream.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   * IOException is converted to a block response on {@code out}.
   *
   * @param scope the JVM scope
   * @param args  command-line arguments
   * @param out   the output stream to write JSON to
   * @throws NullPointerException if {@code args} or {@code out} are null
   */
  public static void run(JvmScope scope, String[] args, PrintStream out)
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    String amendMessage = "";
    boolean noEdit = false;
    String worktreePath = ".";

    // Parse arguments
    int index = 0;
    while (index < args.length)
    {
      switch (args[index])
      {
        case "--message" ->
        {
          if (index + 1 >= args.length)
          {
            out.println(block(scope, "--message requires an argument"));
            return;
          }
          amendMessage = args[index + 1];
          index += 2;
        }
        case "--no-edit" ->
        {
          noEdit = true;
          ++index;
        }
        default ->
        {
          worktreePath = args[index];
          ++index;
        }
      }
    }

    GitAmend cmd = new GitAmend(scope, worktreePath);
    try
    {
      String result = cmd.execute(amendMessage, noEdit);
      out.println(result);
    }
    catch (IOException e)
    {
      out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
