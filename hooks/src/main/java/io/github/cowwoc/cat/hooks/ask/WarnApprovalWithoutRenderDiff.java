package io.github.cowwoc.cat.hooks.ask;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AskHandler;
import io.github.cowwoc.cat.hooks.util.SessionFileUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warn when presenting approval gate without render-diff output (M232).
 * <p>
 * This handler detects when an approval gate is being presented during /cat:work
 * and warns if render-diff.py wasn't used to display the diff.
 * <p>
 * Related mistakes: M170, M171, M201, M211, M231, M232, R013/A021
 */
public final class WarnApprovalWithoutRenderDiff implements AskHandler
{
  private static final int RECENT_LINES_TO_CHECK = 200;
  private static final int MIN_BOX_CHARS_FOR_RENDER_DIFF = 20;
  private static final int MIN_BOX_CHARS_WITH_INVOCATION = 10;
  private static final int MIN_MANUAL_DIFF_SIGNS = 5;
  private static final Pattern BOX_CHARS = Pattern.compile("[╭╮╰╯│├┤]");
  private static final Pattern MANUAL_DIFF_SIGNS = Pattern.compile("^\\+\\+\\+|^---|^@@", Pattern.MULTILINE);

  /**
   * Creates a new WarnApprovalWithoutRenderDiff handler.
   */
  public WarnApprovalWithoutRenderDiff()
  {
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    String toolInputText = toolInput.toString();
    if (!toolInputText.toLowerCase(Locale.ROOT).contains("approve"))
      return Result.allow();

    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      return Result.allow();

    Path catDir = Paths.get(projectDir, ".claude", "cat");
    if (!Files.isDirectory(catDir))
      return Result.allow();

    if (sessionId.isEmpty())
      return Result.allow();

    Path sessionFile = Paths.get(System.getProperty("user.home"),
      ".config/claude/projects/-workspace", sessionId + ".jsonl");

    if (!Files.exists(sessionFile))
      return Result.allow();

    return checkSessionForRenderDiff(sessionFile);
  }

  /**
   * Check the session file for render-diff invocation and output.
   *
   * @param sessionFile the session JSONL file
   * @return the check result
   * @throws UncheckedIOException if reading the session file fails
   */
  private Result checkSessionForRenderDiff(Path sessionFile)
  {
    try
    {
      List<String> recentLines = SessionFileUtils.getRecentLines(sessionFile, RECENT_LINES_TO_CHECK);
      String recentContent = String.join("\n", recentLines);

      int renderDiffCount = countOccurrences(recentContent, "render-diff.py");
      int boxCharsCount = countMatches(recentContent, BOX_CHARS);
      int manualDiffCount = countMatches(recentContent, MANUAL_DIFF_SIGNS);

      if (renderDiffCount == 0 && boxCharsCount < MIN_BOX_CHARS_FOR_RENDER_DIFF)
      {
        String warning = "⚠️ RENDER-DIFF NOT DETECTED (M232/A021)\n" +
                         "\n" +
                         "Approval gate REQUIRES 4-column table diff format.\n" +
                         "\n" +
                         "BEFORE presenting approval:\n" +
                         "1. Run: git diff ${BASE_BRANCH}..HEAD | \"${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py\"\n" +
                         "2. Present the VERBATIM output (must have ╭╮╰╯│ box characters)\n" +
                         "3. DO NOT reformat, summarize, or excerpt the output\n" +
                         "4. Then show the approval question\n" +
                         "\n" +
                         "If diff is large, present ALL of it across multiple messages.\n" +
                         "NEVER summarize with 'remaining files show...' (M231)";
        return Result.withContext(warning);
      }

      if (renderDiffCount > 0 && boxCharsCount < MIN_BOX_CHARS_WITH_INVOCATION &&
        manualDiffCount > MIN_MANUAL_DIFF_SIGNS)
      {
        String warning = "⚠️ RENDER-DIFF OUTPUT MAY BE REFORMATTED (M211)\n" +
                         "\n" +
                         "render-diff.py was invoked but box characters (╭╮╰╯│) are sparse.\n" +
                         "The diff may have been reformatted into plain diff format.\n" +
                         "\n" +
                         "REQUIREMENT: Present render-diff output VERBATIM - copy-paste exactly.\n" +
                         "DO NOT extract into code blocks or reformat as standard diff.\n" +
                         "\n" +
                         "The user must see the actual 4-column table output.";
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
