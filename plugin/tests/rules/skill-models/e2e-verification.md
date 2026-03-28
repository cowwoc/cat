# E2E Verification: instruction-builder path formula for plugin/rules/skill-models.md

## Test target

File: `plugin/rules/skill-models.md`

## TEST_DIR path derivation

Input: `SKILL_TEXT_PATH=plugin/rules/skill-models.md`

Steps:
1. Strip `plugin/` prefix: `SKILL_RELATIVE=rules/skill-models.md`
2. Remove file extension: `SKILL_RELATIVE_NO_EXT=rules/skill-models`
3. Prepend `plugin/tests/`: `TEST_DIR=plugin/tests/rules/skill-models`

Result: `plugin/tests/rules/skill-models/`

## Iteration 1 verification (manual path formula check)

- [x] TEST_DIR path formula produces `plugin/tests/rules/skill-models/` (not `plugin/rules/skill-models/test/`)
- [x] Formula correctly handles non-skill file target (`plugin/rules/*.md`)
- [x] Directory created at expected location

## Iteration 2 verification (bash formula execution)

Path formula executed via bash with `SKILL_TEXT_PATH=plugin/rules/skill-models.md`:

```
SKILL_RELATIVE="${SKILL_TEXT_PATH#plugin/}"        → rules/skill-models.md
SKILL_RELATIVE_NO_EXT="${SKILL_RELATIVE%.*}"       → rules/skill-models
TEST_DIR="${CLAUDE_PROJECT_DIR}/plugin/tests/${SKILL_RELATIVE_NO_EXT}"
                                                   → .../plugin/tests/rules/skill-models
```

- [x] Bash formula produces `plugin/tests/rules/skill-models` (confirmed via shell execution)
- [x] Path does NOT contain `plugin/rules/skill-models/test/` (old formula path)
- [x] Formula handles non-skill files correctly (strips `plugin/` prefix, removes extension, prepends `plugin/tests/`)
- [x] Implementation in `first-use.md` lines 233-239 matches the expected formula

Note: instruction-builder-agent cannot be invoked from a subagent context
(skill constraint: "MAIN AGENT ONLY"). The path formula was verified via
direct bash execution of the formula from `first-use.md` lines 233-239.
