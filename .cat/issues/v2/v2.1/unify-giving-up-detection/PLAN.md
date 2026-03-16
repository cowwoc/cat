# Plan: unify-giving-up-detection

## Current State
Two separate classes detect giving-up patterns in Claude's output:
- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java` — implements `PromptHandler`, analyzes text for 4 violation types (constraint rationalization, code removal, compilation abandonment, permission seeking) using keyword composition
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java` — implements `PostToolHandler`, reads the conversation log JSONL file, analyzes recent assistant messages for token usage rationalization using ordered compound keyword matching

Each class has its own pattern detection logic and reminder constants, resulting in duplicated concerns and inconsistent coverage (patterns caught in one context may be missed in the other).

## Target State
A shared `GivingUpDetector` utility class in `client/src/main/java/io/github/cowwoc/cat/hooks/util/GivingUpDetector.java` contains the union of all detection patterns from both classes. Both handlers delegate their text analysis to `GivingUpDetector`. Conversation-log I/O remains in `DetectAssistantGivingUp`.

The shared detector documents which patterns it contains and justifies which violation types apply in each detection context (prompt vs. assistant message).

## Parent Requirements
None

## Approaches

### A: Full pattern union in shared utility class (Chosen)
- **Risk:** LOW
- **Scope:** 4 files (create GivingUpDetector, modify 2 handlers, create test)
- **Description:** `GivingUpDetector` holds all patterns from both classes. Both handlers call `GivingUpDetector.check(text)` for pattern detection. Single source of truth with no context-switching logic.

### B: Abstract base class with shared detection
- **Risk:** MEDIUM
- **Scope:** 5+ files
- **Description:** Create `AbstractGivingUpDetector` with shared logic extended by both handlers.
- **Rejected:** Both handlers already extend `PromptHandler`/`PostToolHandler` interfaces; adding an inheritance chain adds complexity for no benefit over a plain utility class.

### C: Context-aware detector with enum parameter
- **Risk:** LOW
- **Scope:** 4 files
- **Description:** `GivingUpDetector` accepts a `Context` enum (PROMPT or ASSISTANT) and applies different pattern subsets per context.
- **Rejected:** The goal is consistent detection regardless of context. Having different patterns per context defeats the purpose of unification.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — both handler classes retain their public interfaces (`PromptHandler.check`, `PostToolHandler.check`). Internal detection logic moves to a utility.
- **Mitigation:** Existing tests for both classes continue to pass. New unit tests cover `GivingUpDetector` directly.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GivingUpDetector.java` — **CREATE** new utility class with union of all patterns from both handlers
- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java` — **MODIFY** to delegate `detectViolationType` and all reminder constants to `GivingUpDetector`; keep `removeQuotedSections` locally
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java` — **MODIFY** to delegate `detectGivingUpPattern` and `containsPattern` to `GivingUpDetector`; keep conversation-log I/O and 20-message window logic
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GivingUpDetectorTest.java` — **CREATE** unit tests covering at least one pattern from each of the 5 detection categories: constraint_rationalization, code_removal, compilation_abandonment, permission_seeking, token_rationalization

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create `GivingUpDetector` utility class with:
  - All pattern constants from `DetectGivingUp` (CONSTRAINT_RATIONALIZATION_REMINDER, CODE_REMOVAL_REMINDER, COMPILATION_ABANDONMENT_REMINDER, PERMISSION_SEEKING_REMINDER)
  - All reminder constants from `DetectAssistantGivingUp`: move the inline reminder string (the multi-line text starting with "🚨 ASSISTANT GIVING-UP PATTERN DETECTED - TOKEN POLICY VIOLATION") into a constant named `TOKEN_RATIONALIZATION_REMINDER` in `GivingUpDetector`
  - All private detection methods from `DetectGivingUp`: `detectViolationType`, `detectConstraintRationalization`, `detectCodeDisabling`, `detectAskingPermission`, `hasConstraintKeyword`, `hasAbandonmentAction`, `hasBrokenCodeIndicator`, `hasRemovalAction`, `hasCompilationProblem`, `hasPermissionLanguage`, `hasNumberedOptions`
  - All compound keyword matching from `DetectAssistantGivingUp`: the `detectGivingUpPattern` patterns and `containsPattern` helper
  - `GivingUpDetector` is a non-static class; callers instantiate it via `new GivingUpDetector()` (no constructor arguments)
  - Public method `check(String text) -> String` returning the raw reminder text (no XML wrapping; or empty string if no pattern detected); handles both prompt-style and assistant-message-style patterns in a unified detection pass; `detectViolationType` runs first (prompt patterns), then `detectGivingUpPattern` (token rationalization patterns), returning the first match found
  - Javadoc explaining that patterns cover both user-prompt context (4 violation types) and assistant-message context (token rationalization), and that both detection paths share the same entry point
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GivingUpDetector.java`

- Modify `DetectGivingUp` to:
  - Remove all pattern constants (5 reminder strings) — move to `GivingUpDetector`
  - Remove `detectViolationType` and all private helper methods — delegate to `GivingUpDetector`
  - In `check(String prompt, String sessionId)`: call `removeQuotedSections(prompt)` then call `GivingUpDetector.check(workingText)` and return the result
  - Keep `removeQuotedSections` and `QUOTED_TEXT_PATTERN` locally (specific to prompt-text preprocessing)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java`

- Modify `DetectAssistantGivingUp` to:
  - Remove `detectGivingUpPattern` and `containsPattern` methods — delegate to `GivingUpDetector`
  - In the per-message loop: call `new GivingUpDetector().check(messageText)`; if non-empty, wrap the returned string in `<system-reminder>\n` + reminder + `\n</system-reminder>` and pass the wrapped string to `Result.context(...)`; this preserves the existing `<system-reminder>` XML envelope that the original inline string contained
  - Keep all conversation-log I/O: `getConversationLogPath`, `getRecentAssistantTextContent`, the 20-message window, `ConversationLogUtils` usage
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java`

### Wave 2
- Create unit tests in `GivingUpDetectorTest`:
  - One test per violation type: constraint_rationalization, code_removal, compilation_abandonment, permission_seeking, token_rationalization (5 tests minimum)
  - One test verifying no false positive on clean text
  - One test verifying quote removal suppresses false positives (inherited from DetectGivingUp logic)
  - Follow TestNG conventions; no class fields; no @Before/@After; self-contained tests
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GivingUpDetectorTest.java`
- Run `mvn -f client/pom.xml verify` and confirm all tests pass
- Update `STATE.md` status to `closed` in `client/src/main/java/...` — NOTE: STATE.md update is done separately by the workflow; do NOT update it here

## Post-conditions
- [ ] `GivingUpDetector` class exists with Javadoc documenting all 5 violation categories (constraint_rationalization, code_removal, compilation_abandonment, permission_seeking, token_rationalization) and justifying their applicability in both prompt and assistant-message contexts
- [ ] `DetectGivingUp.check()` delegates pattern matching to `GivingUpDetector`; only `removeQuotedSections` remains local
- [ ] `DetectAssistantGivingUp` delegates pattern matching to `GivingUpDetector`; conversation-log I/O remains local
- [ ] `mvn -f client/pom.xml verify` passes with no new failures
- [ ] `GivingUpDetectorTest` contains at least 5 tests (one per violation category) plus a clean-text test
- [ ] E2E (prompt path): a prompt containing "given the complexity, let me move on to easier tasks" triggers the constraint_rationalization reminder
- [ ] E2E (assistant path): an assistant message containing "given token usage, let me complete a few more" triggers the token_rationalization reminder
