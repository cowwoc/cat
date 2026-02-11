package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Display utilities for building terminal box displays.
 *
 * Provides methods for calculating display width (accounting for emojis)
 * and building box structures with Unicode box-drawing characters.
 */
public final class DisplayUtils
{
  // Public box-drawing characters for use by other classes
  public static final String BOX_TOP_LEFT = "╭";
  public static final String BOX_TOP_RIGHT = "╮";
  public static final String BOX_BOTTOM_LEFT = "╰";
  public static final String BOX_BOTTOM_RIGHT = "╯";
  public static final String BOX_VERTICAL = "│";
  public static final String BOX_HORIZONTAL = "─";
  public static final String BOX_LEFT_INTERSECTION = "├";
  public static final String BOX_RIGHT_INTERSECTION = "┤";
  public static final String HORIZONTAL_LINE = "─";
  public static final String BULLET = "•";
  public static final String ARROW_RIGHT = "→";

  // Public emoji constants for use by other classes
  public static final String EMOJI_FOLDER = "📁";
  public static final String EMOJI_LOCK = "🔒";
  public static final String EMOJI_HERB = "🌿";
  public static final String EMOJI_HOURGLASS = "⏳";
  public static final String EMOJI_MEMO = "📝";
  public static final String EMOJI_MAGNIFIER = "🔍";
  public static final String EMOJI_BROOM = "🧹";
  public static final String EMOJI_CHECKMARK = "✅";
  public static final String EMOJI_SHUFFLE = "🔀";
  public static final String EMOJI_CLIPBOARD = "📋";


  private static final int DEFAULT_PREFIX_LENGTH = 4;

  // Progress bar characters
  private static final String PROGRESS_FILLED = "█";
  private static final String PROGRESS_EMPTY = "░";

  // Rating circle characters
  private static final char FILLED_CIRCLE = '●';
  private static final char EMPTY_CIRCLE = '○';

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  /**
   * Cached emoji widths map.
   */
  private final Map<String, Integer> emojiWidths;

  /**
   * Sorted emojis for matching (longest first).
   */
  private final List<String> sortedEmojis;

  /**
   * Creates a DisplayUtils instance with auto-detected terminal type.
   * <p>
   * Requires the {@code CLAUDE_PLUGIN_ROOT} environment variable to be set.
   * Fails fast if the variable is undefined or {@code emoji-widths.json} is missing.
   *
   * @param mapper the JSON mapper to use for loading configuration
   * @throws NullPointerException if mapper is null
   * @throws IOException if {@code CLAUDE_PLUGIN_ROOT} is undefined or {@code emoji-widths.json} cannot be loaded
   */
  public DisplayUtils(JsonMapper mapper) throws IOException
  {
    requireThat(mapper, "mapper").isNotNull();

    String envRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
    if (envRoot == null || envRoot.isEmpty())
      throw new IOException("CLAUDE_PLUGIN_ROOT environment variable is not set");

    Path widthsFile = Path.of(envRoot).resolve("emoji-widths.json");
    if (!Files.exists(widthsFile))
      throw new IOException("emoji-widths.json not found at " + widthsFile);

    Map<String, Integer> widths = loadEmojiWidthsFromFile(widthsFile, TerminalType.detect(), mapper);
    this.emojiWidths = widths;
    this.sortedEmojis = new ArrayList<>(widths.keySet());
    this.sortedEmojis.sort(Comparator.comparingInt(String::length).reversed());
  }

  /**
   * Loads emoji widths from the JSON file.
   *
   * @param path the path to emoji-widths.json
   * @param terminalType the terminal type to look up
   * @param mapper the JSON mapper to use for parsing
   * @return the map of emoji to width
   * @throws IOException if the file cannot be loaded
   */
  private Map<String, Integer> loadEmojiWidthsFromFile(Path path, TerminalType terminalType, JsonMapper mapper)
    throws IOException
  {
    String content = Files.readString(path);
    Map<String, Object> data = mapper.readValue(content, MAP_TYPE);

    @SuppressWarnings("unchecked")
    Map<String, Object> terminals = (Map<String, Object>) data.get("terminals");

    if (terminals == null || terminals.isEmpty())
      throw new IOException("No terminals found in " + path);

    Map<String, Integer> result = new HashMap<>();
    Object terminalData = terminals.get(terminalType.getJsonKey());
    if (terminalData == null)
      throw new IOException("Terminal type " + terminalType.getJsonKey() + " not found in " + path);

    if (terminalData instanceof Map)
    {
      @SuppressWarnings("unchecked")
      Map<String, Object> terminalWidths = (Map<String, Object>) terminalData;
      for (Map.Entry<String, Object> entry : terminalWidths.entrySet())
      {
        if (entry.getValue() instanceof Number num)
          result.put(entry.getKey(), num.intValue());
      }
    }

    if (result.isEmpty())
      throw new IOException("No emoji widths found in " + path);

    return result;
  }

