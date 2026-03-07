# Plan: preload-common-tools-at-session-start

## Current State

During `/cat:work`, commonly-needed tools (Read, Edit, Grep, Glob, Bash, AskUserQuestion, Write, Task,
Skill) are discovered individually via separate `ToolSearch` calls as each phase needs them. Session
analysis identified 5+ separate ToolSearch calls per session that could have been combined. Each
ToolSearch call requires a tool round-trip (~300ms latency, ~300 tokens).

## Target State

A single `ToolSearch` call using `select:<tool1>,<tool2>,...` syntax pre-loads all commonly-needed tools
at the start of `work-with-issue-agent` execution, saving ~5 round-trips per `/cat:work` invocation
(~1.5K tokens, ~1.5s wall-clock time).

## Parent Requirements

None — performance optimization.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — pre-loading tools earlier has no behavioral effect; tools are still
  available when needed
- **Mitigation:** Verify the select: syntax loads all listed tools; test that pre-loaded tools work
  correctly in later phases

## Files to Modify
- `plugin/skills/work-with-issue-agent/first-use.md` — add a ToolSearch pre-load call at the top of
  Step 1 (before any phase-specific tool usage) using the select: syntax to load all tools needed
  across all phases: `Read, Edit, Grep, Glob, Bash, AskUserQuestion, Write, Task, Skill`

  NOTE: If issue `split-work-with-issue-phases` is implemented first, this change applies to the
  thin orchestrator and/or the per-phase files instead.

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/work-with-issue-agent/first-use.md` and identify all ToolSearch calls currently
  in the file. List each tool being loaded and at which step. Identify any tools loaded redundantly.
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

### Wave 2
- Add a single consolidated ToolSearch call using `select:Read,Edit,Grep,Glob,Bash,AskUserQuestion,Write,Task,Skill`
  at the very start of the skill execution (before Step 1 content). Remove any redundant individual
  ToolSearch calls for tools already in the consolidated list.
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions
- [ ] A single ToolSearch call pre-loads all common tools at the start of work-with-issue-agent
- [ ] No redundant ToolSearch calls for pre-loaded tools appear later in the skill
- [ ] All tools are available and functional throughout all phases of /cat:work
- [ ] Consolidated ToolSearch reduces redundant tool discovery: Verified through operational use in subsequent /cat:work invocations
