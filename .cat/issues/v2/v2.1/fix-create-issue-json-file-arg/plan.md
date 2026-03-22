# Plan

## Goal

Fix `create-issue` CLI to: (1) accept a file path argument instead of inline JSON (matching the
`plan_file` pattern already used for plan.md), and (2) always pretty-print `index.json` by parsing
and re-serializing `index_content` through the shared JsonMapper before writing.

Currently `IssueCreator.java` writes `index_content` directly via `Files.writeString()` without
formatting, so the output format depends entirely on how the caller constructed the string. Inline
JSON arguments are also fragile (escaping issues, length limits, multiline restrictions). The fix
moves JSON passing to a temp-file pattern and enforces pretty-printing server-side.

## Pre-conditions

(none)

## Post-conditions

- [ ] `create-issue` CLI accepts an `index_file` field (path to a JSON file) instead of
  `index_content` (inline JSON string) in its input — matching how `plan_file` already works
- [ ] `IssueCreator.java` reads index content from the file, parses it through JsonMapper, and
  re-serializes with `INDENT_OUTPUT` before writing `index.json` — so output is always
  pretty-printed regardless of input formatting
- [ ] The `add-agent` skill instructions (`plugin/skills/add-agent/first-use.md`) are updated
  to write index JSON to a temp file and pass `index_file` instead of `index_content`
- [ ] `IssueCreatorTest.java` is updated to pass a temp file path and verify `index.json` is
  pretty-printed (indented) in the output
- [ ] Java test suite passes with no regressions (`mvn -f client/pom.xml test`)
- [ ] E2E: run `/cat:add` end-to-end and confirm the created `index.json` is pretty-printed
