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

### Core Functionality
- [ ] Script reads git diff (stdin or file argument)
- [ ] Reads terminalWidth from cat-config.json
- [ ] Outputs variant 2 format
- [ ] Includes file-by-file, section-by-section structure
- [ ] Shows summary header (files, +/- lines)

### Text/Prose Files (markdown, txt, license)
- [ ] Wraps text at configured width
- [ ] Detects section boundaries (## headings, blank line paragraphs)
- [ ] Isolates changed phrases from unchanged context

### Code Files (java, python, js, ts, sh, json, yaml)
- [ ] Preserves original indentation exactly
- [ ] Does NOT wrap code lines (code readability > width compliance)
- [ ] Detects section boundaries by language:
  - Java/JS/TS: class/function/method definitions
  - Python: def/class definitions
  - Shell: function definitions
  - JSON/YAML: top-level keys
- [ ] Shows complete changed lines (not isolated phrases)
- [ ] Includes 2-3 lines of context around changes

### File Type Detection
- [ ] Detect file type from extension
- [ ] Apply appropriate formatting rules per type
- [ ] Fallback to code-style (no wrapping) for unknown types

### Whitespace & Invisible Character Visibility
- [ ] Show trailing whitespace explicitly: `line text····⏎` (using · or similar)
- [ ] Distinguish tabs from spaces: `→` for tab, `·` for space (when relevant)
- [ ] Mark line ending changes: `[LF→CRLF]` or `[CRLF→LF]`
- [ ] Flag non-standard whitespace (non-breaking space, zero-width chars)
- [ ] Whitespace-only changes get explicit callout: `[whitespace only]`
- [ ] Option to toggle verbose whitespace display (default: show only when changed)
