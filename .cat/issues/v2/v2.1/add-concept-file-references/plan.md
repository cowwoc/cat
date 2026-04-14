# Plan: add-concept-file-references

## Goal

Add "when to read" references for `plugin/concepts/instruction-testing.md` and
`plugin/concepts/instruction-test-design.md` in the skill files that need them. Neither file is
currently referenced by any skill or rule file in `plugin/`, leaving agents without guidance on when
to consult them.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation-only changes, no behavioral changes
- **Mitigation:** N/A

## Files to Modify

- `plugin/skills/instruction-builder-agent/first-use.md` — add reference to
  `instruction-testing.md` in the reference block or test/iterate section, with "when to read"
  guidance tied to Steps 9–13 of the workflow (write test cases, spawn runs, grade, analyze, iterate)
- `plugin/skills/instruction-builder-agent/testing.md` — add reference to
  `instruction-test-design.md` at the top as a "when to read" pointer: read this file when
  designing, writing, or reviewing `.md` test case files (frontmatter, Turn 1, Assertions format,
  positive/negative templates, two-tier verification, pass thresholds)
- `plugin/skills/sprt-runner-agent/first-use.md` — add references to both concept files:
  - `instruction-testing.md`: read when understanding the test/iterate workflow that sprt-runner-agent
    implements (grading output schema, test JSON schema, aggregation inputs)
  - `instruction-test-design.md`: read when examining test case `.md` files to understand the file
    format, category values, Turn 1 requirements, and assertion conventions the runner processes

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- In `plugin/skills/instruction-builder-agent/first-use.md`:
  - Locate the section that describes the test/iterate workflow steps (Steps 9–13: write test cases,
    spawn runs, grade and aggregate, analyze and review, improve and iterate)
  - Add a "when to read" reference: "Read `${CLAUDE_PLUGIN_ROOT}/concepts/instruction-testing.md`
    before executing the test/iterate workflow steps (Steps 9–13): it defines the eval set format,
    assertion schemas, grading output schema, test JSON schema, and the full loop."

- In `plugin/skills/instruction-builder-agent/testing.md`:
  - Add at the top of the file (after the license header and title, before the first section):
    a "when to read" callout: "Read `${CLAUDE_PLUGIN_ROOT}/concepts/instruction-test-design.md`
    when writing or reviewing `.md` test case files. It defines the file format (frontmatter,
    Turn 1, Assertions), positive/negative case templates, two-tier verification, organic design
    rules, and pass thresholds."

- In `plugin/skills/sprt-runner-agent/first-use.md`:
  - Add to the Prerequisites or a new Reference Files section:
    - "Read `${CLAUDE_PLUGIN_ROOT}/concepts/instruction-testing.md` when you need the grading output
      schema, test JSON schema, or aggregation input format used in the SPRT loop."
    - "Read `${CLAUDE_PLUGIN_ROOT}/concepts/instruction-test-design.md` when examining test case
      `.md` files — it defines the category values, Turn 1 content requirements, assertion syntax,
      and positive/negative case conventions the runner processes."

## Post-conditions

- [ ] `plugin/skills/instruction-builder-agent/first-use.md` references `instruction-testing.md`
  with a "when to read" trigger tied to Steps 9–13
- [ ] `plugin/skills/instruction-builder-agent/testing.md` references `instruction-test-design.md`
  with a "when to read" trigger for writing/reviewing `.md` test case files
- [ ] `plugin/skills/sprt-runner-agent/first-use.md` references both concept files with
  "when to read" triggers tied to their specific use cases
- [ ] No references point to `.claude/` paths (concept files are in `plugin/concepts/` — a deployed
  path, compliant with `plugin-file-references.md`)
