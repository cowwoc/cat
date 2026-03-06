# Plan: split-execution-waves-into-agent-sections

## Current State

PLAN.md files use a single `## Execution Waves` section for all execution steps. The `work-with-issue-agent` skill
pattern-matches PLAN.md for a hardcoded list of skill names (`optimize-doc`, `compare-docs`,
`stakeholder-review-agent`) to detect which steps require main-agent-level invocation. This is brittle: adding a new
main-agent skill requires updating the grep pattern in `work-with-issue-agent`.

## Target State

PLAN.md files use two execution sections:

1. **`## Main Agent Waves`** (above, optional) ��� steps the main agent executes directly before spawning implementation
   subagents. Each item is a skill invocation that requires main-agent-level execution (e.g., `/cat:optimize-doc`,
   `/cat:compare-docs`, `/cat:stakeholder-review-agent`).
2. **`## Sub-Agent Waves`** (below, renamed from `## Execution Waves`) ��� steps delegated to implementation subagents
   via the Task tool.

`work-with-issue-agent` reads `## Main Agent Waves` from PLAN.md and executes each listed skill directly, eliminating
the brittle grep-based skill detection pattern.

## Parent Requirements

None ��� aligns with existing anti-pattern documentation in `optimize-execution/first-use.md`

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** PLAN.md schema change; existing `## Execution Waves` sections must be renamed
- **Mitigation:** Migration script (2.1.sh Phase 14) renames 546 existing files; `## Main Agent Waves` is optional
  (absent = no pre-invocation step, fully backward compatible)

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` ��� replace grep pattern-matching with Main Agent Waves reader
- `plugin/skills/add/first-use.md` ��� update PLAN.md templates to use Sub-Agent Waves; document Main Agent Waves
- `plugin/migrations/2.1.sh` ��� add Phase 14: rename `## Execution Waves` ��� `## Sub-Agent Waves` in PLAN.md files

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Update `plugin/skills/work-with-issue-agent/first-use.md`:
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
  - Find the "Read PLAN.md and Identify Skills" section (Step 3)
  - Replace the grep-based skill detection with logic that reads `## Main Agent Waves` from PLAN.md:
    ```bash
    MAIN_AGENT_WAVES=$(sed -n '/^## Main Agent Waves/,/^## /p' "$PLAN_MD" | head -n -1)
    ```
  - If `MAIN_AGENT_WAVES` is non-empty, extract each bullet item (`- /cat:skill-name args`) and invoke
    the corresponding skill at the main agent level using the Skill tool
  - If `MAIN_AGENT_WAVES` is absent (old-format PLAN.md), skip pre-invocation entirely (backward compatible)
  - Keep wave count detection with fallback: check for `## Sub-Agent Waves` first, fall back to `## Execution Waves`
    for old files: `grep -c "^### Wave " "$PLAN_MD"` still works since both sections use `### Wave N`

- Update `plugin/skills/add/first-use.md`:
  - Files: `plugin/skills/add/first-use.md`
  - Rename `## Execution Waves` ��� `## Sub-Agent Waves` in all PLAN.md templates and examples
  - Add documentation for the optional `## Main Agent Waves` section (placed above `## Sub-Agent Waves`):
    - Explain it is optional and only needed when the issue uses skills that spawn subagents (optimize-doc,
      compare-docs, stakeholder-review)
    - Each bullet is a skill invocation: `- /cat:optimize-doc path/to/file.md`
  - Preserve support note: work-with-issue-agent falls back to `## Execution Waves` for old files

- Update `plugin/migrations/2.1.sh`:
  - Files: `plugin/migrations/2.1.sh`
  - Add entry 14 to the migration header comment block
  - Add Phase 14 implementation: rename `## Execution Waves` ��� `## Sub-Agent Waves` in PLAN.md files
    under `.claude/cat/issues/`
  - Use `sed -i` with idempotency check: skip files that already contain `## Sub-Agent Waves`
  - Log count of files modified

- Commit: `refactor: split execution waves into main-agent and sub-agent sections`

## Post-conditions

- [ ] work-with-issue-agent reads `## Main Agent Waves` from PLAN.md instead of grepping for skill names
- [ ] work-with-issue-agent falls back gracefully when `## Main Agent Waves` is absent (no pre-invocation)
- [ ] work-with-issue-agent wave count detection handles both `## Sub-Agent Waves` and `## Execution Waves`
- [ ] `add-agent` PLAN.md templates use `## Sub-Agent Waves` for implementation steps
- [ ] `add-agent` documents `## Main Agent Waves` as the optional section for main-agent skill invocations
- [ ] `plugin/migrations/2.1.sh` Phase 14 renames `## Execution Waves` ��� `## Sub-Agent Waves` in existing PLAN.md files
- [ ] Migration is idempotent (safe to run multiple times)
- [ ] E2E: Create a test PLAN.md with `## Main Agent Waves` containing `/cat:optimize-doc` and confirm
      work-with-issue-agent invokes it at the main agent level without pattern matching