/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import java.util.Locale;
import java.util.Optional;

/**
 * Detects "giving up" patterns in text, covering both user-prompt context and assistant-message context.
 * <p>
 * Patterns are organized into five violation categories:
 * <ol>
 *   <li>{@code constraint_rationalization} — phrases justifying scope reduction due to complexity,
 *       token budget, or other constraints (applies in both prompt and assistant-message context)</li>
 *   <li>{@code code_removal} — phrases indicating broken code is being disabled or removed instead of
 *       debugged (applies in prompt context)</li>
 *   <li>{@code compilation_abandonment} — constraint rationalization combined with a compilation
 *       or build problem indicator (applies in prompt context)</li>
 *   <li>{@code permission_seeking} — asking the user for permission to continue mid-task (applies in
 *       prompt context)</li>
 *   <li>{@code token_rationalization} — references to token usage or context limits coupled with work
 *       scope reduction (applies in assistant-message context)</li>
 * </ol>
 * <p>
 * Both detection paths share the same entry point: {@link #check(String)}. The method runs the
 * prompt-style patterns first ({@code constraint_rationalization}, {@code code_removal},
 * {@code compilation_abandonment}, {@code permission_seeking}), then the token-rationalization
 * patterns, returning the reminder for the first match found.
 */
public final class GivingUpDetector
{
  /**
   * Reminder injected when constraint rationalization is detected.
   */
  public static final String CONSTRAINT_RATIONALIZATION_REMINDER = """
    🚨 GIVING UP PATTERN DETECTED - PERSISTENCE REQUIRED

    MANDATORY RESPONSE:
    ✅ IMMEDIATELY return to the original technical problem
    ✅ Apply systematic debugging and decomposition approach
    ✅ Continue working on the exact issue that triggered this pattern
    ✅ Use incremental progress rather than abandoning the work
    ✅ Exhaust all reasonable technical approaches before scope modification

    PROHIBITED: Abandoning complex problems for simpler alternatives without technical justification.""";

  /**
   * Reminder injected when code removal (disabling broken code instead of debugging) is detected.
   */
  public static final String CODE_REMOVAL_REMINDER = """
    🚨 CODE DISABLING ANTI-PATTERN DETECTED - DEBUGGING REQUIRED

    You appear to be disabling, removing, or skipping broken code instead of debugging it.

    CRITICAL VIOLATION: When code is broken or failing, you MUST debug and fix the root cause, NOT
    remove/disable the code.

    PROHIBITED PATTERNS:
    ❌ "The test passes without the custom deserializer, so let me remove it"
    ❌ "Let me simplify by removing the broken code"
    ❌ "I'll disable this for now"
    ❌ "Let me skip this broken feature"
    ❌ "Comment out the failing code temporarily"
    ❌ "Remove the exception handler to fix compilation"
    ❌ "Try a simpler approach" (when debugging should continue)

    MANDATORY RESPONSE:
    ✅ IMMEDIATELY debug the broken code to find the root cause
    ✅ Apply systematic troubleshooting approach (add logging, test isolation, step-by-step analysis)
    ✅ Fix the underlying problem, don't remove functionality
    ✅ If code appears unused, verify with evidence before removal
    ✅ Use incremental debugging rather than wholesale removal

    ACCEPTABLE PATTERNS:
    ✅ "Let me add debug logging to understand why this fails"
    ✅ "I'll create a minimal test case to isolate the issue"
    ✅ "Let me verify the API contract to ensure correct usage"
    ✅ "I'll check the compilation error details to find the exact problem"

    WHY THIS MATTERS:
    - Removing broken code hides problems instead of solving them
    - "Simplifying" often means abandoning requirements
    - Features exist for a reason - debug first, remove only with justification
    - Test passing without code suggests the code may be working but test is wrong

    CORRECT APPROACH:
    1. Identify the specific error/failure
    2. Add targeted debug output to understand behavior
    3. Form hypothesis about root cause
    4. Test hypothesis with minimal changes
    5. Fix the actual problem
    6. Verify fix with tests

    Reference: CLAUDE.md "LONG-TERM SOLUTION PERSISTENCE" and "GIVING UP DETECTION PATTERNS\"""";

