/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.GivingUpDetector;
import io.github.cowwoc.cat.claude.hook.util.TurnSegment;
import io.github.cowwoc.cat.claude.hook.util.ViolationType;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GivingUpDetector.
 * <p>
 * Covers all four violation categories (constraint_rationalization, code_removal,
 * compilation_abandonment, token_rationalization) plus false-positive suppression tests.
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
   * "Due to token budget constraints and the compilation error, I'll remove the module" triggers
   * compilation_abandonment because "token budget" is a constraint keyword, "compilation error" is
   * a compilation problem indicator, and "i'll remove" is an abandonment phrase.
   */
  @Test
  public void detectsCompilationAbandonment()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check(
      "Due to token budget constraints and the compilation error, I'll remove the module");
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
      "Due to token budget constraints and the compilation error, I'll remove the module");
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

  /**
   * Verifies that "Let me run the complex Maven build" does NOT trigger CONSTRAINT_RATIONALIZATION.
   * <p>
   * The word "complex" alone is a common adjective in technical discussion and must not be treated
   * as a giving-up signal. Only multi-word constraint phrases (e.g., "token budget") qualify.
   */
  @Test
  public void complexAdjectiveAloneIsNotConstraintRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("Let me run the complex Maven build");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that "I'll run the difficult migration script" does NOT trigger CONSTRAINT_RATIONALIZATION.
   * <p>
   * The word "difficult" alone is a common adjective in technical discussion. Combining it with
   * "I'll" must not be treated as a scope-reduction signal.
   */
  @Test
  public void difficultAdjectiveAloneIsNotConstraintRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("I'll run the difficult migration script");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that token budget combined with an abandonment action IS detected as
   * CONSTRAINT_RATIONALIZATION.
   * <p>
   * Multi-word constraint phrases like "token budget" are unambiguous giving-up signals when
   * paired with an abandonment action.
   */
  @Test
  public void tokenBudgetWithAbandonmentIsConstraintRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("Given the token budget, let me simplify the approach");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }

  /**
   * Verifies that "let me remove X" in a compound message (text + tool_use) is a known
   * false-positive source suppressed at the ConversationLogUtils layer, not by GivingUpDetector.
   * <p>
   * When this text appears in a pure-text context (direct input to GivingUpDetector), it DOES
   * trigger CODE_REMOVAL — because in a pure-text turn, "let me remove" indicates real intent.
   * The suppression for compound messages is handled by ConversationLogUtils.extractTextContent()
   * returning "" for compound messages before they reach GivingUpDetector.
   */
  @Test
  public void letMeRemoveInPureTextTriggesCodeRemoval()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String result = detector.check("Let me remove the stale worktrees.");
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that "Given:" followed by a list with "Token usage:" item triggers constraint rationalization.
   * <p>
   * This is the primary positive case from plan.md: "Given:" prefix with bulleted list where one item
   * contains "Token usage:".
   */
  @Test
  public void givenListWithTokenUsageTriggersConstraintRationalization()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String text = """
      Given:
      - Full instruction-builder flow = multi-hour process
      - Token usage: 100K/200K
      """;
    String result = detector.check(text);
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
    requireThat(result, "result").contains("PERSISTENCE REQUIRED");
  }

  /**
   * Verifies that "Given:" without "Token usage:" in list items does not trigger detection.
   * <p>
   * This is a negative case from plan.md: "Given:" without "Token usage:" should not trigger.
   */
  @Test
  public void givenListWithoutTokenUsageDoesNotTrigger()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String text = """
      Given:
      - Full instruction-builder flow = multi-hour process
      - Process duration: 2 hours
      """;
    String result = detector.check(text);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that "Token usage:" without "Given:" prefix does not trigger detection.
   * <p>
   * This is a negative case from plan.md: "Token usage:" alone without "Given:" should not trigger.
   */
  @Test
  public void tokenUsageWithoutGivenDoesNotTrigger()
  {
    GivingUpDetector detector = new GivingUpDetector();
    String text = """
      Current status:
      - Token usage: 100K/200K
      - Files processed: 25
      """;
    String result = detector.check(text);
    requireThat(result, "result").isEmpty();
  }

  // ── TurnSegment-based detection tests ──────────────────────────────────────

  /**
   * Verifies that a compound segment adjacent to a code file triggers CODE_REMOVAL when the text
   * contains an intro+action phrase.
   * <p>
   * "Let me remove the broken implementation" adjacent to a .java file is a legitimate code-removal
   * signal because the narrated tool call targets a source file.
   */
  @Test
  public void compoundSegmentWithCodeFileAdjacentTriggers()
  {
    GivingUpDetector detector = new GivingUpDetector();
    // aboveFilePath is a .java file → compound + code file → apply intro+action check
    TurnSegment segment = new TurnSegment(
      "Let me remove the broken implementation",
      "/workspace/src/main/java/Foo.java",
      null);
    String result = detector.check(segment);
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that a compound segment adjacent to a non-code file (e.g. {@code .lock}) is suppressed.
   * <p>
   * "Let me remove the stale lock file" adjacent to a .lock file is a legitimate non-code operation.
   * The detector must not fire CODE_REMOVAL for this pattern.
   */
  @Test
  public void compoundSegmentWithNonCodeFileAdjacentIsSuppressed()
  {
    GivingUpDetector detector = new GivingUpDetector();
    // belowFilePath is a .lock file → compound + non-code file → suppress
    TurnSegment segment = new TurnSegment(
      "Let me remove the stale lock file",
      null,
      "/workspace/.cat/locks/session.lock");
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that a compound segment with no recognized file path in either adjacent block is suppressed.
   * <p>
   * When adjacent tool_use blocks have no file path (e.g., a Bash command with no code file),
   * both paths are null in a compound segment but the segment itself is still compound. Because no
   * code file is adjacent, the detection is suppressed to avoid false positives on non-code operations.
   */
  @Test
  public void compoundSegmentWithNoAdjacentFilePathIsSuppressed()
  {
    GivingUpDetector detector = new GivingUpDetector();
    // Both paths null → treated as pure-text → requires resource-exhaustion phrase too
    // Pure-text path: no resource-exhaustion phrase → suppressed
    TurnSegment segment = new TurnSegment(
      "Let me remove the stale worktrees",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that a pure-text segment containing both intro+action AND a specific resource-exhaustion
   * phrase triggers CODE_REMOVAL detection.
   * <p>
   * The combination of "i'll skip" (intro+action) with "context window" (specific resource-exhaustion
   * phrase) in the same sentence indicates the agent is abandoning work due to resource limits.
   */
  @Test
  public void pureTextSegmentWithIntroActionAndResourceExhaustionTriggers()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "I'll skip this test since the context window is filling up",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
  }

  /**
   * Verifies that the word "context" alone (e.g., "context manager") is not treated as a
   * resource-exhaustion signal in a pure-text segment.
   * <p>
   * Only specific multi-word phrases like "context window" or "context limit" indicate resource
   * exhaustion. The word "context" alone is too common in technical text to be a reliable signal.
   */
  @Test
  public void genericContextWordIsNotResourceExhaustion()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "I'll remove the context manager from the service",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that the word "token" alone (e.g., "token class") is not treated as a
   * resource-exhaustion signal in a pure-text segment.
   * <p>
   * Only specific phrases like "token limit" indicate resource exhaustion. "Token" is a common
   * term in authentication and parsing contexts.
   */
  @Test
  public void genericTokenWordIsNotResourceExhaustion()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "Let me remove the token class from the auth module",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that "too large" alone is not treated as a resource-exhaustion signal.
   * <p>
   * Removing something "too large" describes a size concern, not a resource-exhaustion
   * giving-up pattern.
   */
  @Test
  public void tooLargeAloneIsNotResourceExhaustion()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "The image is too large, let me remove it from the repository",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that "too long" alone is not treated as a resource-exhaustion signal.
   * <p>
   * Removing something "too long" describes a length concern, not a resource-exhaustion
   * giving-up pattern.
   */
  @Test
  public void tooLongAloneIsNotResourceExhaustion()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "The method name is too long, I'll remove the verbose prefix",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that a pure-text segment with intro+action only (no resource-exhaustion phrase) is suppressed.
   * <p>
   * "Let me remove the stale worktrees" describes a legitimate non-code operation with no resource
   * exhaustion context, so it must not trigger CODE_REMOVAL.
   */
  @Test
  public void pureTextSegmentWithIntroActionOnlyIsSuppressed()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "Let me remove the stale worktrees",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that a pure-text segment with resource-exhaustion phrase only (no intro+action) is suppressed.
   * <p>
   * Mentioning "context" or "token" without a paired intro+action phrase is not a code-removal signal.
   */
  @Test
  public void pureTextSegmentWithResourceExhaustionOnlyIsSuppressed()
  {
    GivingUpDetector detector = new GivingUpDetector();
    TurnSegment segment = new TurnSegment(
      "The context window is large and contains many files",
      null,
      null);
    String result = detector.check(segment);
    requireThat(result, "result").isEmpty();
  }
}
