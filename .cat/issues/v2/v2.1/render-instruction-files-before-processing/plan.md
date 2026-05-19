# Plan

## Goal

Update instruction-builder to process instruction files as release-rendered content by resolving include directives (for example `<!-- cat:include ... -->`) and applying edits to the originating source files that contribute rendered content, not blindly to the top-level instruction file.

## Parent Requirements

None

## Pre-conditions

(none)

## Post-conditions

- [ ] Instruction-builder renders include directives into an in-memory/effective file view before analysis or edit planning.
- [ ] Proposed edits map back to the correct source file(s) that produced the rendered text segments.
- [ ] Includes from multiple source files (for example bootstrap/common includes) are supported in one instruction file.
- [ ] A regression test covers editing text that originates from an included file and verifies the source include file is updated.
