/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.GivingUpDetector;
import io.github.cowwoc.cat.claude.hook.util.ViolationType;
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

  /**
   * Verifies that technical discussion mentioning broken code as a topic does not trigger code-removal
   * detection.
   * <p>
   * The phrase "allow fixing adjacent broken code — don't skip error handling" describes a coding practice,
   * not agent intent to disable code. The word "broken" is a topic being discussed and "skip" appears in a
   * negated instruction ("don't skip"), neither of which reflects first-person intent to remove or disable
   * code. The detector must not fire CODE_REMOVAL_REMINDER on this input.
   */
  @Test
  public void technicalDiscussionAboutBrokenCodeIsNotFalsePositive()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("allow fixing adjacent broken code — don't skip error handling");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that "i'll remove" and "i'll skip" phrases are detected.
   * "i'll disable" is covered by detectsCodeRemoval().
   */
  @Test
  public void illRemovalPhrasesAreDetected()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String removeResult = detector.check("the test is broken so i'll remove this temporarily");
    requireThat(removeResult, "removeResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String skipResult = detector.check("this keeps failing so i'll skip the validation");
    requireThat(skipResult, "skipResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that "i will remove", "i will disable", and "i will skip" phrases are detected.
   */
  @Test
  public void iWillRemovalPhrasesAreDetected()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String removeResult = detector.check("since this fails i will remove the exception handler");
    requireThat(removeResult, "removeResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String disableResult = detector.check("since this fails i will disable the check");
    requireThat(disableResult, "disableResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String skipResult = detector.check("since this fails i will skip the validation");
    requireThat(skipResult, "skipResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that "let me remove", "let me disable", and "let me skip" phrases are detected.
   */
  @Test
  public void letMeRemovalPhrasesAreDetected()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String removeResult = detector.check("let me remove the failing assertion");
    requireThat(removeResult, "removeResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String disableResult = detector.check("let me disable this check");
    requireThat(disableResult, "disableResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String skipResult = detector.check("let me skip this test");
    requireThat(skipResult, "skipResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that "i'm going to remove", "i'm going to disable", and "i'm going to skip" phrases
   * are detected.
   */
  @Test
  public void imGoingToRemovalPhrasesAreDetected()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String removeResult = detector.check("i'm going to remove this broken test");
    requireThat(removeResult, "removeResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String disableResult = detector.check("i'm going to disable the handler");
    requireThat(disableResult, "disableResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    String skipResult = detector.check("i'm going to skip the validation");
    requireThat(skipResult, "skipResult").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that passive voice describing past removal is not a false positive.
   */
  @Test
  public void passiveVoiceRemovalIsNotFalsePositive()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("the handler was removed in a previous commit");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that negated removal instructions are not false positives.
   */
  @Test
  public void negatedRemovalIsNotFalsePositive()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("do not remove this guard clause");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that bare imperative removal instructions without a first-person subject are not false
   * positives.
   */
  @Test
  public void bareImperativeIsNotFalsePositive()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("remove the unused import");
    requireThat(result, "result").isEmpty();
  }
}
