# Plan: add-main-agent-only-restrictions

## Goal
Add main-agent-only invocation restrictions to skills that require spawning capability
(shrink-doc, compare-docs, stakeholder-review). Include references to delegation rules.

## Problem Statement
These skills internally spawn subagents to do work. When invoked by a subagent (which cannot
spawn), the workflow fails silently or produces incorrect results. Skills must document this
restriction clearly.

## Satisfies
M429 - Technically impossible workflow correction

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Documentation-only changes, low code impact
- **Mitigation:** Clear, consistent restriction markers across all affected skills

## Scope

### Files to Modify
| File | Changes |
|------|---------|
| plugin/skills/shrink-doc/SKILL.md | Add invocation restriction, delegation reference |
| plugin/skills/compare-docs/SKILL.md | Add invocation restriction, delegation reference |
| plugin/skills/stakeholder-review/SKILL.md | Add invocation restriction, delegation reference |

### Reference Document
| File | Purpose |
|------|---------|
| plugin/skills/delegate/SKILL.md | Source of delegation rules to reference |

## Design

### Restriction Marker Format
Add to the top of each affected skill (after frontmatter):

```markdown
## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally. It CANNOT be invoked by
a subagent (subagents cannot spawn nested subagents or invoke skills).

If you need this skill's functionality within delegated work:
1. Main agent invokes this skill directly
2. Pass results to the implementation subagent
3. See: plugin/skills/delegate/SKILL.md § "Model Selection for Subagents"
```

### Delegation Reference
Add cross-reference to ensure agents understand the architectural constraint:

```markdown
**Related**: [Delegation Rules](../delegate/SKILL.md) - explains subagent tool limitations
and when to use main agent vs subagent execution.
```

## Acceptance Criteria
- [ ] shrink-doc has invocation restriction marker
- [ ] compare-docs has invocation restriction marker
- [ ] stakeholder-review has invocation restriction marker
- [ ] All three reference delegation rules document
- [ ] Restriction text is consistent across all files
- [ ] No functional changes to skill logic

## Execution Steps

### Step 1: Add restriction to shrink-doc
- Add invocation restriction section after any existing frontmatter
- Add delegation reference in related section
- Verify skill still parses correctly

### Step 2: Add restriction to compare-docs
- Same pattern as shrink-doc
- Ensure consistent wording

### Step 3: Add restriction to stakeholder-review
- Same pattern as above
- This skill orchestrates multiple stakeholder subagents

### Step 4: Verify consistency
- Read all three files
- Confirm restriction text matches
- Confirm delegation references are valid paths

### Step 5: Commit changes
- Single commit: "config: add main-agent-only restrictions to spawning skills (M429)"