  /**
   * Reminder injected when compilation abandonment (avoiding build errors by removing dependencies) is detected.
   */
  public static final String COMPILATION_ABANDONMENT_REMINDER = """
    🚨 COMPILATION DEBUGGING ABANDONMENT DETECTED - SYSTEMATIC APPROACH REQUIRED

    You appear to be avoiding compilation/build problems by removing dependencies or "simplifying" instead of debugging.

    CRITICAL VIOLATION: When build/compilation fails, you MUST debug systematically to find and fix the root cause.

    PROHIBITED PATTERNS:
    ❌ "Due to complex JPMS issues, I'll simplify by removing the dependency"
    ❌ "Module not found error - let me remove this requirement"
    ❌ "Empty JAR produced - I'll take a different approach"
    ❌ "Build succeeds but no classes - I'll redesign to avoid this"
    ❌ "JPMS module path too complex - I'll simplify the API"

    MANDATORY SYSTEMATIC DEBUGGING APPROACH:

    **Step 1: Identify Exact Error**
    ✅ Read full error message carefully
    ✅ Note exact file/line where error occurs
    ✅ Distinguish between: missing dependency, wrong version, compilation error, packaging issue

    **Step 2: Investigate Root Cause**
    For "module not found":
    ✅ Check if module-info.java exists in dependency
    ✅ Verify module name matches between requires and module declaration
    ✅ Check if JAR contains module-info.class: `jar tf path/to/file.jar | grep module-info`
    ✅ Verify dependency is in Maven reactor or installed: `mvn dependency:tree`

    For "empty JAR" (build success but no .class files):
    ✅ Check for compilation errors: `mvn compile -X 2>&1 | grep -i error`
    ✅ Look for "nothing to compile" messages
    ✅ Verify source files exist: `find module/src -name "*.java"`
    ✅ Check target/classes directory: `ls -la module/target/classes/`
    ✅ Try manual javac to see actual errors: `javac -d /tmp module/src/main/java/File.java`

    For JPMS issues:
    ✅ Verify transitive dependencies have module descriptors
    ✅ Check --add-modules or --add-reads might be needed
    ✅ Test compilation with explicit module-path
    ✅ Check for split packages across modules

    **Step 3: Fix Root Cause**
    ✅ Add missing module-info.java files
    ✅ Fix module name mismatches
    ✅ Resolve actual compilation errors in source
    ✅ Add missing dependencies to POM
    ✅ Fix transitive JPMS requirements

    **Step 4: Verify Fix**
    ✅ mvn clean compile succeeds
    ✅ JAR contains expected .class files
    ✅ Module dependencies resolved correctly

    NEVER ACCEPTABLE:
    ❌ Removing dependencies because "it's too hard to make them work"
    ❌ Simplifying API because "JPMS is complex"
    ❌ Redesigning to avoid debugging
    ❌ Moving to "later" without fixing

    ACCEPTABLE ONLY WITH EVIDENCE:
    ✅ "After investigation, discovered dependency X genuinely isn't needed (evidence: ...)"
    ✅ "Consulted stakeholder Y who confirmed this dependency should be removed"
    ✅ "Root cause is external API unavailable - documented blocker"

    Reference: CLAUDE.md "LONG-TERM SOLUTION PERSISTENCE" - Exhaust reasonable effort before downgrading""";

  /**
   * Reminder injected when permission-seeking (asking the user to approve mid-task continuation) is detected.
   */
  public static final String PERMISSION_SEEKING_REMINDER = """
    ❌ PROTOCOL VIOLATION DETECTED - AUTONOMOUS COMPLETION REQUIRED

    You appear to be asking the user for permission to continue mid-protocol.

    CRITICAL VIOLATION: Once INIT state begins, you MUST complete ALL protocol states (0-8) autonomously
    without asking user permission.

    PROHIBITED PATTERNS:
    ❌ "Would you like me to continue with implementation?"
    ❌ "This will take 2-3 days, should I proceed?"
    ❌ "Select a different task or continue?"
    ❌ "Requires extended work session" (as justification to stop)

    TIME ESTIMATES ARE NOT BLOCKERS:
    - "2-3 days" = effort estimation, NOT permission gate
    - You MUST complete implementation regardless of estimated duration
    - Token budget NEVER justifies stopping mid-protocol
    - Complexity NEVER justifies asking permission mid-task

    MANDATORY ACTION:
    ✅ Continue IMMEDIATELY to next protocol state
    ✅ Complete States 0-8 without further user interaction
    ✅ Only stop if genuinely blocked (external API unavailable, ambiguous conflicting requirements)

    LEGITIMATE STOPPING REASONS:
    ✅ External dependency unavailable (API credentials, network access)
    ✅ Ambiguous requirements with no resolution path
    ✅ User explicitly interrupts ("stop", modifies todo.md)

    NOT LEGITIMATE:
    ❌ Task is complex
    ❌ Task takes time
    ❌ Token usage high
    ❌ "Should I ask first?"

    Reference: CLAUDE.md "AUTONOMOUS TASK COMPLETION REQUIREMENT\"""";

