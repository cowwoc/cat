/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Detects "giving up" patterns in text, covering both user-prompt context and assistant-message context.
 * <p>
 * Patterns are organized into four violation categories:
 * <ol>
 *   <li>{@code constraint_rationalization} — phrases justifying scope reduction due to complexity,
 *       token budget, or other constraints (applies in both prompt and assistant-message context)</li>
 *   <li>{@code code_removal} — phrases indicating broken code is being disabled or removed instead of
 *       debugged (applies in prompt context)</li>
 *   <li>{@code compilation_abandonment} — constraint rationalization combined with a compilation
 *       or build problem indicator (applies in prompt context)</li>
 *   <li>{@code token_rationalization} — references to token usage or context limits coupled with work
 *       scope reduction (applies in assistant-message context)</li>
 * </ol>
 * <p>
 * Detection splits the text into sentences on {@code .}, {@code !}, {@code ?}, or newline, then applies
 * ordered keyword checks within each sentence using {@link #indexOf(String, Collection, int)}. This
 * prevents false positives when constraint and abandonment keywords appear in two separate, unrelated
 * sentences.
 * <p>
 * Both detection paths share the same entry point: {@link #check(String)}. The method runs the
 * prompt-style patterns first ({@code constraint_rationalization}, {@code code_removal},
 * {@code compilation_abandonment}), then the token-rationalization patterns, returning the reminder
 * for the first match found.
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
   * Reminder injected when compilation abandonment (avoiding build errors by removing dependencies) is
   * detected.
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
   * Reminder injected when token usage rationalization (mentioning token counts to justify reduced scope)
   * is detected.
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
   * Preamble phrases that open a constraint-rationalization sentence.
   */
  private static final Set<String> CONSTRAINT_PREAMBLES = Set.of("given", "due to");

  /**
   * Complexity or difficulty signal words.
   */
  private static final Set<String> COMPLEXITY_KEYWORDS = Set.of("complexity", "difficulty");

  /**
   * Constraint-type keywords used after a preamble ("given/due to … [keyword]").
   */
  private static final Set<String> CONSTRAINT_KEYWORDS = Set.of(
    "token budget", "token constraints", "time constraints", "context constraints", "context status");

  /**
   * Constraint keywords that act as the sentence subject without a preceding preamble.
   */
  private static final Set<String> CONSTRAINT_SUBJECT_KEYWORDS = Set.of(
    "context constraints", "token constraints", "time constraints", "token budget");

  /**
   * First-person introductory phrases that precede an abandonment verb, including causal forms
   * ("force me to", "i need to").
   */
  private static final Set<String> FIRST_PERSON_INTROS = Set.of(
    "force me to", "let me", "i'll", "i will", "i'm going to", "i need to");

  /**
   * Abandonment verbs that follow a first-person intro in constraint-rationalization patterns.
   */
  private static final Set<String> ABANDONMENT_VERBS = Set.of(
    "move on", "skip", "defer", "simplify", "step back", "drop", "omit",
    "take a different", "take a simpler", "reduce", "summarize", "remove", "recommend", "wrap");

  /**
   * Approach qualifiers that follow "so i recommend" in subject-led constraint sentences.
   */
  private static final Set<String> APPROACH_QUALIFIERS = Set.of("different approach", "simpler approach");

  /**
   * First-person introductory phrases used in code-removal patterns.
   */
  private static final Set<String> CODE_REMOVAL_INTROS = Set.of("i'll", "i will", "let me", "i'm going to");

  /**
   * Removal action words that immediately follow an intro in code-removal patterns.
   */
  private static final Set<String> CODE_REMOVAL_ACTIONS = Set.of("remove", "disable", "skip");

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
   * compilation_abandonment), then token-rationalization patterns.
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
   * compilation_abandonment), then token-rationalization patterns, returning the reminder for the
   * first match found.
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
      case TOKEN_RATIONALIZATION -> TOKEN_RATIONALIZATION_REMINDER;
    };
  }

  /**
   * Detects the type of prompt-style violation in the given text.
   * <p>
   * Detection priority (most specific first):
   * <ol>
   *   <li>constraint_rationalization + compilation problem → COMPILATION_ABANDONMENT</li>
   *   <li>constraint_rationalization (standalone) → CONSTRAINT_RATIONALIZATION</li>
   *   <li>code_disabling (broken code + removal action) → CODE_REMOVAL</li>
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
      if (containsCompilationIndicator(textLower))
        return Optional.of(ViolationType.COMPILATION_ABANDONMENT);
      return Optional.of(ViolationType.CONSTRAINT_RATIONALIZATION);
    }

    if (detectCodeDisabling(textLower))
      return Optional.of(ViolationType.CODE_REMOVAL);

    return Optional.empty();
  }

  /**
   * Returns {@code true} if the text matches a constraint-rationalization pattern.
   * <p>
   * Each check operates on individual sentences so that keyword matches cannot span sentence
   * boundaries. Within a sentence, components are required to appear in left-to-right order using
   * {@link #indexOf(String, Collection, int)} chaining.
   *
   * @param textLower the lowercase text to check
   * @return {@code true} if a pattern matches
   */
  private boolean detectConstraintRationalization(String textLower)
  {
    for (String sentence : splitSentences(textLower))
    {
      // Patterns 1 and 2 share a common preamble ("given" or "due to").
      int preambleEnd = indexOf(sentence, CONSTRAINT_PREAMBLES, 0);
      if (preambleEnd >= 0)
      {
        // Pattern 1: preamble → complexity keyword → first-person intro → abandonment verb
        if (keywordThenAbandonment(sentence, COMPLEXITY_KEYWORDS, preambleEnd))
          return true;
        // Pattern 2: preamble → constraint keyword → first-person intro → abandonment verb
        if (keywordThenAbandonment(sentence, CONSTRAINT_KEYWORDS, preambleEnd))
          return true;
      }
      // Pattern 3: "due to session length" → first-person intro → abandonment verb
      int sessionLengthEnd = indexOf(sentence, "due to session length", 0);
      if (sessionLengthEnd >= 0 && abandonmentFollows(sentence, sessionLengthEnd))
        return true;
      // Pattern 4: "given the extensive work already completed" → first-person intro → abandonment verb
      int extensiveWorkEnd = indexOf(sentence, "given the extensive work already completed", 0);
      if (extensiveWorkEnd >= 0 && abandonmentFollows(sentence, extensiveWorkEnd))
        return true;
      // Pattern 5: constraint as sentence subject → first-person intro → abandonment verb
      //            OR constraint as subject → "so i recommend" → approach qualifier
      int subjectEnd = indexOf(sentence, CONSTRAINT_SUBJECT_KEYWORDS, 0);
      if (subjectEnd >= 0)
      {
        if (abandonmentFollows(sentence, subjectEnd))
          return true;
        int recommendEnd = indexOf(sentence, "so i recommend", subjectEnd);
        if (recommendEnd >= 0 && indexOf(sentence, APPROACH_QUALIFIERS, recommendEnd) >= 0)
          return true;
      }
      // Pattern 6: "given the" → "complexity and" → "token budget" (both constraint signals present)
      int givenTheEnd = indexOf(sentence, "given the", 0);
      if (givenTheEnd >= 0)
      {
        int complexityAndEnd = indexOf(sentence, "complexity and", givenTheEnd);
        if (complexityAndEnd >= 0 && indexOf(sentence, "token budget", complexityAndEnd) >= 0)
          return true;
      }
      // Self-sufficient giving-up phrases that need no pairing
      if (sentence.contains("rather than diving deeper") || sentence.contains("rather than going deeper"))
        return true;
      if (sentence.contains("instead of implementing the full solution"))
        return true;
      int beyondEnd = indexOf(sentence, "this appears to be beyond", 0);
      if (beyondEnd >= 0 && indexOf(sentence, "scope", beyondEnd) >= 0)
        return true;
      int focusEnd = indexOf(sentence, "let me focus on", 0);
      if (focusEnd >= 0 && indexOf(sentence, "instead", focusEnd) >= 0)
        return true;
      if (sentence.contains("let me focus on features that provide more immediate value"))
        return true;
      if (sentence.contains("let me move on to easier tasks"))
        return true;
      if (sentence.contains("i'll create a solid mvp"))
        return true;
      if (sentence.contains("due to complexity and token usage"))
        return true;
      int dueToTokenEnd = indexOf(sentence, "due to token constraints", 0);
      if (dueToTokenEnd >= 0 && indexOf(sentence, "i'll summarize the remaining steps", dueToTokenEnd) >= 0)
        return true;
    }
    return false;
  }

  /**
   * Returns {@code true} if the text matches a code-disabling pattern.
   *
   * @param textLower the lowercase text to check
   * @return {@code true} if a pattern matches
   */
  private boolean detectCodeDisabling(String textLower)
  {
    for (String sentence : splitSentences(textLower))
    {
      // "i'll/i will/let me/i'm going to remove/disable/skip" (intro immediately precedes action)
      for (String intro : CODE_REMOVAL_INTROS)
        for (String action : CODE_REMOVAL_ACTIONS)
          if (sentence.contains(intro + " " + action))
            return true;
      // Literal disabling phrases
      if (sentence.contains("temporarily disable"))
        return true;
      if (sentence.contains("disable for now"))
        return true;
      if (sentence.contains("skip for now"))
        return true;
      if (sentence.contains("skipping it for now") || sentence.contains("skipping this for now"))
        return true;
      if (sentence.contains("recommend skipping"))
        return true;
      // Sentence-local combos where context makes the intent unambiguous
      if (containsSequence(sentence, "i recommend", "skip"))
        return true;
      if (containsSequence(sentence, "simplifying the implementation", "remove"))
        return true;
      if (containsSequence(sentence, "simpler approach", "remove"))
        return true;
      // Specific exception-handling removal phrases
      if (sentence.contains("simplify by removing"))
        return true;
      if (sentence.contains("removing the broad exception handler"))
        return true;
      if (sentence.contains("remove the exception handler"))
        return true;
      if (sentence.contains("removing the try-catch"))
        return true;
    }
    return false;
  }

  /**
   * Returns {@code true} if the text contains a keyword indicating a compilation or build problem.
   * <p>
   * Searches the full text rather than individual sentences because compilation indicators are
   * standalone signals that need not be paired with any other keyword.
   *
   * @param textLower the lowercase text to check
   * @return {@code true} if a compilation keyword is found
   */
  private boolean containsCompilationIndicator(String textLower)
  {
    return textLower.contains("compilation error") ||
      textLower.contains("module not found") ||
      textLower.contains("build fails") ||
      textLower.contains("empty jar") ||
      textLower.contains("no classes compiled") ||
      textLower.contains("jpms");
  }

  /**
   * Returns {@code true} if the assistant message matches a token-rationalization pattern.
   *
   * @param messageText the text content of one assistant message
   * @return {@code true} if a pattern matches
   */
  private boolean detectGivingUpPattern(String messageText)
  {
    String lower = messageText.toLowerCase(Locale.ENGLISH);
    for (String sentence : splitSentences(lower))
    {
      // "given ... token usage ... let me/i'll"
      // "given ... token usage ... strategic ... optimization"
      int givenEnd = indexOf(sentence, "given", 0);
      if (givenEnd >= 0)
      {
        int tokenUsageEnd = indexOf(sentence, "token usage", givenEnd);
        if (tokenUsageEnd >= 0)
        {
          if (indexOf(sentence, "let me", tokenUsageEnd) >= 0 ||
            indexOf(sentence, "i'll", tokenUsageEnd) >= 0)
            return true;
          int strategicEnd = indexOf(sentence, "strategic", tokenUsageEnd);
          if (strategicEnd >= 0 && indexOf(sentence, "optimization", strategicEnd) >= 0)
            return true;
        }
      }
      // "token usage ... complete a few more"
      // "token usage ... then proceed to"
      if (containsSequence(sentence, "token usage", "complete a few more"))
        return true;
      if (containsSequence(sentence, "token usage", "then proceed to"))
        return true;
      // "token usage (NNN/NNN)" — open paren followed by a slash in the same sentence
      int tokenUsageParenEnd = indexOf(sentence, "token usage (", 0);
      if (tokenUsageParenEnd >= 0 && sentence.indexOf('/', tokenUsageParenEnd) >= 0)
        return true;
      // "tokens used ... let me"
      if (containsSequence(sentence, "tokens used", "let me"))
        return true;
      // "tokens remaining ... i'll"
      if (containsSequence(sentence, "tokens remaining", "i'll"))
        return true;
      // "given our token ... complete"
      if (containsSequence(sentence, "given our token", "complete"))
        return true;
      // "given our context ... complete"
      if (containsSequence(sentence, "given our context", "complete"))
        return true;
      // "token budget ... a few more"
      if (containsSequence(sentence, "token budget", "a few more"))
        return true;
      // "context constraints ... strategic"
      if (containsSequence(sentence, "context constraints", "strategic"))
        return true;
      // "i've optimized ... let me ... then proceed"
      int optimizedEnd = indexOf(sentence, "i've optimized", 0);
      if (optimizedEnd >= 0)
      {
        int letMeEnd = indexOf(sentence, "let me", optimizedEnd);
        if (letMeEnd >= 0 && indexOf(sentence, "then proceed", letMeEnd) >= 0)
          return true;
      }
      // "completed ... token ... continue with"
      int completedEnd = indexOf(sentence, "completed", 0);
      if (completedEnd >= 0)
      {
        int tokenEnd = indexOf(sentence, "token", completedEnd);
        if (tokenEnd >= 0 && indexOf(sentence, "continue with", tokenEnd) >= 0)
          return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if one of {@code keywords} appears in {@code sentence} at or after
   * {@code fromIndex}, followed by a first-person intro and an abandonment verb.
   *
   * @param sentence  the text to search within
   * @param keywords  the candidate keywords to match
   * @param fromIndex the minimum start index for the search
   * @return {@code true} if the pattern matches
   */
  private static boolean keywordThenAbandonment(String sentence, Collection<String> keywords, int fromIndex)
  {
    int keywordEnd = indexOf(sentence, keywords, fromIndex);
    return keywordEnd >= 0 && abandonmentFollows(sentence, keywordEnd);
  }

  /**
   * Returns {@code true} if a first-person intro followed by an abandonment verb appears in
   * {@code sentence} at or after {@code fromIndex}.
   *
   * @param sentence  the text to search within
   * @param fromIndex the minimum start index for the search
   * @return {@code true} if the pattern matches
   */
  private static boolean abandonmentFollows(String sentence, int fromIndex)
  {
    int introEnd = indexOf(sentence, FIRST_PERSON_INTROS, fromIndex);
    return introEnd >= 0 && indexOf(sentence, ABANDONMENT_VERBS, introEnd) >= 0;
  }

  /**
   * Returns {@code true} if {@code second} appears after {@code first} in {@code sentence},
   * searching from position 0.
   *
   * @param sentence the text to search within
   * @param first    the term that must appear first
   * @param second   the term that must follow
   * @return {@code true} if both terms are found in order
   */
  private static boolean containsSequence(String sentence, String first, String second)
  {
    int firstEnd = indexOf(sentence, first, 0);
    return firstEnd >= 0 && indexOf(sentence, second, firstEnd) >= 0;
  }

  /**
   * Splits text into sentences on {@code .}, {@code !}, {@code ?}, or newline.
   *
   * @param text the text to split
   * @return the sentences; may include empty strings for adjacent delimiters
   * @throws NullPointerException if {@code text} is null
   */
  private static String[] splitSentences(String text)
  {
    return text.split("[.!?\\n]");
  }

  /**
   * Returns the index of the character immediately after the first occurrence of {@code term} at or
   * after {@code fromIndex}, or {@code -1} if not found.
   *
   * @param sentence  the text to search within
   * @param term      the term to find
   * @param fromIndex the minimum start index for the search
   * @return the index after the last character of the match, or {@code -1}
   */
  private static int indexOf(String sentence, String term, int fromIndex)
  {
    int start = sentence.indexOf(term, fromIndex);
    if (start < 0)
      return -1;
    return start + term.length();
  }

  /**
   * Returns the index of the character immediately after the earliest-starting match among
   * {@code terms} at or after {@code fromIndex}, or {@code -1} if none found.
   *
   * @param sentence  the text to search within
   * @param terms     the candidate terms
   * @param fromIndex the minimum start index for the search
   * @return the index after the last character of the earliest match, or {@code -1}
   * @throws NullPointerException if {@code sentence} or {@code terms} are null
   */
  private static int indexOf(String sentence, Collection<String> terms, int fromIndex)
  {
    int earliestStart = -1;
    int correspondingEnd = -1;
    for (String term : terms)
    {
      int start = sentence.indexOf(term, fromIndex);
      if (start >= 0 && (earliestStart < 0 || start < earliestStart))
      {
        earliestStart = start;
        correspondingEnd = start + term.length();
      }
    }
    return correspondingEnd;
  }
}
