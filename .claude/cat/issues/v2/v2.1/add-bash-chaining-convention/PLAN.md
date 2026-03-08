# Plan: add-bash-chaining-convention

## Current State

Sequential independent Bash commands are issued as separate tool calls throughout orchestration skills, creating
unnecessary round-trips. Session analysis shows 6 consecutive Bash groups (sizes: 9, 5, 4, 3, 3) in a single session
for a small documentation issue. Prior issues `enforce-bash-command-chaining` and `chain-consecutive-bash-in-merge`
addressed specific sites but did not add a persistent convention, so the pattern recurs.

## Target State

A plugin convention in `plugin/rules/` explicitly prohibits separate Bash calls for independent operations and
requires `&&` chaining. All orchestration skill files are updated to chain consecutive independent Bash calls. Future
sessions trigger fewer `batch_candidate` warnings from session-analyzer.

## Parent Requirements

None — quality/performance improvement from session analysis.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — `&&` chains exit on first failure, same as separate calls
- **Mitigation:** Preserve `||` fallbacks and dependent commands as separate calls; chain only when truly independent

## Alternatives Considered

- **Single global grep and fix**: Fast but fragile — mechanical sed replacement may break commands with conditional
  logic. Rejected; implementation subagent needs to evaluate each group.
- **New convention only (no skill changes)**: Convention alone doesn't fix existing violations. Rejected.
- **Fix only worst offenders**: Addresses symptoms not root cause. Rejected; convention prevents recurrence.

## Files to Modify

- `plugin/rules/` — New convention file `bash-efficiency.md` (or add to existing `workflow.md` if present)
  prohibiting separate tool calls for independent Bash operations
- `plugin/skills/work-merge-agent/first-use.md` — Chain banner + lock verify + wave count and other independent groups
- `plugin/skills/work-implement-agent/first-use.md` — Chain banner + lock verify + plan read groups
- `plugin/skills/work-with-issue-agent/first-use.md` — Chain any consecutive independent checks
- `plugin/skills/work-confirm-agent/first-use.md` — Chain banner + verify calls
- `plugin/skills/work-review-agent/first-use.md` — Chain banner + config reads
- `plugin/skills/stakeholder-review-agent/first-use.md` — Chain git data + config reads (already noted in batch)

  **Verify file locations:**
  ```bash
  ls /workspace/plugin/skills/work-merge-agent/
  ls /workspace/plugin/rules/
  ```

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add `bash-efficiency.md` to `plugin/rules/` with the following convention:
  - **MANDATORY:** Chain independent consecutive Bash commands with `&&` in a single Bash tool call
  - Rationale: Each separate Bash call adds a tool call round-trip; chaining independent commands eliminates wasted latency
  - **Definition of independent:** Commands where the second does not depend on the stdout/exit-code of the first for its arguments (read-only checks, status queries, file reads)
  - **When NOT to chain:** Commands where failure of step N should NOT prevent step N+1 (use `;`), or commands
    where one command's output feeds the next as a variable that must be captured separately
  - Set `subAgents: [all]` in frontmatter to apply to all agents

### Wave 2

- In each orchestration skill file, identify all consecutive independent Bash call groups and merge them:
  - Pattern to fix: Multiple separate Bash tool calls that check independent state (e.g., banner, lock verify,
    git status, plan read — each as its own call)
  - Pattern to apply: Single Bash call chaining all independent operations with `&&`
  - Do NOT chain: Commands that need their output captured separately into variables for use in subsequent logic
- Commit all skill changes together
- Update STATE.md (status: closed, progress: 100%) in same commit

## Post-conditions

- [ ] `plugin/rules/bash-efficiency.md` exists with the `&&` chaining convention
- [ ] All orchestration skill files have consecutive independent Bash calls merged into `&&` chains
- [ ] No 3+ consecutive Bash call groups remain for independent operations in the modified skills
- [ ] Convention frontmatter includes `subAgents: [all]` to apply to all agent types