  /**
   * Reminder injected when token usage rationalization (mentioning token counts to justify reduced scope) is detected.
   */
  public static final String TOKEN_RATIONALIZATION_REMINDER = """
    🚨 ASSISTANT GIVING-UP PATTERN DETECTED - TOKEN POLICY VIOLATION

    **CRITICAL VIOLATION DETECTED**: You mentioned token usage/context as justification for reducing work
    scope or quality.

    **Token Usage Policy (CLAUDE.md)**:
    "Token usage MUST NEVER affect behavior, implementation, or task completion. IGNORE all token warnings
    and limits. Work with full quality until task is complete or user instructs otherwise. Token budget
    does NOT justify incomplete implementations, shortcuts, quality compromises, or progress reporting
    interruptions."

    **Detected Pattern**: Assistant message contained token/context references coupled with work scope reduction.

    **PROHIBITED PATTERNS**:
    ❌ "Given our token usage (NNNk/NNNk), let me complete a few more..."
    ❌ "Token usage (NNNk/NNNk), I'll do strategic optimizations then proceed"
    ❌ "Given our context, let me complete..."
    ❌ "I've optimized several, let me continue with a few more then proceed"
    ❌ Reporting token status before reducing scope

    **MANDATORY RESPONSE**:
    ✅ IMMEDIATELY return to completing the full task
    ✅ IGNORE all token usage information
    ✅ Work with full quality until task is COMPLETELY done
    ✅ DO NOT use token count to justify partial completion
    ✅ DO NOT report progress and then reduce scope
    ✅ Complete ALL files in the original request

    **CORRECT APPROACH**:
    ✅ Continue systematically through all remaining files
    ✅ Apply same quality standards to all files
    ✅ Only stop when user explicitly instructs or task is 100% complete
    ✅ Never mention token usage in relation to work scope

    Reference: CLAUDE.md "Token Usage Policy" and "Prohibited Downgrade Patterns\"""";

  /**
   * Creates a new giving-up detector.
   */
  public GivingUpDetector()
  {
  }

  /**
   * Detects the type of giving-up violation in the given text.
   * <p>
   * Checks prompt-style patterns first (constraint_rationalization, code_removal,
   * compilation_abandonment, permission_seeking), then token-rationalization patterns.
   *
   * @param text the text to analyze
   * @return the violation type if a pattern is detected, or empty if none detected
   * @throws NullPointerException if {@code text} is null
   */
  public Optional<ViolationType> detectType(String text)
  {
    Optional<ViolationType> promptViolation = detectViolationType(text);
    if (promptViolation.isPresent())
      return promptViolation;
    if (detectGivingUpPattern(text))
      return Optional.of(ViolationType.TOKEN_RATIONALIZATION);
    return Optional.empty();
  }

  /**
   * Checks text for giving-up patterns and returns the appropriate reminder.
   * <p>
   * Runs prompt-style patterns first (constraint_rationalization, code_removal,
   * compilation_abandonment, permission_seeking), then token-rationalization patterns,
   * returning the reminder for the first match found.
   *
   * @param text the text to check
   * @return the raw reminder string if a pattern is detected, or empty string if none detected
   * @throws NullPointerException if {@code text} is null
   */
  public String check(String text)
  {
    Optional<ViolationType> type = detectType(text);
    if (type.isEmpty())
      return "";
    return switch (type.get())
    {
      case CONSTRAINT_RATIONALIZATION -> CONSTRAINT_RATIONALIZATION_REMINDER;
      case CODE_REMOVAL -> CODE_REMOVAL_REMINDER;
      case COMPILATION_ABANDONMENT -> COMPILATION_ABANDONMENT_REMINDER;
      case PERMISSION_SEEKING -> PERMISSION_SEEKING_REMINDER;
      case TOKEN_RATIONALIZATION -> TOKEN_RATIONALIZATION_REMINDER;
    };
  }

