# Plan: fix-skill-loader-arg-quoting

## Problem

When the Skill tool is invoked with args containing shell metacharacters (parentheses, brackets, braces,
wildcards, etc.), a zsh "bad pattern" error occurs. The shell interprets unquoted `(` and `)` as glob
patterns, causing the invocation to fail entirely. This blocks workflows that include descriptions or
content with these characters in skill args.

## Parent Requirements

None — infrastructure bugfix.

## Reproduction Code

```bash
# Invoking a skill with args containing parentheses fails:
# Skill tool: skill: "cat:plan-builder-agent"
#             args: "session-id high initial /tmp/file.json (occurrences: X/Y)"
# Error: bad pattern: (occurrences:
```

## Expected vs Actual

- **Expected:** Skill invocation succeeds; args containing `(`, `)`, `[`, `]`, `{`, `}`, `*`, `?`
  are treated as literals
- **Actual:** zsh "bad pattern" error when args contain unquoted parentheses or other metacharacters

## Root Cause

The SKILL.md preprocessor directives for skills that accept variable arguments use unquoted `$ARGUMENTS`
substitution. When the SkillLoader Java code expands `$ARGUMENTS` into the directive string and the
result is tokenized by `ShellParser.tokenize()`, parentheses are split across multiple tokens incorrectly
(e.g., `(with` and `parens)` become separate tokens instead of part of a single argument).

Additionally, some invocation paths may pass args through the user's shell (zsh) without proper quoting,
allowing metacharacter interpretation before Java receives the args.

The following SKILL.md files use unquoted `$ARGUMENTS` in their `!` directives:
- `plugin/skills/work-review-agent/SKILL.md` — `... work-review-agent $ARGUMENTS`
- `plugin/skills/research/SKILL.md` — `... research "${CLAUDE_SESSION_ID}" $ARGUMENTS`
- `plugin/skills/work-implement-agent/SKILL.md` — `... work-implement-agent $ARGUMENTS`
- `plugin/skills/work-merge-agent/SKILL.md` — `... work-merge-agent $ARGUMENTS`
- `plugin/skills/stakeholder-review-agent/SKILL.md` — `... stakeholder-review-agent $ARGUMENTS`
- `plugin/skills/work-with-issue-agent/SKILL.md` — `... work-with-issue-agent $ARGUMENTS`
- `plugin/skills/get-output-agent/SKILL.md` — `... get-output-agent $ARGUMENTS`
- `plugin/skills/work-confirm-agent/SKILL.md` — `... work-confirm-agent $ARGUMENTS`
- `plugin/skills/work-agent/SKILL.md` — `... work-agent $ARGUMENTS`
- `plugin/skills/research-agent/SKILL.md` — `... research-agent $ARGUMENTS`
- `plugin/skills/work/SKILL.md` — `... work "${CLAUDE_SESSION_ID}" $ARGUMENTS`
- `plugin/skills/work-complete-agent/SKILL.md` — `... work-complete-agent $ARGUMENTS`

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Changing `$ARGUMENTS` to `"$ARGUMENTS"` changes how multi-token args are passed.
  Skills that receive multi-word args as a single quoted argument rather than multiple separate args
  may behave differently. However, `ShellParser.tokenize()` already handles quoted strings, so
  `"$ARGUMENTS"` where ARGUMENTS = `arg1 arg2 arg3` tokenizes to `["arg1 arg2 arg3"]` (one token)
  while unquoted `$ARGUMENTS` tokenizes to `["arg1", "arg2", "arg3"]` (three tokens). This is a
  BREAKING CHANGE for skills that rely on individual token splitting. Each SKILL.md must be checked
  to understand its arg contract before changing.
- **Mitigation:** Check each skill's argument-hint and first-use.md to understand expected arg format.
  The correct fix may vary per skill: `"$ARGUMENTS"` when args are pre-quoted space-separated list
  passed as single token, vs individual positional args `"$1" "$2" "$3"` when exact split matters.

## Files to Modify

- `plugin/skills/work-review-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/research/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work-implement-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work-merge-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/stakeholder-review-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work-with-issue-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/get-output-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work-confirm-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/research-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work/SKILL.md` — fix `$ARGUMENTS` quoting
- `plugin/skills/work-complete-agent/SKILL.md` — fix `$ARGUMENTS` quoting
- `client/src/main/java/io/github/cowwoc/cat/hooks/ShellParser.java` — verify tokenize() handles
  all metacharacters correctly
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ShellParserTest.java` — add/verify tests for
  metacharacters in input

## Test Cases

- [ ] Original bug: invoke skill with args containing `(` and `)` — succeeds
- [ ] Brackets `[`, `]` in args — handled correctly
- [ ] Braces `{`, `}` in args — handled correctly
- [ ] Wildcard `*`, `?` in args — handled correctly
- [ ] Existing tests pass after quoting changes

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Investigate exact failure point: read all 12 SKILL.md files listed above and their corresponding
  `first-use.md` files to determine the expected argument contract for each skill (how many positional
  args it expects and whether `$ARGUMENTS` should be a single quoted string or multiple tokens).
  - Files: all 12 SKILL.md files, `plugin/skills/*/first-use.md` for each affected skill
- Read `client/src/main/java/io/github/cowwoc/cat/hooks/ShellParser.java` to confirm `tokenize()`
  handles all shell metacharacters correctly (not just parentheses)
- Read `client/src/test/java/io/github/cowwoc/cat/hooks/test/ShellParserTest.java` to understand
  existing coverage

### Wave 2

- For each SKILL.md in the list: based on Wave 1 analysis, apply the correct fix:
  - If args are passed as a single joined string: change `$ARGUMENTS` → `"$ARGUMENTS"`
  - If args need individual splitting AND could contain metacharacters: use positional quoting
    `"$1" "$2" ... "$N"` (only if the skill has a fixed arg count from `argument-hint`)
  - Document the decision in a comment if the change is non-obvious
  - Files: all 12 SKILL.md files listed above
- Add/update `ShellParserTest.java` tests to cover parentheses, brackets, braces, and wildcards
  in tokenized input
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/ShellParserTest.java`
- Run `mvn -f client/pom.xml test` to confirm all tests pass
- Update STATE.md: status closed, progress 100%
  - Files: `.claude/cat/issues/v2/v2.1/fix-skill-loader-arg-quoting/STATE.md`

## Post-conditions

- [ ] All 12 SKILL.md files have properly quoted `$ARGUMENTS` or equivalent in their directives
- [ ] `ShellParser.tokenize()` is verified to handle all common shell metacharacters correctly
- [ ] Tests added for shell metacharacter tokenization in `ShellParserTest.java`
- [ ] `mvn -f client/pom.xml test` passes with no failures
- [ ] E2E: invoke a skill with args containing `(text)` and confirm the skill loads correctly
  without a "bad pattern" error
