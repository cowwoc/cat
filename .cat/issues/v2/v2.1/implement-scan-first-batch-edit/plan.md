# Plan

## Goal

Add a new `cat:scan-and-edit` skill that implements a scan-first, batch-edit, compile-once pattern. The
`plugin/agents/work-execute.md` implementation subagent will call this skill before editing any files. The skill greps
all usages of every symbol being changed (renamed, removed, or moved), builds a complete file→changes map, applies all
edits without intermediate compilation, then compiles once at the end. This eliminates the compile-fix loop that caused
an 11-hour looping session when a Wave 2 implementation subagent encountered cascading Java refactoring errors.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/scan-and-edit/SKILL.md` created with valid frontmatter (`description`, `user-invocable: false`,
  `allowed-tools`, `argument-hint`) and preprocessor directive pointing to `first-use.md`
- [ ] `plugin/skills/scan-and-edit/first-use.md` created with license header and four-phase agent instructions:
  (1) scan — grep all usages of symbols being changed before any edits, (2) map — build complete file→changes list,
  (3) edit — apply all changes without recompiling between files, (4) compile — run build once at the end
- [ ] `plugin/agents/work-execute.md` updated to invoke `cat:scan-and-edit` (via `skill: "cat:scan-and-edit"`)
  before any file editing steps
- [ ] All existing tests pass after changes
- [ ] E2E: Manually run a refactoring scenario with multi-file symbol removal and confirm build passes with zero
  intermediate compilations

## Research Findings

The scan-first pattern eliminates cascading compile-fix loops that occur in multi-file Java refactoring. When a symbol
is renamed or removed, references exist in: class declarations, import statements, method signatures, field types,
local variable types, constructor calls, and javadoc. Grepping all usages before the first edit produces a complete
change map; applying all changes in one pass and compiling once eliminates the "compile → find new error → fix →
compile again" loop.

The `plugin/agents/work-execute.md` file is the implementation subagent spec. It currently instructs the agent to
follow plan.md execution steps but has no explicit guidance for symbol refactoring. Adding a section with the Skill
invocation pattern is the correct integration point.

For `SKILL.md` frontmatter: `allowed-tools` must list only tools the invoking agent needs during the scan-and-edit
workflow (Bash, Grep, Read, Edit). The `description` field drives skill trigger detection, so it must clearly describe
when to use the skill.

For `first-use.md`: the license header is an HTML comment at the top of the file (no YAML frontmatter in first-use.md
files). The four-phase structure ensures the agent follows the correct order: scan everything first, build the full
change map, then apply all edits, then compile once.

## Jobs

### Job 1

**File: `plugin/skills/scan-and-edit/SKILL.md`** (create; exempt from license headers)

Write exactly:
```
---
description: Before editing any files when renaming, removing, or moving Java symbols — scan all usages first,
  build a complete file→changes map, apply all edits without intermediate compilation, then compile once
model: haiku
user-invocable: false
argument-hint: "<cat_agent_id> <symbol1> [symbol2 ...]"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" scan-and-edit "$0"`
```

**File: `plugin/skills/scan-and-edit/first-use.md`** (create; license header required)

Begin with the standard HTML license header comment block (see `.claude/rules/license-header.md`), then write a
`# Scan-and-Edit` heading followed by four numbered phases:

- **Phase 1 — Scan:** For each symbol passed as an argument (e.g., class name, method name, field name), run
  `grep -rn "<symbol>" "${WORKTREE_PATH}"` to find every occurrence across all files. Collect the full list of
  matching files and line numbers before touching any file.
- **Phase 2 — Map:** Build a concrete change map as a markdown table with columns: `File`, `Line`, `Change`.
  Each row must identify the exact path (relative to `${WORKTREE_PATH}`), the line number, and the
  replacement text. If a symbol has zero matches, note it explicitly and proceed — no error.
- **Phase 3 — Edit:** Apply every row in the change map using the Edit tool. Do NOT run any build command
  between individual edits. Process all files before compiling.
- **Phase 4 — Compile:** After all edits are applied, run `mvn -f "${WORKTREE_PATH}/client/pom.xml" test`
  exactly once. If the build fails, fix compilation errors by repeating phases 1–4 for the newly surfaced
  symbols, then compile again.

Edge cases to address in the instructions:
- If a symbol also appears in non-Java files (Markdown, JSON, shell scripts), include those occurrences in
  Phase 1 scan results and apply changes in Phase 3 if they reference the symbol (e.g., import paths in docs).
- If a symbol has zero grep matches, log "No usages found for <symbol>" in the change map and continue.

**File: `plugin/agents/work-execute.md`** (update; add section after `## Key Constraints` which ends at line 65)

Insert a new `## Java Symbol Refactoring` section at the end of the file (after line 65). The section must
contain exactly:

```markdown
## Java Symbol Refactoring

When plan steps involve renaming, removing, or moving a Java symbol (class, method, field, or type), invoke
`cat:scan-and-edit` via the Skill tool BEFORE making any file edits:

```
skill: "cat:scan-and-edit", args: "<cat_agent_id> <Symbol1> [Symbol2 ...]"
```

Pass the symbol names as positional arguments after the cat_agent_id. The skill will scan all usages, build
a change map, apply all edits, and compile once at the end.
```

**Build verification:**

Run `mvn -f "${WORKTREE_PATH}/client/pom.xml" test` to verify all existing tests pass.

**Commit:**

Stage all three files (`plugin/skills/scan-and-edit/SKILL.md`, `plugin/skills/scan-and-edit/first-use.md`,
`plugin/agents/work-execute.md`) plus `index.json` (set to `{"status": "closed"}`) in a single commit.
Commit message: `feature: add cat:scan-and-edit skill for Java symbol refactoring`
