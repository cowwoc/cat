# Plan: consolidate-license-header-docs

## Goal
Replace the duplicated license header rules in CLAUDE.md with a single reference to `plugin/concepts/license-header.md`,
which is the authoritative source for license header formats, rules, and exemptions.

## Satisfies
- None (documentation cleanup)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Agents may miss the MANDATORY requirement if it's only in license-header.md
- **Mitigation:** Keep the MANDATORY marker and brief summary in CLAUDE.md, reference license-header.md for details

## Files to Modify
- `CLAUDE.md` - Replace license header section with brief summary + reference to license-header.md

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Consolidate CLAUDE.md license section:** Replace the full license header text, year rules, formatting rules, and
   exemption list with a brief "MANDATORY: All new source files must include a license header" note that references
   `plugin/concepts/license-header.md` for all details.

## Post-conditions
- CLAUDE.md license section contains only a brief MANDATORY note and a reference to license-header.md
- No duplicated rules or exemption lists between the two files
- `plugin/concepts/license-header.md` remains the single source of truth for all license header details
