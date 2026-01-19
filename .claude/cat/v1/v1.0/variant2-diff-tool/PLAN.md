# Plan: variant2-diff-tool

## Goal
Create a script/tool that converts git diff output to variant 2 format (isolated changes with context), respecting the configured terminal width from cat-config.json.

## Satisfies
- None (tooling improvement)

## Approach Outlines

### Conservative
Shell script using awk/sed to parse unified diff and reformat with basic section detection.
- **Risk:** LOW
- **Tradeoff:** Limited section detection, may not handle all edge cases

### Balanced
Python script that parses unified diff, detects section boundaries (markdown headings, function definitions, config keys), wraps text at configured width, and outputs variant 2 format.
- **Risk:** MEDIUM
- **Tradeoff:** Python dependency, but more robust parsing

### Aggressive
Full diff rendering library with language-aware section detection, syntax highlighting (ASCII-safe), and interactive section expansion.
- **Risk:** HIGH
- **Tradeoff:** Over-engineering, complexity

## Acceptance Criteria
- [ ] Script reads git diff (stdin or file argument)
- [ ] Reads terminalWidth from cat-config.json
- [ ] Outputs variant 2 format with proper text wrapping
- [ ] Detects section boundaries for markdown files (## headings)
- [ ] Detects section boundaries for code files (function/class definitions)
- [ ] Isolates changed text from unchanged context
- [ ] Includes file-by-file, section-by-section structure
- [ ] Shows summary header (files, +/- lines)
