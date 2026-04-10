# Bugfix: fix-giving-up-detector-false-positives

## Problem

`DetectAssistantGivingUp` (a `PostToolUse` handler registered for all tools) fires spuriously on
every Bash/Read/Skill tool call, injecting CODE_REMOVAL_REMINDER or CONSTRAINT_RATIONALIZATION_REMINDER
into every response.

**Root cause**: When the agent writes text like "I'll delete the stale worktrees" or "Let me clean up
the old runner directories" immediately before a Bash tool call, that text lands in a compound assistant
JSONL entry whose `content` array contains BOTH a `{"type":"text","text":"..."}` block AND a
`{"type":"tool_use",...}` block. `ConversationLogUtils.extractTextContent` currently extracts the text
from these compound messages and feeds it to `GivingUpDetector`, which then matches "let me remove" /
"i'll remove" / "let me skip" etc. — all perfectly routine operational phrases — as code-removal
giving-up patterns.

Text blocks inside compound (text + tool_use) messages describe what the agent is **about to do with
a tool**. They are not analytical reasoning that could express giving-up thinking; they are narration
of tool invocations. Scanning them is equivalent to scanning Bash command output: the content does not
represent the agent's decision-making reasoning.

**Secondary cause**: `hasConstraintKeyword` is too broad. It matches the single word "complex" and
`hasAbandonmentAction` matches "i'll" as a standalone abandonment signal. Together, any sentence of the
form "I'll run the complex Maven build" triggers `CONSTRAINT_RATIONALIZATION`, even though neither
word indicates scope reduction.

## Root Cause

1. `ConversationLogUtils.extractTextContent()` does not distinguish between "pure text" assistant
   turns (reasoning-only responses) and "compound" turns (reasoning + tool invocation). Both are
   processed identically, so the narration before a tool call is scanned for giving-up patterns.

2. `GivingUpDetector.hasConstraintKeyword()` includes bare "complex" / "difficult" / "volume" without
   requiring a scope-reduction phrase nearby, making it match unrelated technical discussions.

## Satisfies

None — infrastructure/reliability improvement

## Post-conditions

- [ ] When an assistant message's `content` array contains at least one `tool_use` block,
      `DetectAssistantGivingUp` does NOT scan the text blocks of that message
- [ ] A "pure text" assistant turn (no `tool_use` blocks) that contains giving-up patterns IS still
      detected correctly
- [ ] "Let me run the complex Maven build" does NOT trigger `CONSTRAINT_RATIONALIZATION`
- [ ] "I'll delete the stale worktrees" in a compound message does NOT trigger `CODE_REMOVAL`
- [ ] The genuine giving-up patterns documented in `GivingUpDetector` constants still trigger for
      their intended inputs when those inputs appear in pure-text turns
- [ ] Tests cover: compound message with "let me remove" → no detection
- [ ] Tests cover: pure text turn with a genuine giving-up phrase → detection fires
- [ ] `mvn -f client/pom.xml verify -e` passes

## Implementation

### Change 1 — `ConversationLogUtils.extractTextContent()`

Add a check: if the content array contains ANY block with `"type":"tool_use"`, return `""` (skip the
whole message). Only return text content from pure-text messages.

```java
if (contentNode.isArray())
{
  // Skip compound messages (text + tool_use): the text describes the upcoming tool call,
  // not the agent's analytical reasoning. Scanning it causes false positives in
  // DetectAssistantGivingUp (e.g., "Let me remove X" → CODE_REMOVAL false positive).
  for (JsonNode block : contentNode)
  {
    if ("tool_use".equals(block.path("type").asString()))
      return "";
  }
  // Pure-text message: extract and concatenate all text blocks
  StringBuilder sb = new StringBuilder();
  for (JsonNode block : contentNode)
  {
    if ("text".equals(block.path("type").asString()))
    {
      String text = block.path("text").asString();
      if (text != null && !text.isEmpty())
      {
        if (!sb.isEmpty())
          sb.append(' ');
        sb.append(text);
      }
    }
  }
  return sb.toString();
}
```

### Change 2 — `GivingUpDetector.hasConstraintKeyword()`

Remove bare "complex", "difficult", "volume", "lengthy" from constraint keywords. These single words
appear in normal technical discussion and are not giving-up signals on their own. Keep multi-word phrases
like "token budget", "context constraints", "time constraints" which are genuinely giving-up signals.

```java
private boolean hasConstraintKeyword(String text)
{
  return text.contains("time constraints") ||
    text.contains("token budget") ||
    text.contains("token constraints") ||
    text.contains("context constraints") ||
    text.contains("context status") ||
    (text.contains("context") && text.contains("tokens")) ||
    text.contains("given the complexity of properly implementing") ||
    text.contains("given the evidence that this requires significant changes");
}
```

(The multi-word phrases that already appear verbatim in `detectConstraintRationalization()` cover the
specific patterns; duplicating them in `hasConstraintKeyword` is unnecessary.)

## Pre-conditions

- [ ] All dependent issues are closed

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/ConversationLogUtils.java` — skip compound messages (text + tool_use)
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetector.java` — narrow `hasConstraintKeyword`
- `client/src/test/java/io/github/cowwoc/cat/client/test/ConversationLogUtilsTest.java` (or similar) — add tests for compound-message skipping
- `client/src/test/java/io/github/cowwoc/cat/client/test/GivingUpDetectorTest.java` — add tests for narrowed constraint keywords
