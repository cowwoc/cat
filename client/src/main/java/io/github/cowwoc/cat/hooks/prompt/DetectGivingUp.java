/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.prompt;

import io.github.cowwoc.cat.hooks.PromptHandler;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.Locale;
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

  private static final String CONSTRAINT_RATIONALIZATION_REMINDER = """
    🚨 GIVING UP PATTERN DETECTED - PERSISTENCE REQUIRED

    MANDATORY RESPONSE:
    ✅ IMMEDIATELY return to the original technical problem
    ✅ Apply systematic debugging and decomposition approach
    ✅ Continue working on the exact issue that triggered this pattern
    ✅ Use incremental progress rather than abandoning the work
    ✅ Exhaust all reasonable technical approaches before scope modification

    PROHIBITED: Abandoning complex problems for simpler alternatives without technical justification.""";

  private static final String CODE_REMOVAL_REMINDER = """
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

  private static final String COMPILATION_ABANDONMENT_REMINDER = """
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

  private static final String PERMISSION_SEEKING_REMINDER = """
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
    String violationType = detectViolationType(workingText);

    if (violationType.isEmpty())
      return "";

    return switch (violationType)
    {
      case "constraint_rationalization" -> CONSTRAINT_RATIONALIZATION_REMINDER;
      case "code_removal" -> CODE_REMOVAL_REMINDER;
      case "compilation_abandonment" -> COMPILATION_ABANDONMENT_REMINDER;
      case "permission_seeking" -> PERMISSION_SEEKING_REMINDER;
      default -> "";
    };
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

  /**
   * Detects the type of violation in the given text.
   *
   * @param text the text to analyze (with quotes removed)
   * @return violation type string, or empty if no violation detected
   */
  private String detectViolationType(String text)
  {
    String textLower = text.toLowerCase(Locale.ROOT);

    // Detection priority (most specific first):
    // 1. constraint_rationalization + compilation_problem → compilation_abandonment
    // 2. constraint_rationalization + permission_seeking → permission_seeking
    // 3. constraint_rationalization (standalone)
    // 4. code_disabling (broken code + removal action)
    // 5. permission_seeking (standalone)

    if (detectConstraintRationalization(textLower))
    {
      if (hasCompilationProblem(textLower))
        return "compilation_abandonment";
      if (detectAskingPermission(textLower))
        return "permission_seeking";
      return "constraint_rationalization";
    }

    if (detectCodeDisabling(textLower))
      return "code_removal";

    if (detectAskingPermission(textLower))
      return "permission_seeking";

    return "";
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
}