  /**
   * Calculate the terminal display width of text.
   *
   * Accounts for emoji display widths which may differ from character count.
   *
   * @param text the text to measure
   * @return the display width in terminal columns
   */
  public int displayWidth(String text)
  {
    requireThat(text, "text").isNotNull();

    int width = 0;
    int i = 0;
    int textLen = text.length();

    while (i < textLen)
    {
      boolean matched = false;
      String remaining = text.substring(i);

      // Try to match known emojis (longest first)
      for (String emoji : sortedEmojis)
      {
        if (remaining.startsWith(emoji))
        {
          width += emojiWidths.get(emoji);
          i += emoji.length();
          matched = true;
          break;
        }
      }

      if (!matched)
      {
        // Regular character = width 1
        ++width;
        ++i;
      }
    }

    return width;
  }

  /**
   * Build a single box line with content and padding.
   *
   * @param content the content to display
   * @param minWidth the minimum content width (excluding borders)
   * @return the formatted box line
   */
  public String buildLine(String content, int minWidth)
  {
    requireThat(content, "content").isNotNull();
    requireThat(minWidth, "minWidth").isNotNegative();

    int contentWidth = displayWidth(content);
    int padding = minWidth - contentWidth;
    if (padding < 0)
      padding = 0;

    return BOX_VERTICAL + " " + content + " ".repeat(padding) + " " + BOX_VERTICAL;
  }

  /**
   * Build a top border.
   *
   * @param minWidth the minimum content width (excluding borders)
   * @return the formatted top border line
   */
  public String buildTopBorder(int minWidth)
  {
    requireThat(minWidth, "minWidth").isNotNegative();

    int dashCount = minWidth + 2;
    String dashes = BOX_HORIZONTAL.repeat(dashCount);
    return BOX_TOP_LEFT + dashes + BOX_TOP_RIGHT;
  }

  /**
   * Build a bottom border.
   *
   * @param minWidth the minimum content width (excluding borders)
   * @return the formatted bottom border line
   */
  public String buildBottomBorder(int minWidth)
  {
    requireThat(minWidth, "minWidth").isNotNegative();

    int dashCount = minWidth + 2;
    String dashes = BOX_HORIZONTAL.repeat(dashCount);
    return BOX_BOTTOM_LEFT + dashes + BOX_BOTTOM_RIGHT;
  }

  /**
   * Build a horizontal separator line.
   *
   * @param minWidth the minimum content width (excluding borders)
   * @return the formatted separator line
   */
  public String buildSeparator(int minWidth)
  {
    requireThat(minWidth, "minWidth").isNotNegative();

    int dashCount = minWidth + 2;
    String dashes = BOX_HORIZONTAL.repeat(dashCount);
    return BOX_LEFT_INTERSECTION + dashes + BOX_RIGHT_INTERSECTION;
  }

  /**
   * Build a box with header and optional separators.
   *
   * @param header header text to display
   * @param contentLines lines of content inside the box
   * @return the complete box as a string
   */
  public String buildHeaderBox(String header, List<String> contentLines)
  {
    return buildHeaderBox(header, contentLines, List.of(), null, null);
  }

  /**
   * Build a box with header and optional separators.
   *
   * @param header header text to display
   * @param contentLines lines of content inside the box
   * @param minWidth minimum box width, or null for auto
   * @return the complete box as a string
   */
  public String buildHeaderBox(String header, List<String> contentLines, Integer minWidth)
  {
    return buildHeaderBox(header, contentLines, List.of(), minWidth, null);
  }

  /**
   * Build a box with header and optional separators.
   *
   * @param header header text to display
   * @param contentLines lines of content inside the box
   * @param separatorIndices indices where separator lines should be inserted
   * @return the complete box as a string
   */
  public String buildHeaderBox(String header, List<String> contentLines, List<Integer> separatorIndices)
  {
    return buildHeaderBox(header, contentLines, separatorIndices, null, null);
  }