  /**
   * Detects the type of prompt-style violation in the given text.
   * <p>
   * Detection priority (most specific first):
   * <ol>
   *   <li>constraint_rationalization + compilation problem → COMPILATION_ABANDONMENT</li>
   *   <li>constraint_rationalization + permission_seeking → PERMISSION_SEEKING</li>
   *   <li>constraint_rationalization (standalone) → CONSTRAINT_RATIONALIZATION</li>
   *   <li>code_disabling (broken code + removal action) → CODE_REMOVAL</li>
   *   <li>permission_seeking (standalone) → PERMISSION_SEEKING</li>
   * </ol>
   *
   * @param text the text to analyze
   * @return the violation type if a prompt-style pattern is detected, or empty if none detected
   */
  private Optional<ViolationType> detectViolationType(String text)
  {
    String textLower = text.toLowerCase(Locale.ROOT);

    if (detectConstraintRationalization(textLower))
    {
      if (hasCompilationProblem(textLower))
        return Optional.of(ViolationType.COMPILATION_ABANDONMENT);
      if (detectAskingPermission(textLower))
        return Optional.of(ViolationType.PERMISSION_SEEKING);
      return Optional.of(ViolationType.CONSTRAINT_RATIONALIZATION);
    }

    if (detectCodeDisabling(textLower))
      return Optional.of(ViolationType.CODE_REMOVAL);

    if (detectAskingPermission(textLower))
      return Optional.of(ViolationType.PERMISSION_SEEKING);

    return Optional.empty();
  }

  /**
   * Detects constraint rationalization pattern.
   *
   * @param textLower the lowercase text to check
   * @return true if pattern detected
   */
  private boolean detectConstraintRationalization(String textLower)
  {
    if (hasConstraintKeyword(textLower) && hasAbandonmentAction(textLower))
      return true;

    return textLower.contains("given the complexity of properly implementing") ||
      textLower.contains("given the evidence that this requires significant changes") ||
      textLower.contains("rather than diving deeper into this complex issue") ||
      textLower.contains("instead of implementing the full solution") ||
      textLower.contains("this appears to be beyond the current scope") ||
      textLower.contains("let me focus on completing the task protocol instead") ||
      textLower.contains("let me focus on features that provide more immediate value") ||
      textLower.contains("let me move on to easier tasks") ||
      textLower.contains("due to complexity and token usage") ||
      textLower.contains("i'll create a solid mvp") ||
      textLower.contains("due to session length, let me") ||
      (textLower.contains("given the") && textLower.contains("complexity and") &&
        textLower.contains("token budget")) ||
      textLower.contains("due to token constraints and the need to complete this workflow, " +
        "i'll summarize the remaining steps") ||
      textLower.contains("given the extensive work already completed");
  }

  /**
   * Detects code disabling pattern.
   *
   * @param textLower the lowercase text to check
   * @return true if pattern detected
   */
  private boolean detectCodeDisabling(String textLower)
  {
    if (hasBrokenCodeIndicator(textLower) && hasRemovalAction(textLower))
      return true;

    return textLower.contains("temporarily disable") ||
      textLower.contains("disable for now") ||
      textLower.contains("skip for now") ||
      textLower.contains("skipping it for now") ||
      textLower.contains("skipping this for now") ||
      textLower.contains("recommend skipping") ||
      (textLower.contains("i recommend") && textLower.contains("skip")) ||
      (textLower.contains("simplifying the implementation") && textLower.contains("remove")) ||
      (textLower.contains("simpler approach") && textLower.contains("remove")) ||
      textLower.contains("simplify by removing") ||
      textLower.contains("removing the broad exception handler") ||
      textLower.contains("remove the exception handler") ||
      textLower.contains("removing the try-catch");
  }

  /**
   * Detects asking permission pattern.
   *
   * @param textLower the lowercase text to check
   * @return true if pattern detected
   */
  private boolean detectAskingPermission(String textLower)
  {
    if (hasPermissionLanguage(textLower) &&
      (textLower.contains("proceed with") || textLower.contains("continue with")))
      return true;

    if (hasConstraintKeyword(textLower) && hasPermissionLanguage(textLower))
      return true;

    if (hasNumberedOptions(textLower) && hasPermissionLanguage(textLower))
      return true;

    if ((textLower.contains("2-3 days") && textLower.contains("implementation")) ||
      textLower.contains("requires extended work session") ||
      textLower.contains("multi-day implementation") ||
      (textLower.contains("will be quite") && textLower.contains("would you like")))
      return true;

    return (textLower.contains("state 3") || textLower.contains("synthesis")) &&
      (textLower.contains("ready for implementation") || textLower.contains("would you like"));
  }

