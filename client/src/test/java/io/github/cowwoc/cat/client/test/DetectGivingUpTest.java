/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.prompt.DetectGivingUp;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for DetectGivingUp handler.
 * <p>
 * Tests verify pattern detection and quote removal.
 */
public final class DetectGivingUpTest
{
  /**
   * Verifies that blank prompt throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*prompt.*")
  public void blankPromptThrowsException()
  {
    DetectGivingUp handler = new DetectGivingUp();
    handler.check("", "test-session-1");
  }

  /**
   * Verifies that normal text without patterns returns empty string.
   */
  @Test
  public void normalTextReturnsEmpty()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String result = handler.check(
      "Let me implement this feature properly.", "test-session-2");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies constraint rationalization detection with composable keywords.
   */
  @Test
  public void detectsConstraintRationalization()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Given the token budget, I'll skip the advanced features.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
    requireThat(result, "result").contains("PERSISTENCE REQUIRED");
  }

  /**
   * Verifies detection of specific constraint rationalization pattern.
   */
  @Test
  public void detectsSpecificConstraintPattern()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Given the complexity of properly implementing this, " +
        "let me take a different approach.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }

  /**
   * Verifies code disabling pattern detection.
   */
  @Test
  public void detectsCodeDisabling()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "The test is failing, so I'll temporarily disable this code.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    requireThat(result, "result").contains("DEBUGGING REQUIRED");
  }

  /**
   * Verifies broken code removal pattern detection.
   */
  @Test
  public void detectsBrokenCodeRemoval()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "The test passes without the custom deserializer, " +
        "so let me remove it.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies compilation abandonment pattern detection.
   */
  @Test
  public void detectsCompilationAbandonment()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Due to token budget pressure and the compilation error, " +
        "I'll simplify by removing the dependency.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("COMPILATION DEBUGGING ABANDONMENT DETECTED");
    requireThat(result, "result").contains("SYSTEMATIC APPROACH REQUIRED");
  }

  /**
   * Verifies quoted text is ignored to prevent false positives.
   */
  @Test
  public void quotedTextIgnored()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "The user said \"given the complexity, I'll skip this\" " +
        "but I will implement it fully.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies unbalanced quotes are not removed.
   */
  @Test
  public void unbalancedQuotesKept()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt = "Due to time constraints\" I'll skip this feature.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }

  /**
   * Verifies constraint keyword detection.
   */
  @Test
  public void detectsConstraintKeywords()
  {
    DetectGivingUp handler = new DetectGivingUp();

    String[] prompts = {
      "Due to time constraints, I'll defer this.",
      "The token budget is too high, so I'll skip this.",
      "Given the token constraints, let me simplify.",
      "Context constraints force me to recommend a different approach."
    };

    for (int i = 0; i < prompts.length; ++i)
    {
      String result = handler.check(prompts[i], "test-session-" + i);
      requireThat(result, "result" + i).isNotEmpty();
    }
  }

  /**
   * Verifies abandonment action keyword detection.
   */
  @Test
  public void detectsAbandonmentActions()
  {
    DetectGivingUp handler = new DetectGivingUp();

    String[] prompts = {
      "Due to token budget, I'll skip the advanced features.",
      "Given the token constraints, let me simplify this.",
      "Context constraints are high, so I recommend a different approach.",
      "Time constraints mean I need to defer this."
    };

    for (int i = 0; i < prompts.length; ++i)
    {
      String result = handler.check(prompts[i], "test-session-" + i);
      requireThat(result, "result" + i).isNotEmpty();
    }
  }

  /**
   * Verifies MVP rationalization is detected.
   */
  @Test
  public void detectsMvpRationalization()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Due to token constraints, I'll create a solid MVP " +
        "instead of the full implementation.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }

  /**
   * Verifies that the token-constraints workflow summary phrase is detected as constraint rationalization.
   */
  @Test
  public void detectsTokenConstraintsWorkflowSummaryPhrase()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Due to token constraints and the need to complete this workflow, " +
        "I'll summarize the remaining steps.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }

  /**
   * Verifies that the extensive-work-completed rationalization phrase is detected.
   */
  @Test
  public void detectsExtensiveWorkCompletedPhrase()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Given the extensive work already completed, " +
        "I'll skip the remaining edge cases.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }

  /**
   * Verifies removing exception handler is detected.
   */
  @Test
  public void detectsExceptionHandlerRemoval()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "To fix the compilation error, I'll remove the exception handler.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies pattern is detected at end of long prompt.
   */
  @Test
  public void detectsPatternAtEndOfLongPrompt()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String longPrompt =
      "X".repeat(150_000) + " Given the complexity, I'll skip this.";
    String result = handler.check(longPrompt, "test-session");
    requireThat(result, "result").isNotEmpty();
  }

  /**
   * Verifies module not found abandonment is detected.
   */
  @Test
  public void detectsModuleNotFoundAbandonment()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Module not found error persists due to token budget pressure, " +
        "so let me simplify by removing this dependency.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("COMPILATION DEBUGGING ABANDONMENT DETECTED");
  }
}
