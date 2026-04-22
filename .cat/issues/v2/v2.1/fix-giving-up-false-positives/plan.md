# Plan: fix-giving-up-false-positives

## Problem
`GivingUpDetector.detectCodeDisabling()` fires false positives when the agent performs legitimate non-code
operations (deleting git branches, removing lock files, cleaning worktrees) because the `<intro> <action>`
pattern is blind to what is actually being removed. Currently, compound turns (text + tool_use blocks) are
skipped entirely by returning empty string, and pure-text turns are scanned with a broad heuristic.

## Parent Requirements
None

## Root Cause
Two separate issues:
1. Compound turns are skipped — legitimate false-positive signal is never examined but also never fires
2. Pure-text turns use a broad `<intro> <action>` heuristic that matches any removal, not just code-removal

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** May miss some true positives if resource-exhaustion phrases are not present
- **Mitigation:** The narrower pure-text check is more precise; compound-turn check adds true signal

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/TurnSegment.java` — new record
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/ConversationLogUtils.java` — add `extractSegments()` and file-path extraction helpers
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetector.java` — update `detectCodeDisabling()` for segment-based approach and resource-exhaustion check
- `client/src/main/java/io/github/cowwoc/cat/claude/tool/post/DetectAssistantGivingUp.java` — switch to `extractSegments()`

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Add TurnSegment record
- Create `TurnSegment.java` record with fields: `String text`, `@Nullable String aboveFilePath`,
  `@Nullable String belowFilePath`
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/TurnSegment.java`

### Job 2: Add extractSegments() to ConversationLogUtils
- Add `extractSegments(String jsonlLine, JsonMapper mapper)` returning `List<TurnSegment>`
- Walk content array; for each text block at index i, find adjacent tool_use at i-1 and i+1
- Extract file path per tool: Edit/Write/Read → `input.file_path`; NotebookEdit → `input.notebook_path`;
  Bash → regex over `input.command` for tokens with a code extension
- Pure-text messages (no tool_use in turn) → single `TurnSegment(text, null, null)`
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/ConversationLogUtils.java`

### Job 3: Update GivingUpDetector
- Add code extension whitelist: `.java .js .ts .tsx .jsx .py .rb .go .rs .c .cpp .h .hpp .cs .swift
  .kt .scala .sh .bash .zsh .md .html .css .scss .sql`
- Add resource-exhaustion phrase set: `context`, `context window`, `context limit`, `save context`,
  `token`, `token limit`, `running out`, `too long`, `taking too long`, `time limit`, `too large`,
  `conversation is getting`
- Update `detectCodeDisabling()` to accept a `TurnSegment`:
  - Compound segment (either filePath non-null): trigger only if either adjacent file has a code
    extension, then apply existing intro+action check
  - Compound segment with no code file adjacent → suppress (return false)
  - Pure-text segment (both filePaths null): trigger only if sentence contains BOTH intro+action AND
    a resource-exhaustion phrase
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetector.java`

### Job 4: Update DetectAssistantGivingUp
- Replace call to `ConversationLogUtils.extractTextContent()` with `ConversationLogUtils.extractSegments()`
- Pass each `TurnSegment` to `GivingUpDetector.check()` instead of a flat string
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/tool/post/DetectAssistantGivingUp.java`

### Job 5: Write tests
- Test compound turn with code-file adjacent → triggers
- Test compound turn with non-code file adjacent (e.g. `.lock`) → suppressed
- Test compound turn with no adjacent tool file path → suppressed
- Test pure-text turn with intro+action + resource-exhaustion phrase → triggers
- Test pure-text turn with intro+action only (no resource-exhaustion) → suppressed
- Test pure-text turn with resource-exhaustion only (no intro+action) → suppressed
  - Files: `client/src/test/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetectorTest.java`

## Post-conditions
- [ ] All new test cases pass (`mvn -f client/pom.xml verify -e`)
- [ ] No regressions in existing GivingUpDetector tests
- [ ] E2E: agent deleting a git branch or lock file no longer triggers the CODE DISABLING warning
- [ ] E2E: agent saying "I'll skip this test since the context is getting too long" still triggers the warning