  /**
   * Build a box with header and optional separators.
   *
   * @param header header text to display
   * @param contentLines lines of content inside the box
   * @param separatorIndices indices where separator lines should be inserted
   * @param minWidth minimum box width, or null for auto
   * @param prefix header prefix (default "--- ", use "- " for simpler style)
   * @return the complete box as a string
   */
  public String buildHeaderBox(String header, List<String> contentLines,
                               List<Integer> separatorIndices, Integer minWidth, String prefix)
  {
    requireThat(header, "header").isNotNull();
    requireThat(contentLines, "contentLines").isNotNull();

    if (separatorIndices == null)
      separatorIndices = List.of();

    if (prefix == null)
      prefix = BOX_HORIZONTAL + BOX_HORIZONTAL + BOX_HORIZONTAL + " ";  // "--- "

    // Calculate max width
    int maxContentWidth = calculateMaxWidth(contentLines);

    // Account for prefix in header width calculation
    int headerWidth = displayWidth(header) + prefix.length() + 1;  // +1 for space before suffix dashes
    int boxWidth = Math.max(maxContentWidth, headerWidth);
    if (minWidth != null && minWidth > boxWidth)
      boxWidth = minWidth;

    // Build header
    int suffixDashCount = boxWidth - prefix.length() - displayWidth(header) + 1;
    if (suffixDashCount < 1)
      suffixDashCount = 1;
    String suffixDashes = BOX_HORIZONTAL.repeat(suffixDashCount);
    String top = BOX_TOP_LEFT + prefix + header + " " + suffixDashes + BOX_TOP_RIGHT;

    List<String> lines = new ArrayList<>();
    lines.add(top);

    for (int i = 0; i < contentLines.size(); ++i)
    {
      if (separatorIndices.contains(i))
        lines.add(buildSeparator(boxWidth));
      lines.add(buildLine(contentLines.get(i), boxWidth));
    }

    lines.add(buildBottomBorder(boxWidth));
    return String.join("\n", lines);
  }

  /**
   * Build a progress bar string.
   *
   * @param percent progress percentage (0-100)
   * @param width width of the progress bar in characters
   * @return the progress bar string
   */
  public String buildProgressBar(int percent, int width)
  {
    requireThat(percent, "percent").isBetween(0, 100);
    requireThat(width, "width").isPositive();

    int filled = percent * width / 100;
    int empty = width - filled;
    return PROGRESS_FILLED.repeat(filled) + PROGRESS_EMPTY.repeat(empty);
  }

  /**
   * Build a progress bar string with default width of 25.
   *
   * @param percent progress percentage (0-100)
   * @return the progress bar string
   */
  public String buildProgressBar(int percent)
  {
    return buildProgressBar(percent, 25);
  }

  /**
   * Build an inner box with header and content.
   *
   * @param header header text
   * @param contentItems content lines
   * @return list of lines forming the inner box
   */
  public List<String> buildInnerBox(String header, List<String> contentItems)
  {
    return buildInnerBox(header, contentItems, null);
  }

  /**
   * Build an inner box with header and content.
   *
   * @param header header text
   * @param contentItems content lines
   * @param forcedWidth forced width, or null for auto
   * @return list of lines forming the inner box
   */
  public List<String> buildInnerBox(String header, List<String> contentItems, Integer forcedWidth)
  {
    requireThat(header, "header").isNotNull();
    requireThat(contentItems, "contentItems").isNotNull();

    List<String> effectiveContent;
    if (contentItems.isEmpty())
      effectiveContent = List.of("");
    else
      effectiveContent = contentItems;

    int maxContentWidth = calculateMaxWidth(effectiveContent);

    int headerWidth = displayWidth(header);
    int headerMinWidth = headerWidth + 1;

    int boxWidth = Math.max(headerMinWidth, maxContentWidth);
    if (forcedWidth != null && forcedWidth > boxWidth)
      boxWidth = forcedWidth;

    int remaining = boxWidth - headerWidth - 1;
    if (remaining < 0)
      remaining = 0;
    String dashes;
    if (remaining > 0)
      dashes = BOX_HORIZONTAL.repeat(remaining);
    else
      dashes = "";
    String innerTop = BOX_TOP_LEFT + BOX_HORIZONTAL + " " + header + " " + dashes + BOX_TOP_RIGHT;

    List<String> lines = new ArrayList<>();
    lines.add(innerTop);
    for (String item : effectiveContent)
      lines.add(buildLine(item, boxWidth));
    lines.add(buildBottomBorder(boxWidth));

    return lines;
  }

