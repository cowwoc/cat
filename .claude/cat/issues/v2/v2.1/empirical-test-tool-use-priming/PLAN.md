# Plan: empirical-test-tool-use-priming

## Problem

The empirical test runner sends all priming messages as user messages. When priming messages contain instructions like
"Run `git branch --show-current`", the agent attempts to execute them as tool_use actions instead of treating them as
prior conversation context. This makes it impossible to simulate post-tool-use scenarios (like `/cat:status` after a
work session) — the exact conditions that trigger verbatim echo failures (M507/M508).

## Root Cause

`buildInput()` calls `makeUserMessage()` for every priming message. The stream-json format supports
`tool_use`/`tool_result` message types, but the test runner has no way to express them.

## Approach

Extend the JSON config format to support structured priming messages with explicit message types. When a priming message
is a string, treat it as a user message (backward compatible). When it is an object, use the specified type.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Backward compatibility with existing configs
- **Mitigation:** String priming messages remain user messages; only objects trigger new behavior

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java` - Add tool_use/tool_result message
  builders; update `buildInput` to dispatch on priming message type
- `plugin/skills/empirical-test/first-use.md` - Document structured priming message format

## Acceptance Criteria
- [ ] String priming messages still work as user messages (backward compatible)
- [ ] Object priming messages with `type: "tool_use"` generate proper tool_use + assistant + tool_result sequences
- [ ] Test runner can simulate a conversation with completed Bash tool calls before the test prompt
- [ ] Skill documentation updated with structured priming examples

## Execution Steps
1. **Step 1:** Add message builder methods for tool_use and tool_result stream-json events
   - Files: `EmpiricalTestRunner.java`
2. **Step 2:** Update `buildInput` to check if each priming message is a string (user message) or object (structured)
   - Files: `EmpiricalTestRunner.java`
3. **Step 3:** Update skill documentation with structured priming format and examples
   - Files: `plugin/skills/empirical-test/first-use.md`
4. **Step 4:** Rebuild jlink image and run a test with tool_use priming to verify
