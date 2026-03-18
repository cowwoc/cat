# Plan: fix-stakeholder-review-content-relay

## Problem

The `stakeholder-review-agent` skill embeds full file content verbatim in each stakeholder subagent
prompt ("Files to Review (Full Content)" section). This is the Subagent Content Relay Anti-Pattern:
the main agent reads files solely to paste them into subagent prompts, bloating main agent context
permanently for all remaining turns.

For a typical review with 3 changed files (~1.5K tokens each), this adds ~4.5K tokens to each of 6
parallel stakeholder prompts — contributing ~27K tokens to main agent output, which compounds across
every subsequent main agent turn.

## Satisfies

None — performance optimization

## Root Cause

The skill constructs stakeholder prompts with "Files to Review (Full Content)" sections. Subagents
could read these files independently at lower token cost (subagent baseline ~18K context vs. main
agent at 80K+ during review phase).

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Subagents reading files independently adds 1-2 Read calls per subagent; parallel
  spawning means these happen concurrently, not serially
- **Mitigation:** Stakeholders already have worktree path; they can construct file paths from it

## Files to Modify

- `plugin/skills/stakeholder-review-agent/SKILL.md` — replace "Files to Review (Full Content)"
  prompt section with file paths + diff summary; instruct subagents to read files independently

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Replace full file content embedding in stakeholder prompt construction with file path list and
  diff summary
  - Files: `plugin/skills/stakeholder-review-agent/SKILL.md`
- Update subagent prompt template to instruct reviewers to read specified files using their tools
  (Read, Glob, Grep) rather than relying on pre-loaded content
  - Files: `plugin/skills/stakeholder-review-agent/SKILL.md`

## Post-conditions

- [ ] Stakeholder subagent prompts contain file paths and diff summary, not full file content
- [ ] Stakeholders use Read tool to access file content independently
- [ ] Stakeholder review output quality unchanged (reviewers still see full file context)
- [ ] All existing tests pass
