# Plan: refactor-work-orchestration

## Goal
Eliminate work-batch-executor and work-review skills. Update work-with-issue to directly orchestrate
execute/review/merge phases at the main agent level, enabling proper skill invocation.

## Problem Statement
Claude Code subagents cannot:
- Spawn nested subagents (Task tool unavailable)
- Invoke skills dynamically (Skill tool unavailable)

The current architecture has work-batch-executor (a subagent) attempting to spawn work-execute,
work-review, and work-merge subagents. This is architecturally impossible.

## Satisfies
M429 - Technically impossible workflow correction

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  - Complex refactoring across multiple skills
  - Work orchestration is core functionality
  - Must maintain approval gates and trust levels
- **Mitigation:**
  - Incremental changes with testing at each step
  - Keep existing skills until new flow verified
  - Maintain all existing user-facing behavior

## Scope

### Files to Modify
| File | Action |
|------|--------|
| plugin/skills/work-with-issue/SKILL.md | Rewrite to orchestrate directly |
| plugin/skills/work/SKILL.md | Update to work with new flow |

### Files to Delete (after migration complete)
| File | Reason |
|------|--------|
| plugin/skills/work-batch-executor/SKILL.md | Replaced by direct orchestration |
| plugin/skills/work-review/SKILL.md | Logic migrated to work-with-issue |
| plugin/skills/work-execute/SKILL.md | Logic migrated to work-with-issue |

### Files to Reference
| File | Purpose |
|------|---------|
| plugin/skills/delegate/SKILL.md | Delegation rules to follow |
| plugin/skills/stakeholder-review/SKILL.md | How to invoke from main agent |

## Design

### Current (Broken) Architecture
```
work → work-with-issue → spawn batch-executor subagent
                              ↓ (FAILS - subagent cannot spawn)
                         work-execute (subagent)
                         work-review (subagent)
                         work-merge (subagent)
```

### New Architecture
```
work → work-with-issue (main agent orchestrates directly)
           ↓
      [Execute Phase] Main agent:
           - Reads PLAN.md, identifies skills needed
           - Pre-invokes skills that require spawning (shrink-doc, compare-docs)
           - Spawns implementation subagent with pre-processed inputs
           ↓
      [Review Phase] Main agent:
           - Invokes /cat:stakeholder-review directly
           - Handles approval gate
           ↓
      [Merge Phase] Main agent:
           - Spawns merge subagent (mechanical, no skill invocation)
```

### Phase Details

#### Execute Phase
1. Main agent reads PLAN.md from worktree
2. Identify any skill invocations required (scan for `/cat:*` patterns)
3. For skills requiring spawning capability (shrink-doc, compare-docs, stakeholder-review):
   - Invoke at main agent level
   - Capture results
4. Spawn implementation subagent with:
   - PLAN.md steps that don't require skill invocation
   - Pre-computed results from skill invocations
   - File modification instructions

#### Review Phase
1. Main agent invokes `/cat:stakeholder-review` directly
2. Present review results to user
3. Handle approval/rejection based on trust level

#### Merge Phase
1. Spawn merge subagent (haiku - purely mechanical)
2. Squash commits, merge to base, cleanup worktree
3. No skill invocation needed - just git operations

## Logic Migration

### From work-batch-executor
- Progress banner output → work-with-issue
- Phase coordination → work-with-issue
- Error handling and rollback → work-with-issue

### From work-review
- Stakeholder invocation → main agent in work-with-issue
- Review result formatting → work-with-issue
- Approval gate logic → work-with-issue (already partially there)

### From work-execute
- PLAN.md parsing → work-with-issue
- Skill pre-detection → NEW in work-with-issue
- Subagent prompt construction → work-with-issue (updated)

## Acceptance Criteria
- [ ] work-with-issue directly orchestrates all phases without spawning nested subagents
- [ ] Skills requiring spawning (shrink-doc, compare-docs, stakeholder-review) invoked at main level
- [ ] Approval gates preserved per trust level
- [ ] Progress banners displayed correctly
- [ ] All existing /cat:work tests pass
- [ ] work-batch-executor, work-review, work-execute deleted
- [ ] No references to deleted skills remain

## Execution Steps

### Step 1: Update work-with-issue to orchestrate execute phase
- Add PLAN.md parsing to identify skill invocations
- Add pre-invocation of spawning-required skills
- Update subagent prompt to receive pre-computed results

### Step 2: Update work-with-issue to orchestrate review phase
- Move stakeholder-review invocation to main agent level
- Migrate review result handling from work-review

### Step 3: Update work-with-issue to orchestrate merge phase
- Ensure merge subagent has no skill dependencies
- Verify mechanical-only operations

### Step 4: Test complete workflow
- Run /cat:work on a test issue
- Verify all phases complete successfully
- Verify approval gates work correctly

### Step 5: Delete obsolete skills
- Remove work-batch-executor/SKILL.md
- Remove work-review/SKILL.md
- Remove work-execute/SKILL.md
- Update any remaining references

### Step 6: Update documentation
- Update delegate.md if it references deleted skills
- Update any concept docs referencing old architecture
