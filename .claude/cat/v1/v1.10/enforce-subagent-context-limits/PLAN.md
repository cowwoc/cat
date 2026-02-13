# Task: enforce-subagent-context-limits

## Goal

Clarify in all relevant workflows that the main agent must decompose work so that NO subagent
(explorer, planner, implementation, stakeholder reviews, etc.) surpasses the context usage limit
specified in cat-config.json. Add reporting of overall token usage across all subagents and
trigger learn-from-mistakes if any subagent exceeds the limit.

## Research

*Populated by stakeholder research. Run `/cat:research` to fill, or add manually.*

### Stack
| Library | Purpose | Version | Rationale |
|---------|---------|---------|-----------|
| *TBD* | *TBD* | *TBD* | *TBD* |

### Architecture
- **Pattern:** *TBD*
- **Integration:** *TBD*

### Pitfalls
- *Run /cat:research to populate*


## Type

Feature

## Requirements

- REQ-001: Document that main agent must ensure subagent work fits within context limits
- REQ-002: Report overall token usage (sum across all subagents) after task completion
- REQ-003: Highlight any individual subagents that exceeded the context limit
- REQ-004: Automatically trigger learn-from-mistakes when a subagent exceeds the limit

## Approach

1. Update spawn-subagent skill to emphasize context limit responsibility
2. Update work.md collect_and_report step to aggregate token usage across all subagents
3. Add threshold comparison logic that checks each subagent's usage against contextLimit × targetContextUsage
4. Add automatic learn-from-mistakes invocation when limit exceeded
5. Update agent-architecture.md to document the context limit enforcement pattern

## Files to Modify

- `plugin/skills/spawn-subagent/SKILL.md` - Add context limit guidance
- `plugin/commands/work.md` - Update collect_and_report step with aggregation and limit checking
- `plugin/.claude/cat/references/agent-architecture.md` - Document context limit enforcement

## Acceptance Criteria

- [ ] Spawn-subagent skill documents context limit responsibility
- [ ] Token report shows overall usage across all subagents
- [ ] Individual subagents that exceeded limit are highlighted
- [ ] Learn-from-mistakes is triggered automatically on limit breach
- [ ] Agent architecture documents the enforcement pattern

## Risk Assessment

- **Risk Level:** LOW
- **Rationale:** Documentation and reporting changes, no core logic modification