  /**
   * Checks for constraint keywords.
   *
   * @param text the text to check
   * @return true if constraint keyword found
   */
  private boolean hasConstraintKeyword(String text)
  {
    return text.contains("time constraints") ||
      text.contains("complexity") ||
      text.contains("complex") ||
      text.contains("token budget") ||
      text.contains("token constraints") ||
      text.contains("context constraints") ||
      text.contains("context status") ||
      (text.contains("context") && text.contains("tokens")) ||
      text.contains("lengthy") ||
      text.contains("difficult") ||
      text.contains("large number") ||
      text.contains("volume");
  }

  /**
   * Checks for abandonment action keywords.
   *
   * @param text the text to check
   * @return true if abandonment action keyword found
   */
  private boolean hasAbandonmentAction(String text)
  {
    return text.contains("skip") ||
      text.contains("simplify") ||
      text.contains("remove") ||
      text.contains("different approach") ||
      text.contains("move on") ||
      text.contains("defer") ||
      text.contains("let me") ||
      text.contains("i'll") ||
      text.contains("i need to") ||
      text.contains("recommend") ||
      text.contains("redesign");
  }

  /**
   * Checks for broken code indicators.
   *
   * @param text the text to check
   * @return true if broken code indicator found
   */
  private boolean hasBrokenCodeIndicator(String text)
  {
    return text.contains("broken") ||
      text.contains("failing") ||
      text.contains("test passes without") ||
      text.contains("works without");
  }

  /**
   * Checks for removal action keywords.
   *
   * @param text the text to check
   * @return true if removal action keyword found
   */
  private boolean hasRemovalAction(String text)
  {
    return text.contains("remove") ||
      text.contains("disable") ||
      text.contains("skip") ||
      text.contains("comment out") ||
      text.contains("temporarily");
  }

  /**
   * Checks for compilation problem indicators.
   *
   * @param text the text to check
   * @return true if compilation problem indicator found
   */
  private boolean hasCompilationProblem(String text)
  {
    return text.contains("compilation error") ||
      text.contains("module not found") ||
      text.contains("build fails") ||
      text.contains("empty jar") ||
      text.contains("no classes compiled") ||
      text.contains("jpms");
  }

  /**
   * Checks for permission-seeking language.
   *
   * @param text the text to check
   * @return true if permission language found
   */
  private boolean hasPermissionLanguage(String text)
  {
    return text.contains("would you like") ||
      text.contains("what's your preference") ||
      text.contains("which approach") ||
      text.contains("or would you prefer");
  }

  /**
   * Checks for numbered option lists.
   *
   * @param text the text to check
   * @return true if numbered options found
   */
  private boolean hasNumberedOptions(String text)
  {
    return text.contains("1. ") && text.contains("2. ");
  }

  /**
   * Detects token-rationalization giving-up patterns in a single assistant message's text.
   *
   * @param messageText the text content of one assistant message
   * @return true if a token-rationalization pattern is detected
   */
  private boolean detectGivingUpPattern(String messageText)
  {
    String lower = messageText.toLowerCase(Locale.ENGLISH);
    return containsPattern(lower, "given", "token usage", "let me") ||
      containsPattern(lower, "given", "token usage", "i'll") ||
      containsPattern(lower, "given", "token usage", "strategic", "optimization") ||
      containsPattern(lower, "token usage", "complete a few more") ||
      containsPattern(lower, "token usage", "then proceed to") ||
      containsPattern(lower, "token usage (", "/", ")") ||
      containsPattern(lower, "tokens used", "let me") ||
      containsPattern(lower, "tokens remaining", "i'll") ||
      containsPattern(lower, "given our token", "complete") ||
      containsPattern(lower, "given our context", "complete") ||
      containsPattern(lower, "token budget", "a few more") ||
      containsPattern(lower, "context constraints", "strategic") ||
      containsPattern(lower, "i've optimized", "let me", "then proceed") ||
      containsPattern(lower, "completed", "token", "continue with");
  }

  /**
   * Checks if text contains all patterns in order.
   *
   * @param text the text to search
   * @param patterns the patterns to find in order
   * @return true if all patterns are found in order
   */
  private boolean containsPattern(String text, String... patterns)
  {
    int position = 0;
    for (String pattern : patterns)
    {
      int found = text.indexOf(pattern, position);
      if (found == -1)
        return false;
      position = found + pattern.length();
    }
    return true;
  }
}
