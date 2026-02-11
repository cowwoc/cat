package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates status display alignment before rendering.
 * <p>
 * Checks for proper box structure with:
 * <ul>
 *   <li>Top borders (╭─)</li>
 *   <li>Bottom borders (╰─)</li>
 *   <li>Content lines starting and ending with │</li>
 *   <li>Inner box structure alignment</li>
 * </ul>
 * <p>
 * Created as prevention for M137 (recurrence of M136).
 * Enhanced for M140 with display width validation.
 */
public final class StatusAlignmentValidator
{
  private static final Pattern INNER_BOX_CONTENT_START = Pattern.compile("^│\\s+│.*");
  private static final Pattern INNER_BOX_CONTENT_END = Pattern.compile(".*│\\s+│$");
  private static final Pattern INNER_BOX_TOP_START = Pattern.compile("^│\\s+╭.*");
  private static final Pattern INNER_BOX_TOP_END = Pattern.compile(".*╮\\s+│$");
  private static final Pattern INNER_BOX_BOTTOM_START = Pattern.compile("^│\\s+╰.*");
  private static final Pattern INNER_BOX_BOTTOM_END = Pattern.compile(".*╯\\s+│$");
  private static final int ERROR_CONTEXT_LENGTH = 10;
  /**
   * Result of validation.
   *
   * @param valid true if validation passed
   * @param errors list of error messages (empty if valid)
   * @param contentLines number of content lines validated
   */
  public record ValidationResult(boolean valid, List<String> errors, int contentLines)
  {
    /**
     * Creates a new validation result.
     *
     * @param valid true if validation passed
     * @param errors list of error messages (empty if valid)
     * @param contentLines number of content lines validated
     * @throws NullPointerException if {@code errors} is null
     */
    public ValidationResult
    {
      requireThat(errors, "errors").isNotNull();
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private StatusAlignmentValidator()
  {
  }

  /**
   * Validates the alignment of a status display.
   *
   * @param input the status display content to validate
   * @return validation result with errors if any
   * @throws NullPointerException if {@code input} is null
   */
  public static ValidationResult validate(String input)
  {
    requireThat(input, "input").isNotNull();

    if (!input.contains("╭─"))
      return new ValidationResult(false, List.of("ERROR: No box structure found"), 0);

    List<String> errors = new ArrayList<>();
    boolean inBox = false;
    int lineNum = 0;
    int contentLines = 0;

    String[] lines = input.split("\n");
    for (String line : lines)
    {
      ++lineNum;

      if (line.startsWith("╭─"))
      {
        inBox = true;
        continue;
      }

      if (line.startsWith("╰─"))
      {
        inBox = false;
        continue;
      }

      if (line.startsWith("├─"))
        continue;

      if (inBox)
      {
        ++contentLines;

        if (!line.startsWith("│"))
          errors.add("Line %d: Missing left border │".formatted(lineNum));

        String trimmed = line.stripTrailing();
        if (!trimmed.endsWith("│"))
        {
          String lastChars;
          if (trimmed.length() >= ERROR_CONTEXT_LENGTH)
            lastChars = trimmed.substring(trimmed.length() - ERROR_CONTEXT_LENGTH);
          else
            lastChars = trimmed;
          errors.add("Line %d: Missing right border │ - ends with: '%s'".formatted(lineNum, lastChars));
        }

        if (INNER_BOX_CONTENT_START.matcher(line).matches() &&
          !INNER_BOX_CONTENT_END.matcher(trimmed).matches())
        {
          errors.add("Line %d: Inner box content line missing outer right border │ - should end with │...│".
            formatted(lineNum));
        }
        else if (INNER_BOX_TOP_START.matcher(line).matches() &&
          !INNER_BOX_TOP_END.matcher(trimmed).matches())
        {
          errors.add("Line %d: Inner box top border missing outer right border │ - should end with ╮...│".
            formatted(lineNum));
        }
        else if (INNER_BOX_BOTTOM_START.matcher(line).matches() &&
          !INNER_BOX_BOTTOM_END.matcher(trimmed).matches())
        {
          errors.add("Line %d: Inner box bottom border missing outer right border │ - should end with ╯...│".
            formatted(lineNum));
        }
      }
    }

    boolean valid = errors.isEmpty();
    return new ValidationResult(valid, errors, contentLines);
  }

  /**
   * Validates the alignment and returns a formatted message.
   *
   * @param input the status display content to validate
   * @return formatted validation message
   * @throws NullPointerException if {@code input} is null
   */
  public static String validateAndFormat(String input)
  {
    requireThat(input, "input").isNotNull();

    ValidationResult result = validate(input);
    if (result.valid)
    {
      return "PASS: Alignment validation successful\n" +
        "  - Validated " + result.contentLines + " content lines";
    }

    StringBuilder message = new StringBuilder(64);
    message.append("ALIGNMENT ERRORS DETECTED (").append(result.errors.size()).append(" issues):\n");
    for (String error : result.errors)
      message.append(error).append('\n');
    return message.toString().stripTrailing();
  }
}
