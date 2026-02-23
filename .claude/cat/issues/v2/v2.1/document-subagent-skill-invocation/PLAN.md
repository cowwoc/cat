# Plan: document-subagent-skill-invocation

## Goal
Add empirically-verified findings about subagent skill invocation to `plugin/concepts/agent-architecture.md`, covering
skill tool availability, SubagentStartHook injection, and JSONL filtering behavior.

## Satisfies
- None (documentation gap)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — documentation-only change
- **Mitigation:** N/A

## Files to Modify
- `plugin/concepts/agent-architecture.md` — Add new section documenting subagent skill capabilities

## Content to Add

Add a new section after "Subagent Responsibilities" covering these empirically-verified findings:

1. **Subagents CAN invoke the Skill tool dynamically** — The Skill tool is not in the subagent exclusion set.
   Empirically confirmed. Subagents can invoke skills at runtime, not just via `skills:` frontmatter preloading.

2. **Claude Code's native skill listing fires only for the main agent** — The built-in `<available_skills>` system
   prompt injection does NOT fire for subagents. Subagents do not receive skill listings from Claude Code itself.

3. **SubagentStartHook fills the gap** — Our SubagentStartHook injects skill listings as `<system-reminder>` with
   `hook_additional_context` type into subagent context. This is the only mechanism that tells subagents which skills
   exist and when to invoke them.

4. **JSONL filtering of hook context** — Claude Code filters `hook_additional_context` from JSONL transcripts by
   default. Set `CLAUDE_CODE_SAVE_HOOK_ADDITIONAL_CONTEXT=1` to include them. This means skill listings are invisible
   in transcripts but present in live context.

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Read agent-architecture.md** to find the right insertion point (after Subagent Responsibilities)
   - Files: `plugin/concepts/agent-architecture.md`
2. **Add "Subagent Skill Invocation" section** with the four findings documented above
   - Files: `plugin/concepts/agent-architecture.md`
3. **Run tests** to verify no regressions
   - `mvn -f client/pom.xml test`

## Post-conditions
- [ ] `plugin/concepts/agent-architecture.md` contains a section documenting subagent skill invocation capabilities
- [ ] All four findings are documented with clear distinction between Claude Code built-in behavior and CAT hook behavior
- [ ] All tests pass
