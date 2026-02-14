# Plan: strip-m-codes-from-agent-docs

## Goal
Strip M-code references (e.g., `(M088)`, `(M252)`) from agent-facing documentation (skills, concepts) to reduce
context token usage. Add a convention to plugin documentation policy prohibiting M-codes in agent-facing files.

## Type
refactor

## Satisfies
None (infrastructure cleanup / token optimization)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None significant — uniform regex removal of parenthesized M-codes
- **Mitigation:** Pattern `\s*\(M\d{2,3}\)` targets only parenthesized labels; standalone M-codes in prose are
  not affected

## Files to Modify
- 32 files in `plugin/skills/**/*.md` — remove parenthesized M-code references (153 occurrences)
- 8 files in `plugin/concepts/**/*.md` — remove parenthesized M-code references (30 occurrences)
- `.claude/rules/common.md` — add M-code convention under Documentation Style section

## Files to NOT Modify
- `MEMORY.md` — human reference, keep M-codes
- `CLAUDE.md` — project config, keep M-codes
- `.claude/rules/*.md` — project config, keep M-codes (except adding the new convention)
- `.claude/cat/retrospectives/` — historical record, keep M-codes

## Acceptance Criteria
- [ ] No `(M###)` patterns remain in plugin/skills/**/*.md or plugin/concepts/**/*.md
- [ ] Convention added to .claude/rules/common.md under "Documentation Style" section as new subsection
  "### M-Code References"
- [ ] Tests passing

## Execution Steps
1. **Step 1:** Strip all parenthesized M-code references from plugin/skills/**/*.md files.
   Pattern: remove ` (M###)` — the space before the opening paren plus the parenthesized code.
   Clean up any resulting double-spaces or trailing whitespace.
   - Files: 32 files in `plugin/skills/**/*.md`
2. **Step 2:** Strip all parenthesized M-code references from plugin/concepts/**/*.md files.
   Same pattern as Step 1.
   - Files: 8 files in `plugin/concepts/**/*.md`
3. **Step 3:** Add convention to `.claude/rules/common.md` under Documentation Style section.
   New subsection "### M-Code References" stating: M-code labels (e.g., `(M088)`) must not appear
   in agent-facing documentation (plugin/skills/, plugin/concepts/, plugin/agents/). These labels
   consume context tokens without providing value to agents. Keep M-codes only in MEMORY.md,
   CLAUDE.md, .claude/rules/, and retrospective files.
   - Files: `.claude/rules/common.md`
4. **Step 4:** Verify no `(M###)` patterns remain in agent-facing docs. Run tests.
