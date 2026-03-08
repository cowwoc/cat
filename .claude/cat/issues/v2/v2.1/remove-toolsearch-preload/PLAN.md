# Plan: remove-toolsearch-preload

## Current State

`work-with-issue-agent` SKILL.md contains a mandatory "Tool Preloading" step that invokes
`ToolSearch("select:Read,Bash,AskUserQuestion,Task,Skill")` before any phase begins. These tools are available by
default in the main agent and do not require explicit loading via ToolSearch. The call adds a wasted tool call
round-trip on every `/cat:work` invocation.

## Target State

The "Tool Preloading" section is removed from `work-with-issue-agent`. Tools are used directly without a preload step.

## Parent Requirements

None — performance micro-optimization from session analysis.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — Read, Bash, AskUserQuestion, Task, and Skill are always available in the main agent
- **Mitigation:** Verify the listed tools are not deferred (deferred tools require ToolSearch to load)

## Research Findings

Deferred tools are listed in `<available-deferred-tools>` system messages. Read, Bash, AskUserQuestion, Task, and
Skill are core tools always present and never deferred. ToolSearch is only needed for tools explicitly listed as
deferred (e.g., MCP tools, specialized tools injected by hooks). The five tools in the preload call are standard
Claude Code tools, not deferred tools.

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — Remove the "Tool Preloading" step and its ToolSearch call

  **Verify file location:**
  ```bash
  ls /workspace/plugin/skills/work-with-issue-agent/
  ```

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Open `plugin/skills/work-with-issue-agent/first-use.md`
- Remove the "Tool Preloading" section (the paragraph and the ToolSearch call block)
- Renumber subsequent steps if they are numbered (Step 1, Step 2, etc.)
- Verify no other section references the "Tool Preloading" step by name
- Commit: `refactor: remove redundant ToolSearch preload from work-with-issue-agent`
- Update STATE.md (status: closed, progress: 100%) in same commit

## Post-conditions

- [ ] No "Tool Preloading" section exists in `work-with-issue-agent` skill file
- [ ] No `ToolSearch("select:Read,Bash,AskUserQuestion,Task,Skill")` call in the skill
- [ ] Remaining steps renumbered sequentially with no gaps
