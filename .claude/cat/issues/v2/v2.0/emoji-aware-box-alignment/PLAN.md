# Task Plan: emoji-aware-box-alignment

## Objective

Enable LLM-rendered closed-border boxes with emojis by providing terminal-specific emoji width
lookups, allowing proper padding calculation without relying on unreliable default assumptions.

## Problem Analysis

LLMs can calculate padding for ASCII text but struggle with emojis because:
1. Emoji display width varies by terminal (some render as 1 cell, others as 2)
2. LLMs cannot intrinsically know how a terminal renders specific emojis
3. Previous approach used "default" fallback widths, leading to misalignment

The solution requires:
1. Detecting the user's terminal type at session start
2. Looking up emoji widths from a measured database
3. Failing fast when data is missing (no guessing)

## Tasks

- [x] Create SessionStart hook to detect terminal type
- [x] Restore emoji-widths.json with terminal-specific measurements
- [x] Restore measure-emoji-widths.sh for users to contribute measurements
- [x] Update box-alignment skill to use emoji-widths.json
- [x] Remove default fallback - fail fast on missing data
- [x] Update stakeholder-review to use closed-border format
- [x] Update status.md to use closed-border format (keep new footer lines)
- [x] Update init.md banners to closed-border format
- [x] Update config.md boxes to closed-border format

## Technical Approach

### Terminal Detection
- SessionStart hook runs detect-terminal.sh
- Checks environment variables: WT_SESSION, TERM_PROGRAM, VSCODE_INJECTION, etc.
- Injects terminal type into session context

### Emoji Width Lookup
- emoji-widths.json stores measured widths per terminal
- Structure: `{ "terminals": { "Windows Terminal": { "🐱": 2, ... } } }`
- No default section - missing data triggers error with contribution instructions

### Fail-Fast Behavior
- If terminal not in emoji-widths.json → error with GitHub issue link
- If emoji not in terminal's table → error with measurement instructions
- Users run measure-emoji-widths.sh and report results

## Verification

- [x] Hook detects terminal type correctly
- [x] Box alignment skill references emoji-widths.json
- [x] Missing terminal/emoji triggers clear error with contribution instructions
- [x] All display formats updated to closed-border

## Files Changed

### New Files
- `plugin/emoji-widths.json` - Emoji width lookup table
- `plugin/scripts/measure-emoji-widths.sh` - Measurement script
- `plugin/hooks/detect-terminal.sh` - Terminal detection hook

### Modified Files
- `plugin/hooks/hooks.json` - Added detect-terminal hook
- `plugin/skills/box-alignment/SKILL.md` - Emoji width lookup + fail-fast
- `plugin/skills/stakeholder-review/SKILL.md` - Closed-border format
- `plugin/commands/status.md` - Closed-border format
- `plugin/commands/init.md` - Closed-border banners
- `plugin/commands/config.md` - Closed-border boxes
