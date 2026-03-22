/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.GivingUpDetector;
import io.github.cowwoc.cat.hooks.util.ViolationType;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GivingUpDetector.
 * <p>
 * Covers all five violation categories (constraint_rationalization, code_removal,
 * compilation_abandonment, permission_seeking, token_rationalization) plus false-positive
 * suppression tests.
 */
public final class GivingUpDetectorTest
{
  /**
   * Verifies that clean text with no giving-up patterns returns an empty string.
   */
  @Test
  public void cleanTextReturnsEmpty()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("I will implement the full solution completely.");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that constraint rationalization is detected when a constraint keyword is combined
   * with an abandonment action.
   * <p>
   * The pattern "given the complexity, let me move on to easier tasks" triggers
   * constraint_rationalization because "complexity" is a constraint keyword and
   * "let me move on to easier tasks" is a literal abandonment phrase.
   */
  @Test
  public void detectsConstraintRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("given the complexity, let me move on to easier tasks");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
    requireThat(result, "result").contains("PERSISTENCE REQUIRED");
  }

  /**
   * Verifies that code removal is detected when broken code is combined with a removal action.
   * <p>
   * The pattern "the test is failing so I'll disable this temporarily" triggers code_removal
   * because "failing" is a broken-code indicator and "disable" with "temporarily" are removal actions.
   */
  @Test
  public void detectsCodeRemoval()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("the test is failing so I'll disable this temporarily");
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    requireThat(result, "result").contains("DEBUGGING REQUIRED");
  }

  /**
   * Verifies that compilation abandonment is detected when a constraint keyword plus
   * a compilation problem indicator trigger an abandonment action.
   * <p>
   * "Due to complex JPMS issues, I'll simplify by removing the dependency" triggers
   * compilation_abandonment because "complex" is a constraint keyword, "jpms" is a compilation
   * problem indicator, and "simplify by removing" is an abandonment phrase.
   */
  @Test
  public void detectsCompilationAbandonment()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check(
      "Due to complex JPMS issues, I'll simplify by removing the dependency");
    requireThat(result, "result").contains("COMPILATION DEBUGGING ABANDONMENT DETECTED");
    requireThat(result, "result").contains("SYSTEMATIC APPROACH REQUIRED");
  }

  /**
   * Verifies that permission seeking is detected when the agent asks the user for consent
   * to continue mid-task.
   * <p>
   * "Would you like me to continue with implementation?" triggers permission_seeking because
   * "would you like" is permission language and "continue with" makes it a gating question.
   */
  @Test
  public void detectsPermissionSeeking()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("Would you like me to continue with implementation?");
    requireThat(result, "result").contains("PROTOCOL VIOLATION DETECTED");
    requireThat(result, "result").contains("AUTONOMOUS COMPLETION REQUIRED");
  }

  /**
   * Verifies that token rationalization is detected when token usage is cited as justification
   * for reducing work scope.
   * <p>
   * "given token usage, let me complete a few more then proceed" triggers token_rationalization
   * because it matches the ordered-keyword pattern "given" → "token usage" → "let me".
   */
  @Test
  public void detectsTokenRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("given token usage, let me complete a few more then proceed");
    requireThat(result, "result").contains("ASSISTANT GIVING-UP PATTERN DETECTED");
    requireThat(result, "result").contains("TOKEN POLICY VIOLATION");
  }

  /**
   * Verifies that detectType() returns CONSTRAINT_RATIONALIZATION for a constraint + abandonment phrase.
   */
  @Test
  public void detectTypeReturnsConstraintRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    Optional<ViolationType> result = detector.detectType(
      "given the complexity, let me move on to easier tasks");
    requireThat(result, "result").isEqualTo(Optional.of(ViolationType.CONSTRAINT_RATIONALIZATION));
  }

  /**
   * Verifies that detectType() returns CODE_REMOVAL when broken code is paired with a removal action.
   */
  @Test
  public void detectTypeReturnsCodeRemoval()
  {
    GivingUpDetector detector = new GivingUpDetector();
    Optional<ViolationType> result = detector.detectType(
      "the test is failing so I'll disable this temporarily");
    requireThat(result, "result").isEqualTo(Optional.of(ViolationType.CODE_REMOVAL));
  }

  /**
   * Verifies that detectType() returns COMPILATION_ABANDONMENT when a constraint keyword is combined
   * with a compilation problem indicator.
   */
  @Test
  public void detectTypeReturnsCompilationAbandonment()
  {
    GivingUpDetector detector = new GivingUpDetector();
    Optional<ViolationType> result = detector.detectType(
      "Due to complex JPMS issues, I'll simplify by removing the dependency");
    requireThat(result, "result").isEqualTo(Optional.of(ViolationType.COMPILATION_ABANDONMENT));
  }

  /**
   * Verifies that detectType() returns PERMISSION_SEEKING when the agent asks for user consent mid-task.
   */
  @Test
  public void detectTypeReturnsPermissionSeeking()
  {
    GivingUpDetector detector = new GivingUpDetector();
    Optional<ViolationType> result = detector.detectType(
      "Would you like me to continue with implementation?");
    requireThat(result, "result").isEqualTo(Optional.of(ViolationType.PERMISSION_SEEKING));
  }

  /**
   * Verifies that detectType() returns TOKEN_RATIONALIZATION when token usage is cited to reduce scope.
   */
  @Test
  public void detectTypeReturnsTokenRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    Optional<ViolationType> result = detector.detectType(
      "given token usage, let me complete a few more then proceed");
    requireThat(result, "result").isEqualTo(Optional.of(ViolationType.TOKEN_RATIONALIZATION));
  }

  /**
   * Verifies that detectType() returns empty for clean text with no giving-up pattern.
   */
  @Test
  public void detectTypeReturnsEmptyForCleanText()
  {
    GivingUpDetector detector = new GivingUpDetector();
    Optional<ViolationType> result = detector.detectType(
      "I will implement the full solution completely.");
    requireThat(result, "result").isEqualTo(Optional.empty());
  }

  /**
   * Verifies that quoted text containing giving-up patterns does not trigger detection.
   * <p>
   * When a prompt quotes someone else saying "given the complexity, I'll skip this", the
   * outer context ("but I will implement fully") should suppress the false positive.
   * This tests the quote-removal logic applied before calling GivingUpDetector.
   * <p>
   * Note: quote removal happens in DetectGivingUp before calling this class. This test
   * confirms that text without the problematic phrase (post-removal) does not trigger.
   */
  @Test
  public void noDetectionOnCleanTextAfterQuoteRemoval()
  {
    GivingUpDetector detector = new GivingUpDetector();
    // Simulates the text remaining after DetectGivingUp.removeQuotedSections() strips the quoted part.
    // The quoted phrase "given the complexity, I'll skip this" was removed, leaving only clean text.
    String result = detector.check("The user said  but I will implement it fully.");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that no false positive occurs on a time-estimate without permission-seeking language.
   */
  @Test
  public void timeEstimateAloneIsNotPermissionSeeking()
  {
    GivingUpDetector detector = new GivingUpDetector();
    // "This will take 2-3 days" by itself does not contain permission language
    String result = detector.check("This will take 2-3 days to complete all the files.");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that the exact E2E pattern from post-conditions triggers constraint_rationalization.
   * <p>
   * Plan post-condition: a prompt containing "given the complexity, let me move on to easier tasks"
   * triggers the constraint_rationalization reminder.
   */
  @Test
  public void e2eConstraintRationalizationPattern()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("given the complexity, let me move on to easier tasks");
    requireThat(result, "result").isEqualTo(GivingUpDetector.CONSTRAINT_RATIONALIZATION_REMINDER);
  }

  /**
   * Verifies that the exact E2E pattern from post-conditions triggers token_rationalization.
   * <p>
   * Plan post-condition: an assistant message containing "given token usage, let me complete a few more"
   * triggers the token_rationalization reminder.
   */
  @Test
  public void e2eTokenRationalizationPattern()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("given token usage, let me complete a few more");
    requireThat(result, "result").isEqualTo(GivingUpDetector.TOKEN_RATIONALIZATION_REMINDER);
  }
}
