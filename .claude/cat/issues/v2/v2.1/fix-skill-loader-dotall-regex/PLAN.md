# Plan: fix-skill-loader-dotall-regex

## Problem
`SkillLoader.extractClassName()` uses regex `Pattern.compile("java.*?-m\\s+(\\S+)/(\\S+)")` which fails
on multi-line launcher scripts because `.*?` does not cross newline boundaries by default. Launcher scripts
use line continuations (`\` + newline) between `java` and `-m`, so the regex never matches. This causes
`executeDirective()` to silently return the original `!` backtick directive unexpanded, breaking all
preprocessor directives that reference launcher scripts (e.g., `/cat:status` output).

## Root Cause
`SkillLoader.java:375` — missing `Pattern.DOTALL` flag on the regex compilation.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — add `Pattern.DOTALL`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` — add failing test first (TDD)

## Acceptance Criteria
- [ ] Test proves regex fails on multi-line launcher content without fix
- [ ] Test passes after adding `Pattern.DOTALL`
- [ ] `load-skill.sh` for status skill produces expanded output (manual verification)
