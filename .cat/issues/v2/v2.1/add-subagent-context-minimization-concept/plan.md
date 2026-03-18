# Plan: add-subagent-context-minimization-concept

## Goal

Create a shared concept file `plugin/concepts/subagent-context-minimization.md` that documents the
context minimization pattern for agent workflows, covering when to delegate work to subagents, how to
pass file paths and references instead of relaying full content, and correct vs. anti-patterns with
codebase examples. Update `optimize-execution` and `skill-builder-agent` skills to reference this
concept file.

## Parent Requirements

None

## Approaches

### A: New standalone concept file + reference additions (chosen)

- **Risk:** LOW
- **Scope:** 3 files (1 new, 2 modifications)
- **Description:** Create `plugin/concepts/subagent-context-minimization.md` as the single authoritative
  source for the context minimization pattern, then add `Related Concepts` references in both
  `optimize-execution/first-use.md` and `skill-builder-agent/first-use.md`. The new concept file
  consolidates content already scattered across `optimize-execution/first-use.md` (Steps 3.4 and 6.10)
  and `plugin/concepts/subagent-delegation.md` (the pre-spawn checklist).

### B: Inline documentation expansion (rejected)

- **Risk:** MEDIUM
- **Scope:** 2 files (larger modifications)
- **Description:** Expand the existing content in `optimize-execution/first-use.md` and
  `skill-builder-agent/first-use.md` in-place. Rejected because it duplicates the pattern across files
  with no single canonical source, making future updates inconsistent.

### C: Extend subagent-delegation.md (rejected)

- **Risk:** MEDIUM
- **Scope:** 1 file + references
- **Description:** Append context-minimization content to the existing
  `plugin/concepts/subagent-delegation.md`. Rejected because subagent-delegation.md covers general
  subagent safety rules (fail-fast, acceptance criteria, validation separation), and context
  minimization is a distinct topic about token efficiency that warrants its own file.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The new concept file must not duplicate wording already in `subagent-delegation.md` or
  `optimize-execution/first-use.md`; it should cross-reference them instead.
- **Mitigation:** Read the existing files before writing; use explicit cross-references where content
  overlaps rather than restating it.

## Files to Modify

- `plugin/concepts/subagent-context-minimization.md` — **CREATE**: new concept file documenting the
  context minimization pattern
- `plugin/skills/optimize-execution/first-use.md` — add a reference under `## Related Concepts`
  pointing to the new concept file
- `plugin/skills/skill-builder-agent/first-use.md` — add a reference under `## Related Concepts`
  (or a new such section) pointing to the new concept file

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Create `plugin/concepts/subagent-context-minimization.md` with license header and full content
  - Files: `plugin/concepts/subagent-context-minimization.md`

### Wave 2

- Add reference to `plugin/concepts/subagent-context-minimization.md` in the `## Related Concepts`
  section of `plugin/skills/optimize-execution/first-use.md`
  - Files: `plugin/skills/optimize-execution/first-use.md`
- Add a `## Related Concepts` section (if absent) and reference to
  `plugin/concepts/subagent-context-minimization.md` in `plugin/skills/skill-builder-agent/first-use.md`
  - Files: `plugin/skills/skill-builder-agent/first-use.md`
- Update `STATE.md` to reflect implementation complete
  - Files: `.cat/issues/v2.1/add-subagent-context-minimization-concept/STATE.md`

## New Concept File Specification

### File: `plugin/concepts/subagent-context-minimization.md`

The file must include a license header (HTML comment format, as per `plugin/concepts/*.md` convention —
but note that `plugin/concepts/` files are NOT in the SKILL.md exemption list, so headers ARE required).

**Sections to include (in order):**

1. **Purpose** — one paragraph: the goal is to keep the main agent's context lean by having subagents
   load their own content from disk rather than having the main agent relay it.

2. **When to Delegate to a Subagent** — decision table adapting the quick decision table from
   `optimize-execution/first-use.md` Step 4 (Pre-Delegation Estimation). Conditions: main agent
   context size, estimated subagent turns, and verdict. Cross-reference
   `plugin/skills/optimize-execution/first-use.md` for the full estimation formula.

3. **How to Pass References Instead of Content** — two subsections:
   - **File references**: pass file paths (absolute or relative to worktree root), not file contents
   - **Git references**: pass commit SHAs and branch names, not diff text or file snapshots
   - **Task descriptions**: pass the description of what to do, not a pre-read expansion of the
     instructions

4. **Correct Patterns** — three code-block examples:
   - Pass file path to subagent (correct)
   - Pass commit SHA instead of diff text (correct)
   - Pass task description with well-defined inputs, not inline content (correct)

5. **Anti-Patterns** — three matching examples:
   - Main agent reads file then pastes into subagent prompt (anti-pattern)
   - Main agent runs `git diff` then pastes output into subagent prompt (anti-pattern)
   - Main agent reads test output then passes verbatim to fix subagent (anti-pattern)

   After anti-patterns, include the exception clause: if the main agent already read the file for
   its own decision-making (e.g., reviewing PLAN.md to choose phases), it MAY include that content
   to save a redundant subagent read.

6. **Codebase Examples** — point to real examples:
   - `plugin/skills/optimize-execution/first-use.md` Step 6.10 (content relay detection)
   - `plugin/concepts/subagent-delegation.md` (pre-spawn checklist item 7: relevant project
     conventions passed inline)
   - The `plugin/skills/skill-builder-agent/first-use.md` Step 2 design-subagent delegation
     (passes file paths, not file content)

7. **Related Concepts** — links to:
   - `plugin/concepts/subagent-delegation.md`
   - `plugin/skills/optimize-execution/first-use.md`

### Reference format for concepts files

Concepts files in `plugin/concepts/` use plain markdown links. Use relative paths:
```
See [subagent-delegation](subagent-delegation.md) for general subagent safety rules.
```

### Reference to add in `optimize-execution/first-use.md`

Add to the existing `## Related Concepts` section (currently the last section, at line 656):

```
- **subagent-context-minimization**: When and how to pass file paths instead of file content to subagents
```

### Reference to add in `skill-builder-agent/first-use.md`

The file currently ends with `## Verification`. Append a new section:

```markdown
## Related Concepts

- **subagent-context-minimization**: When to delegate to subagents and how to pass references instead of
  content — `plugin/concepts/subagent-context-minimization.md`
```

## Post-conditions

- [ ] `plugin/concepts/subagent-context-minimization.md` exists with license header, Purpose, When to
  Delegate, How to Pass References, Correct Patterns, Anti-Patterns, Codebase Examples, and
  Related Concepts sections
- [ ] `plugin/skills/optimize-execution/first-use.md` `## Related Concepts` references
  `subagent-context-minimization`
- [ ] `plugin/skills/skill-builder-agent/first-use.md` has a `## Related Concepts` section that
  references `subagent-context-minimization`
- [ ] License header is present in the new concept file (HTML comment block at top)
- [ ] E2E: Read all three modified/created files and confirm cross-references are consistent and no
  section duplicates content from `plugin/concepts/subagent-delegation.md` verbatim
