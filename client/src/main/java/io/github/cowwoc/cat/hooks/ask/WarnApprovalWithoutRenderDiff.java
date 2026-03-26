/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.ask;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AskHandler;
import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.util.SessionFileUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block or warn when presenting approval gate without get-diff output.
 * <p>
 * This handler detects when an approval gate is being presented during /cat:work
 * and blocks if cat:get-diff wasn't used to display the diff. Sessions that involve
 * git-only operations (force pushes, filter-repo) are exempt
 * from blocking because those workflows do not change the working tree.
 * <p>
 * When cat:get-diff was invoked but the output appears reformatted (sparse box characters
 * with many manual diff signs), the handler issues a warning via additional context
 * rather than blocking.
 */
public final class WarnApprovalWithoutRenderDiff implements AskHandler
{
  private static final int RECENT_LINES_TO_CHECK = 200;
  private static final int MIN_BOX_CHARS_FOR_RENDER_DIFF = 20;
  private static final int MIN_BOX_CHARS_WITH_INVOCATION = 10;
  private static final int REFORMAT_MANUAL_DIFF_THRESHOLD = 5;
  private static final Pattern BOX_CHARS = Pattern.compile("[╭╮╰╯│├┤]");
  private static final Pattern MANUAL_DIFF_SIGNS = Pattern.compile("^\\+\\+\\+|^---|^@@", Pattern.MULTILINE);
  private static final Pattern GIT_ONLY_PATTERN = Pattern.compile(
    "git push(?:\\s+\\S+)*\\s+--force\\b|git push(?:\\s+\\S+)*\\s+-f\\b|git filter-repo\\b");

  private final ClaudeHook scope;

  /**
   * Creates a new WarnApprovalWithoutRenderDiff handler.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public WarnApprovalWithoutRenderDiff(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    String toolInputText = toolInput.toString();
    if (!toolInputText.toLowerCase(Locale.ROOT).contains("approve"))
      return Result.allow();

    Path catDir = scope.getCatDir();
    if (!Files.isDirectory(catDir))
      return Result.allow();

    Path sessionFile = scope.getClaudeSessionsPath().resolve(sessionId + ".jsonl");

    if (!Files.exists(sessionFile))
      return Result.allow();

    return checkSessionForGetDiff(sessionFile);
  }

  /**
   * Check the session file for get-diff invocation and output.
   *
   * @param sessionFile the session JSONL file
   * @return the check result
   * @throws UncheckedIOException if reading the session file fails
   */
  private Result checkSessionForGetDiff(Path sessionFile)
  {
    try
    {
      List<String> recentLines = SessionFileUtils.getRecentLines(sessionFile, RECENT_LINES_TO_CHECK);
      String recentContent = String.join("\n", recentLines);

      int getDiffCount = countOccurrences(recentContent, "get-diff");
      int boxCharsCount = countMatches(recentContent, BOX_CHARS);
      int manualDiffCount = countMatches(recentContent, MANUAL_DIFF_SIGNS);

      if (getDiffCount == 0 && boxCharsCount < MIN_BOX_CHARS_FOR_RENDER_DIFF)
      {
        // Git-only operations (force pushes, filter-repo) do not change the working tree,
        // so requiring a diff review would be a false positive for those workflows.
        if (isGitOnlyOperation(recentContent))
          return Result.allow();

        String warning = """
          ⚠️ RENDER-DIFF NOT DETECTED

          Approval gate REQUIRES 4-column table diff format.

          BEFORE presenting approval:
          1. Invoke: /cat:get-diff
          2. Present the VERBATIM output (must have ╭╮╰╯│ box characters)
          3. DO NOT reformat, summarize, or excerpt the output
          4. Then show the approval question

          If diff is large, present ALL of it across multiple messages.
          NEVER summarize with 'remaining files show...'""";
        return Result.block(warning);
      }

      if (getDiffCount > 0 && boxCharsCount < MIN_BOX_CHARS_WITH_INVOCATION &&
        manualDiffCount > REFORMAT_MANUAL_DIFF_THRESHOLD)
      {
        String warning = """
          ⚠️ RENDER-DIFF OUTPUT MAY BE REFORMATTED

          cat:get-diff was invoked but box characters (╭╮╰╯│) are sparse.
          The diff may have been reformatted into plain diff format.

          REQUIREMENT: Present cat:get-diff output VERBATIM - copy-paste exactly.
          DO NOT extract into code blocks or reformat as standard diff.

          The user must see the actual 4-column table output.""";
        return Result.withContext(warning);
      }
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }

    return Result.allow();
  }

  /**
   * Detect whether the session content indicates a git-only operation that does not change the working tree.
   * <p>
   * Force pushes and filter-repo operations rewrite history or push
   * existing commits without modifying working-tree files, so a diff review is not meaningful.
   *
   * @param recentContent the recent session content to inspect
   * @return {@code true} if the session contains signals of git-only operations
   */
  private boolean isGitOnlyOperation(String recentContent)
  {
    return GIT_ONLY_PATTERN.matcher(recentContent).find();
  }

  /**
   * Count occurrences of a substring in a string.
   *
   * @param text the text to search
   * @param substring the substring to count
   * @return the number of occurrences
   */
  private int countOccurrences(String text, String substring)
  {
    int count = 0;
    int index = text.indexOf(substring);
    while (index != -1)
    {
      ++count;
      index = text.indexOf(substring, index + substring.length());
    }
    return count;
  }

  /**
   * Count regex pattern matches in a string.
   *
   * @param text the text to search
   * @param pattern the pattern to match
   * @return the number of matches
   */
  private int countMatches(String text, Pattern pattern)
  {
    int count = 0;
    Matcher matcher = pattern.matcher(text);
    while (matcher.find())
      ++count;
    return count;
  }
}
