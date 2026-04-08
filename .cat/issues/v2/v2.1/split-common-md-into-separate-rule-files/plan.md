# Plan

## Goal

Split `.claude/rules/common.md` into focused, separately path-filtered rule files so each topic is only
loaded when relevant. Extract every section into its own file; if all content is extracted, delete
`common.md`. Add `paths:` frontmatter where the content is scoped to specific file types or directories.

## Extraction Map

| Section | New file | `paths:` filter |
|---------|----------|-----------------|
| Terminology: CAT Issues vs Claude TaskList | `.claude/rules/cat-issues-vs-tasklist.md` | (none — cross-cutting) |
| Language Requirements + Code Organization | `.claude/rules/language-requirements.md` | `["plugin/**", "client/**"]` |
| Multi-Instance Safety | `.claude/rules/multi-instance-safety.md` | `["plugin/**", "client/**"]` |
| Error Handling | `.claude/rules/error-handling.md` | (none — cross-cutting) |
| Configuration Reads in Worktrees | `.claude/rules/configuration-reads.md` | `[".cat/worktrees/**"]` |
| No Backwards Compatibility | `.claude/rules/backwards-compatibility.md` | (none — cross-cutting) |
| MEMORY.md vs Project Conventions | `.claude/rules/memory-conventions.md` | (none — cross-cutting) |
| Documentation Style | `.claude/rules/documentation-style.md` | `["*.md"]` |
| Pre-existing Problems + Recurring Problems | `.claude/rules/pre-existing-problems.md` | (none — cross-cutting) |
| Shell Efficiency | `.claude/rules/shell-efficiency.md` | `["*.sh"]` |
| Testing | `.claude/rules/testing-conventions.md` | `["client/**"]` |
| Naming Conventions | `.claude/rules/naming-conventions.md` | (none — cross-cutting) |
| Enforcement (cat-rules patterns) | distribute into the file that owns each enforced rule | varies |

The three `cat-rules` enforcement patterns in common.md belong with the rules they enforce:
- `jq` in `*.sh` → `language-requirements.md`
- `/workspace/` in `*.sh,*.md` → `multi-instance-safety.md`
- `FIXME|TODO|fallback|workaround` → `backwards-compatibility.md`

## Pre-conditions

(none)

## Post-conditions

- [ ] Each section listed in the extraction map exists as its own file with the specified `paths:` frontmatter
- [ ] Each extracted file contains only its own section's content (no duplication across files)
- [ ] Enforcement cat-rules patterns are moved into the files that own the rule they enforce
- [ ] `common.md` is deleted (all content extracted)
- [ ] No references to `common.md` remain in CLAUDE.md or any other file
