/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.prompt;

import io.github.cowwoc.cat.hooks.PromptHandler;
import io.github.cowwoc.cat.hooks.util.GivingUpDetector;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.regex.Pattern;

/**
 * Detects "giving up" patterns in user prompts.
 * <p>
 * Identifies phrases indicating abandonment of complex problems and injects
 * targeted reminders based on the specific violation type detected.
 * <p>
 * Features:
 * - Composable keyword detection (constraint+abandonment, broken+removal, etc.)
 * - Quote removal to prevent false positives
 */
public final class DetectGivingUp implements PromptHandler
{
  private static final Pattern QUOTED_TEXT_PATTERN = Pattern.compile("\"[^\"]*\"");

  /**
   * Creates a new giving up detection handler.
   */
  public DetectGivingUp()
  {
  }

  /**
   * Checks the prompt for giving up patterns and returns the appropriate reminder.
   *
   * @param prompt the user's prompt text
   * @param sessionId the current session ID
   * @return a reminder string if a giving up pattern is detected, or empty string
   * @throws NullPointerException if {@code prompt} or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  @Override
  public String check(String prompt, String sessionId)
  {
    requireThat(prompt, "prompt").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();

    String workingText = removeQuotedSections(prompt);
    return new GivingUpDetector().check(workingText);
  }

  /**
   * Removes quoted sections from text to prevent false positives.
   * <p>
   * Only removes balanced quotes (even number of quote characters).
   *
   * @param text the input text
   * @return text with quoted sections removed
   */
  private String removeQuotedSections(String text)
  {
    if (!text.contains("\""))
      return text;

    long quoteCount = text.chars().filter(ch -> ch == '"').count();
    if (quoteCount % 2 != 0)
      return text;

    return QUOTED_TEXT_PATTERN.matcher(text).replaceAll("");
  }
}
