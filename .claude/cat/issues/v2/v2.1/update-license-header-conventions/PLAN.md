# Plan: Update License Header Conventions

## Goal
Correct and consolidate license header conventions: move the authoritative documentation into project conventions,
fix exemptions, add headers to `first-use.md` files, and scrub existing headers from exempt locations.

## Execution Steps

1. **Move license-header.md to project conventions**
   - Move `plugin/concepts/license-header.md` to `.claude/rules/license-header.md`
   - Update any references to the old path

2. **Update exemptions in license-header.md**
   - Replace separate `.claude/cat/issues/` and `.claude/cat/` config entries with a single entry:
     `Files in .claude/cat/` (planning artifacts, config, runtime data)
   - Add exemption: `SKILL.md` files remain exempt; `first-use.md` files require headers

3. **Add license headers to first-use.md files**
   - Scan `plugin/skills/**/first-use.md` and add HTML comment header to each

4. **Remove license headers from all exempt locations**
   - Scan `.claude/cat/**` for files containing `Copyright (c) 2026` and remove the header block
   - Scan `plugin/skills/**/SKILL.md` for any headers and remove them
   - Scan `plugin/agents/**/*.md` for any headers and remove them

## Post-conditions

- `license-header.md` lives in `.claude/rules/`, not `plugin/concepts/`
- All `first-use.md` files have license headers
- No `SKILL.md` files have license headers
- No files under `.claude/cat/` have license headers
- No `plugin/agents/` markdown files have license headers
- `mvn verify` passes