  /**
   * Build a simple box with icon prefix header.
   *
   * @param icon the icon character(s) to display
   * @param title the title text
   * @param contentLines the content lines
   * @return the complete box as a string
   */
  public String buildSimpleBox(String icon, String title, List<String> contentLines)
  {
    requireThat(icon, "icon").isNotNull();
    requireThat(title, "title").isNotNull();
    requireThat(contentLines, "contentLines").isNotNull();

    String prefix = BOX_HORIZONTAL + " " + icon + " " + title;

    // Calculate max width from content
    int maxContentWidth = calculateMaxWidth(contentLines);

    int prefixWidth = displayWidth(prefix);
    int innerWidth = Math.max(maxContentWidth, prefixWidth) + 2;

    List<String> lines = new ArrayList<>();

    // Top border with embedded prefix
    String suffixDashes = BOX_HORIZONTAL.repeat(innerWidth - prefixWidth);
    lines.add(BOX_TOP_LEFT + prefix + suffixDashes + BOX_TOP_RIGHT);

    // Content lines
    for (String content : contentLines)
    {
      int padding = innerWidth - displayWidth(content);
      lines.add(BOX_VERTICAL + " " + content + " ".repeat(padding - 1) + BOX_VERTICAL);
    }

    // Bottom border
    lines.add(BOX_BOTTOM_LEFT + BOX_HORIZONTAL.repeat(innerWidth) + BOX_BOTTOM_RIGHT);

    return String.join("\n", lines);
  }

  /**
   * Build a top border with embedded header text.
   *
   * @param header the header text
   * @param minWidth the minimum content width
   * @return the formatted header top line
   */
  public String buildHeaderTop(String header, int minWidth)
  {
    requireThat(header, "header").isNotNull();
    requireThat(minWidth, "minWidth").isNotNegative();

    int innerWidth = minWidth + 2;
    int headerWidth = displayWidth(header);
    String prefixDashes = BOX_HORIZONTAL + BOX_HORIZONTAL + BOX_HORIZONTAL + " ";
    int suffixDashesCount = innerWidth - DEFAULT_PREFIX_LENGTH - headerWidth - 1;
    if (suffixDashesCount < 1)
      suffixDashesCount = 1;
    String suffixDashes = BOX_HORIZONTAL.repeat(suffixDashesCount);
    return BOX_TOP_LEFT + prefixDashes + header + " " + suffixDashes + BOX_TOP_RIGHT;
  }

  /**
   * Build a concern box.
   *
   * @param severity the severity label
   * @param concerns the list of concerns
   * @return the complete concern box as a string
   */
  public String buildConcernBox(String severity, List<String> concerns)
  {
    requireThat(severity, "severity").isNotNull();
    requireThat(concerns, "concerns").isNotNull();

    int maxContentWidth = calculateMaxWidth(concerns);

    int headerWidth = displayWidth(severity) + 4;
    int maxWidth = Math.max(maxContentWidth, headerWidth);

    String top = BOX_TOP_LEFT + BOX_HORIZONTAL + " " + severity + " " +
                 BOX_HORIZONTAL.repeat(maxWidth - displayWidth(severity) - 1) + BOX_TOP_RIGHT;

    List<String> lines = new ArrayList<>();
    lines.add(top);
    for (String content : concerns)
    {
      int padding = maxWidth - displayWidth(content);
      lines.add(BOX_VERTICAL + " " + content + " ".repeat(padding) + " " + BOX_VERTICAL);
    }
    lines.add(BOX_BOTTOM_LEFT + BOX_HORIZONTAL.repeat(maxWidth + 2) + BOX_BOTTOM_RIGHT);

    return String.join("\n", lines);
  }

  /**
   * Calculates the maximum display width among a list of strings.
   *
   * @param lines the lines to measure
   * @return the maximum display width
   * @throws NullPointerException if lines is null
   */
  private int calculateMaxWidth(List<String> lines)
  {
    int max = 0;
    for (String line : lines)
    {
      int width = displayWidth(line);
      if (width > max)
        max = width;
    }
    return max;
  }

  /**
   * Convert a 1-5 rating to filled/empty circle display.
   *
   * @param rating the rating value (1-5)
   * @return a string of circles representing the rating
   */
  public static String ratingToCircles(int rating)
  {
    int clampedRating = rating;
    if (clampedRating < 1)
      clampedRating = 1;
    if (clampedRating > 5)
      clampedRating = 5;

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < clampedRating; ++i)
      sb.append(FILLED_CIRCLE);
    for (int i = clampedRating; i < 5; ++i)
      sb.append(EMPTY_CIRCLE);
    return sb.toString();
  }
}
