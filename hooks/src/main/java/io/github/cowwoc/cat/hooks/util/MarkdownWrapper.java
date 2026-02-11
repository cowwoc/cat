package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps markdown files to a specified width while preserving special content.
 * <p>
 * Preserves:
 * <ul>
 *   <li>Code blocks (``` fenced)</li>
 *   <li>Markdown tables (| delimited rows)</li>
 *   <li>YAML frontmatter (--- delimited)</li>
 *   <li>Lines with box-drawing characters</li>
 *   <li>Bare URLs (lines that are purely a URL)</li>
 *   <li>Predominantly HTML lines (start with &lt; and end with &gt;)</li>
 * </ul>
 * <p>
 * Wraps:
 * <ul>
 *   <li>Regular prose</li>
 *   <li>Bullet points and numbered lists (maintaining indentation)</li>
 * </ul>
 */
public final class MarkdownWrapper
{
  private static final Pattern BULLET_LIST_PATTERN = Pattern.compile("^(\\s*)([-*+])\\s+(.*)$");
  private static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("^(\\s*)(\\d+\\.)\\s+(.*)$");
  private static final Pattern BARE_URL_PATTERN = Pattern.compile("^<?https?://[^\\s>]+>?$");
  private static final Pattern NUMBERED_MARKER_PATTERN = Pattern.compile("^\\d+\\.");
  private static final String BOX_DRAWING_CHARS = "╭╮╰╯│─├┤┌┐└┘┬┴┼╔╗╚╝║═╠╣╦╩╬";

  /**
   * Private constructor to prevent instantiation.
   */
  private MarkdownWrapper()
  {
  }

  /**
   * Wraps a markdown file to the specified width and writes it back.
   *
   * @param filePath the path to the markdown file
   * @param maxWidth the maximum line width
   * @throws NullPointerException if {@code filePath} is null
   * @throws IllegalArgumentException if {@code maxWidth} is not positive
   * @throws IOException if reading or writing fails
   */
  public static void wrapFile(Path filePath, int maxWidth) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();
    requireThat(maxWidth, "maxWidth").isPositive();

    String content = Files.readString(filePath, StandardCharsets.UTF_8);
    String wrapped = wrapMarkdown(content, maxWidth);
    Files.writeString(filePath, wrapped, StandardCharsets.UTF_8);
  }

  /**
   * Wraps markdown content to the specified width.
   *
   * @param content the markdown content to wrap
   * @param maxWidth the maximum line width
   * @return the wrapped markdown content
   * @throws NullPointerException if {@code content} is null
   * @throws IllegalArgumentException if {@code maxWidth} is not positive
   */
  public static String wrapMarkdown(String content, int maxWidth)
  {
    requireThat(content, "content").isNotNull();
    requireThat(maxWidth, "maxWidth").isPositive();

    String[] lines = content.split("\n", -1);
    StringBuilder result = new StringBuilder();
    boolean inCodeBlock = false;
    boolean inYamlFrontmatter = false;
    int yamlDelimiterCount = 0;

    for (int i = 0; i < lines.length; ++i)
    {
      String line = lines[i];
      boolean hasNewline = i < lines.length - 1 || content.endsWith("\n");

      if (line.strip().startsWith("```"))
      {
        inCodeBlock = !inCodeBlock;
        result.append(line);
        if (hasNewline)
          result.append('\n');
        continue;
      }

      if (isYamlDelimiter(line))
      {
        ++yamlDelimiterCount;
        if (yamlDelimiterCount == 1)
          inYamlFrontmatter = true;
        else if (yamlDelimiterCount == 2)
          inYamlFrontmatter = false;
        result.append(line);
        if (hasNewline)
          result.append('\n');
        continue;
      }

      if (inCodeBlock || inYamlFrontmatter)
      {
        result.append(line);
        if (hasNewline)
          result.append('\n');
        continue;
      }

      if (isTableLine(line))
      {
        result.append(line);
        if (hasNewline)
          result.append('\n');
        continue;
      }

      if (line.isBlank())
      {
        result.append(line);
        if (hasNewline)
          result.append('\n');
        continue;
      }

      ListInfo listInfo = getListInfo(line);
      if (listInfo != null)
      {
        String fullLine = listInfo.indent + listInfo.marker + listInfo.content;
        if (fullLine.length() <= maxWidth)
        {
          result.append(fullLine);
          if (hasNewline)
            result.append('\n');
        }
        else
        {
          String continuationIndent = listInfo.indent + " ".repeat(listInfo.marker.length());
          int availableWidth = maxWidth - listInfo.indent.length() - listInfo.marker.length();
          List<String> wrapped = wrapLine(listInfo.content, availableWidth, "", true);

          result.append(listInfo.indent).append(listInfo.marker).append(wrapped.get(0));
          if (hasNewline || wrapped.size() > 1)
            result.append('\n');

          for (int j = 1; j < wrapped.size(); ++j)
          {
            result.append(continuationIndent).append(wrapped.get(j));
            if (hasNewline || j < wrapped.size() - 1)
              result.append('\n');
          }
        }
        continue;
      }

      if (line.length() <= maxWidth)
      {
        result.append(line);
        if (hasNewline)
          result.append('\n');
      }
      else
      {
        if (hasBoxDrawingChars(line) || isBareUrl(line) || isPredominantlyHtml(line))
        {
          result.append(line);
          if (hasNewline)
            result.append('\n');
        }
        else
        {
          int leadingSpaceCount = line.length() - line.stripLeading().length();
          String indent = line.substring(0, leadingSpaceCount);
          String contentPart = line.substring(leadingSpaceCount);

          List<String> wrapped = wrapLine(contentPart, maxWidth - leadingSpaceCount, indent);
          for (int j = 0; j < wrapped.size(); ++j)
          {
            String lineToAppend;
            if (j == 0)
              lineToAppend = indent + wrapped.get(j);
            else
              lineToAppend = wrapped.get(j);

            result.append(lineToAppend);
            if (hasNewline || j < wrapped.size() - 1)
              result.append('\n');
          }
        }
      }
    }

    return result.toString();
  }

  /**
   * Checks if a line is a markdown table row.
   *
   * @param line the line to check
   * @return true if the line is a table row
   */
  private static boolean isTableLine(String line)
  {
    String stripped = line.stripLeading();
    return stripped.startsWith("|") && stripped.substring(1).contains("|");
  }

  /**
   * Checks if a line is a YAML frontmatter delimiter.
   *
   * @param line the line to check
   * @return true if the line is a YAML delimiter
   */
  private static boolean isYamlDelimiter(String line)
  {
    return line.strip().equals("---");
  }

  /**
   * Checks if a line contains box-drawing characters.
   *
   * @param line the line to check
   * @return true if the line contains box-drawing characters
   */
  private static boolean hasBoxDrawingChars(String line)
  {
    for (int i = 0; i < line.length(); ++i)
    {
      if (BOX_DRAWING_CHARS.indexOf(line.charAt(i)) >= 0)
        return true;
    }
    return false;
  }

  /**
   * Checks if a line is purely a URL.
   *
   * @param line the line to check
   * @return true if the line is a bare URL
   */
  private static boolean isBareUrl(String line)
  {
    return BARE_URL_PATTERN.matcher(line.strip()).matches();
  }

  /**
   * Checks if a line is predominantly HTML.
   *
   * @param line the line to check
   * @return true if the line starts with &lt; and ends with &gt;
   */
  private static boolean isPredominantlyHtml(String line)
  {
    String stripped = line.strip();
    return stripped.startsWith("<") && stripped.endsWith(">");
  }

  /**
   * Extracts list information from a line.
   *
   * @param line the line to analyze
   * @return list information, or null if not a list
   */
  private static ListInfo getListInfo(String line)
  {
    Matcher bulletMatcher = BULLET_LIST_PATTERN.matcher(line);
    if (bulletMatcher.matches())
    {
      return new ListInfo(
        bulletMatcher.group(1),
        bulletMatcher.group(2) + " ",
        bulletMatcher.group(3));
    }

    Matcher numberedMatcher = NUMBERED_LIST_PATTERN.matcher(line);
    if (numberedMatcher.matches())
    {
      return new ListInfo(
        numberedMatcher.group(1),
        numberedMatcher.group(2) + " ",
        numberedMatcher.group(3));
    }

    return null;
  }

  /**
   * Checks if a word would create a list marker at the start of a continuation line.
   *
   * @param word the word to check
   * @param nextWord the next word, or null if none
   * @return true if the word would create a list marker
   */
  private static boolean wouldCreateListMarker(String word, String nextWord)
  {
    if ((word.equals("-") || word.equals("*") || word.equals("+")) && nextWord != null)
      return true;
    if (word.startsWith("- ") || word.startsWith("* ") || word.startsWith("+ "))
      return true;
    return NUMBERED_MARKER_PATTERN.matcher(word).find();
  }

  /**
   * Wraps a single line to the specified width.
   *
   * @param line the line to wrap
   * @param maxWidth the maximum line width
   * @param continuationIndent the indent for continuation lines
   * @return list of wrapped lines
   */
  private static List<String> wrapLine(String line, int maxWidth, String continuationIndent)
  {
    return wrapLine(line, maxWidth, continuationIndent, false);
  }

  /**
   * Wraps a single line to the specified width.
   *
   * @param line the line to wrap
   * @param maxWidth the maximum line width
   * @param continuationIndent the indent for continuation lines
   * @param avoidListMarkers if true, avoid breaking at words that would create list markers
   * @return list of wrapped lines
   */
  private static List<String> wrapLine(String line, int maxWidth, String continuationIndent,
    boolean avoidListMarkers)
  {
    if (line.length() <= maxWidth)
      return List.of(line);

    if (isTableLine(line) || hasBoxDrawingChars(line) || isBareUrl(line) || isPredominantlyHtml(line))
      return List.of(line);

    String[] words = line.split(" ");
    if (words.length == 0)
      return List.of(line);

    List<String> lines = new ArrayList<>();
    String currentLine = words[0];

    for (int i = 1; i < words.length; ++i)
    {
      String word = words[i];
      String testLine = currentLine + " " + word;
      if (testLine.length() <= maxWidth)
        currentLine = testLine;
      else
      {
        String nextWord;
        if (i + 1 < words.length)
          nextWord = words[i + 1];
        else
          nextWord = null;

        if (avoidListMarkers && wouldCreateListMarker(word, nextWord))
        {
          currentLine = handleListMarkerAvoidance(lines, currentLine, word, continuationIndent,
            testLine);
        }
        else
        {
          lines.add(currentLine);
          currentLine = continuationIndent + word;
        }
      }
    }

    if (!currentLine.isEmpty())
      lines.add(currentLine);

    return lines;
  }

  /**
   * Handles breaking a line when the next word would create a list marker.
   *
   * @param lines the list of completed lines
   * @param currentLine the current line being built
   * @param word the word that would create a list marker
   * @param continuationIndent the indent for continuation lines
   * @param testLine the line if word were added normally
   * @return the new current line after handling the marker avoidance
   */
  private static String handleListMarkerAvoidance(List<String> lines, String currentLine,
    String word, String continuationIndent, String testLine)
  {
    int lastSpace = currentLine.lastIndexOf(' ');
    if (lastSpace > 0)
    {
      lines.add(currentLine.substring(0, lastSpace));
      return continuationIndent + currentLine.substring(lastSpace + 1) + " " + word;
    }
    return testLine;
  }

  /**
   * Information about a list item.
   *
   * @param indent the indentation string
   * @param marker the list marker (e.g., "- ", "1. ")
   * @param content the content after the marker
   */
  private record ListInfo(String indent, String marker, String content)
  {
    /**
     * Creates a new list info.
     *
     * @param indent the indentation string
     * @param marker the list marker (e.g., "- ", "1. ")
     * @param content the content after the marker
     * @throws NullPointerException if {@code indent}, {@code marker}, or {@code content} are null
     */
    ListInfo
    {
      requireThat(indent, "indent").isNotNull();
      requireThat(marker, "marker").isNotNull();
      requireThat(content, "content").isNotNull();
    }
  }
}
