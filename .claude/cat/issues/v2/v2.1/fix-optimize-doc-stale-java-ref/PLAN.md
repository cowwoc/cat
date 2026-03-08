# Plan: fix-optimize-doc-stale-java-ref

## Problem

`plugin/skills/optimize-doc/first-use.md` lists `client/src/main/java/**/InjectSessionInstructions.java` as a
Claude-facing file to search for priming. That class no longer exists — its session instructions were moved to
`plugin/rules/`. The stale reference causes the optimize-doc skill to miss priming sources in `plugin/rules/`.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — single-line text change in a skill file
- **Mitigation:** N/A

## Files to Modify

- `plugin/skills/optimize-doc/first-use.md` — remove `client/src/main/java/**/InjectSessionInstructions.java` and
  add `plugin/rules/**/*.md` in the Claude-facing files list

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/optimize-doc/first-use.md`, find the list of Claude-facing files (the bullet list under
  "Claude-facing files are:"). Remove the line `- client/src/main/java/**/InjectSessionInstructions.java` and add
  `- plugin/rules/**/*.md` in its place (after `plugin/hooks/**/*.md (concept/reference docs)`).
  - Files: `plugin/skills/optimize-doc/first-use.md`
- Update STATE.md: status → closed, progress → 100%
  - Files: `.claude/cat/issues/v2/v2.1/fix-optimize-doc-stale-java-ref/STATE.md`

## Post-conditions

- [ ] `plugin/skills/optimize-doc/first-use.md` no longer contains `InjectSessionInstructions.java`
- [ ] `plugin/rules/**/*.md` appears in the Claude-facing files list in that skill
- [ ] E2E: grep for `InjectSessionInstructions` in `plugin/skills/optimize-doc/first-use.md` returns no matches
