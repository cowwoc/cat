package io.github.cowwoc.cat.hooks.prompt;

import io.github.cowwoc.cat.hooks.PromptHandler;

import java.util.ArrayList;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects destructive operations and injects verification reminder.
 */
public final class DestructiveOps implements PromptHandler
{
  /**
   * A keyword pattern with its compiled regex.
   *
   * @param display the display name for the keyword
   * @param pattern the compiled pattern
   */
  private record KeywordPattern(String display, Pattern pattern)
  {
    /**
     * Creates a new keyword pattern.
     *
     * @param display the display name for the keyword
     * @param pattern the compiled pattern
     * @throws AssertionError if {@code display} is blank or {@code pattern} is null
     */
    public KeywordPattern
    {
      assert that(display, "display").isNotBlank().elseThrow();
      assert that(pattern, "pattern").isNotNull().elseThrow();
    }
  }

  private final List<KeywordPattern> patterns;

  /**
   * Creates a new destructive operations handler.
   */
  public DestructiveOps()
  {
    patterns = new ArrayList<>();
    String[] keywords = {
      "git rebase", "git reset", "git checkout", "squash", "consolidate",
      "merge", "remove duplicate", "cleanup", "reorganize", "refactor", "delete"
    };
    for (String kw : keywords)
      patterns.add(new KeywordPattern(kw, Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE)));
    // Special case for "rm" with word boundary
    patterns.add(new KeywordPattern("rm", Pattern.compile("\\brm\\b", Pattern.CASE_INSENSITIVE)));
  }

  @Override
  public String check(String prompt, String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    for (KeywordPattern kp : patterns)
    {
      if (kp.pattern().matcher(prompt).find())
      {
        return "DESTRUCTIVE OPERATION DETECTED: '" + kp.display() + "'\n\n" +
          "MANDATORY VERIFICATION REQUIRED:\n" +
          "After completing this operation, you MUST:\n" +
          "1. Double-check that no important details were unintentionally removed\n" +
          "2. Verify that all essential information has been preserved\n" +
          "3. Compare before/after to ensure completeness\n" +
          "4. If consolidating/reorganizing, confirm all original content is retained\n\n" +
          "This verification step is REQUIRED before considering the task complete.";
      }
    }
    return "";
  }
}
