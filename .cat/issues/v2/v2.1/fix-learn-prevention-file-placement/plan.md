# Plan

## Goal

Move add-agent description-argument guidance from CLAUDE.md to plugin/rules/work-request-handling.md. The M594
learn cycle placed this end-user behavioral rule in the developer conventions file instead of the end-user rules
layer. Remove the "Invoking add-agent with description" sub-section from CLAUDE.md and add equivalent guidance to
plugin/rules/work-request-handling.md so it is injected into every CAT session.

## Pre-conditions

(none)

## Post-conditions

- [ ] The "Invoking add-agent with description" sub-section is removed from CLAUDE.md (not the entire "Issue
  Workflow vs Direct Implementation" section — only the specific sub-section added by M594)
- [ ] Equivalent guidance is added to plugin/rules/work-request-handling.md in the end-user rules layer
- [ ] Content added to work-request-handling.md is semantically equivalent to what was removed from CLAUDE.md
  (same guidance, adapted for end-user context rather than developer conventions context)
- [ ] No new issues introduced
- [ ] E2E verification: Confirm the rule now appears in plugin/rules/ (confirming it will be injected into every
  CAT session rather than being developer-only)

## Research Findings

**File: CLAUDE.md (developer conventions, lines 35–44)**

The sub-section to remove is the bold paragraph starting with `**Invoking add-agent with description:**`
through the end of the code block and closing explanation, immediately before `**Wrong interpretation:**`.
Exact text to remove:

```
**Invoking add-agent with description:** When the intent is clear (e.g., a bug fix or specific feature
request), pass the description as the second argument to `cat:add-agent` to skip the type-selection menu
and go directly to issue creation:

```
skill: "cat:add-agent", args: "<cat_agent_id> fix work-prepare bug where agent ID is misinterpreted"
```

This routes directly to issue creation without asking the user to select between Issue / Patch version /
Minor version / Major version. Pass description whenever the intent is to create an issue (not a version).
```

After removal, `**Wrong interpretation:**` should immediately follow `**Correct interpretation:**` paragraph.

**File: plugin/rules/work-request-handling.md (end-user rules)**

Current content has frontmatter (`mainAgent: true`, `subAgents: []`) and work request handling rules.
The new guidance should be appended as a new section titled `## Passing a Description to add-agent` that
explains the same concept but framed for end-user context (not plugin developer conventions).

Note: Files in `plugin/rules/` are exempt from license headers per `.claude/rules/license-header.md`.

## Approach

Single-wave sequential implementation: remove the sub-section from CLAUDE.md, then add equivalent guidance
to plugin/rules/work-request-handling.md, then verify. No parallelism needed — the two files are independent
but the changes are small enough to do sequentially in one wave.

## Sub-Agent Waves

### Wave 1

- Remove the "Invoking add-agent with description" sub-section from
  `CLAUDE.md`. The sub-section starts with the line `**Invoking add-agent with description:**` and ends
  before the `**Wrong interpretation:**` line. Remove that entire paragraph block including the embedded
  code block and trailing blank line.

- Add guidance to `plugin/rules/work-request-handling.md` by appending the following new section after the
  existing content (before the final newline):

  ```markdown
  **Passing a description to add-agent:** When the intent is clear (e.g., a bug fix or specific feature
  request), pass the description as the second argument to `cat:add-agent` to skip the type-selection menu
  and go directly to issue creation:

  ```
  skill: "cat:add-agent", args: "<cat_agent_id> fix work-prepare bug where agent ID is misinterpreted"
  ```

  This routes directly to issue creation without asking the user to select between Issue / Patch version /
  Minor version / Major version. Pass description whenever the intent is to create an issue (not a version).
  ```

- E2E verification: Confirm that `plugin/rules/work-request-handling.md` contains the phrase
  "Passing a description to add-agent" and that `CLAUDE.md` no longer contains "Invoking add-agent
  with description".

- Update `.cat/issues/v2/v2.1/fix-learn-prevention-file-placement/index.json` to set `status: "closed"`
  and `progress: 100` in the same commit as the implementation changes.

- Commit all changes with message: `config: move add-agent description guidance to end-user rules layer`
  (CLAUDE.md is a config file; plugin/rules/ changes follow the same commit because the convention change
  and its application ship together per CLAUDE.md § "Convention changes belong with their application").
