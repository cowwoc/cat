<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: word-level-diff-output

## Goal
Improve diff output to highlight per-word changes instead of per-line changes. Claude Code does not support ANSI
colors, so the solution must use monochrome formatting (markdown bold, code fences, bracketed markers, or similar).

## Type
feature

## Satisfies
None

## Research Required
This issue requires research before implementation. Key questions:

1. **What formatting survives Claude Code's output rendering?** Test whether markdown bold (`**word**`), inline code
   (`` `word` ``), strikethrough (`~~word~~`), or bracketed markers (`[+word+]` / `[-word-]`) render distinctly in the
   terminal.

2. **What word-diff algorithms exist?** Explore `git diff --word-diff`, `git diff --color-words`, or custom
   token-level diff algorithms. Consider whether the Java CLI can compute word-level diffs directly.

3. **How does the current render-diff skill work?** Examine `plugin/skills/render-diff-first-use/SKILL.md` and any
   Java classes that generate the 4-column diff table to understand the current architecture.

4. **What are the constraints?** Claude Code terminal output is monochrome. The solution must be readable without
   colors. Consider table width limits and how word-level markers affect column alignment.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Monochrome word-level diffs may be harder to read than line-level diffs if the marker syntax is too
  verbose. Table alignment may break with inline markers.
- **Mitigation:** Prototype multiple formatting approaches and test readability empirically.

## Approaches

### A: Git word-diff with bracket markers
- **Risk:** LOW
- **Scope:** 2-3 files (moderate)
- **Description:** Use `git diff --word-diff=plain` which outputs `[-removed-]` and `{+added+}` markers. Parse and
  format into the existing 4-column table.

### B: Custom token-level diff in Java
- **Risk:** MEDIUM
- **Scope:** 3-5 files (moderate)
- **Description:** Implement a word-level diff algorithm in Java that compares tokens within changed lines and
  wraps changed words in markdown bold or similar markers.

### C: Side-by-side with inline bold markers
- **Risk:** MEDIUM
- **Scope:** 3-5 files (moderate)
- **Description:** Keep the 4-column table but add `**bold**` around changed words within each line. Requires
  computing per-word diff for each changed line pair.

## Acceptance Criteria
- [ ] Changed words within a line are visually distinguishable from unchanged words
- [ ] Formatting works in Claude Code terminal (monochrome, no ANSI colors)
- [ ] Existing line-level diff still works for added/removed lines (word-level applies to modified lines only)
- [ ] Output remains readable at typical terminal widths (120-200 chars)

## Execution Steps
1. **Research:** Run `/cat:research` to investigate formatting options and test what renders in Claude Code
2. **Prototype:** Implement the chosen approach in the Java diff renderer
3. **Test:** Validate with real diffs from recent issues
4. **Integrate:** Update render-diff skill to use the new output format

## Success Criteria
- [ ] Word-level changes are visually distinct in Claude Code terminal output
- [ ] No regression in line-level diff rendering
