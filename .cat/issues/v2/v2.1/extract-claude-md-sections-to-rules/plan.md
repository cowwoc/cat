# Plan: extract-claude-md-sections-to-rules

## Goal

Extract path-scoped sections from `CLAUDE.md` into separate `.claude/rules/` files with `paths:` frontmatter,
so each rule is only loaded when the agent is working in a relevant subdirectory.

## Current State

`CLAUDE.md` contains several sections that are only relevant when working in specific subtrees (`plugin/**`,
`client/**`, `plugin/skills/**`), but they are loaded unconditionally on every session. Four sections qualify
for extraction:

| Section | Lines | Current Load | After |
|---------|-------|-------------|-------|
| Plugin Development | 67–86 | always | `plugin/**`, `client/**` |
| Skill Loading | 88–95 | always | `plugin/skills/**`, `plugin/agents/**` |
| Skill Step Numbering | 97–107 | always | `plugin/skills/**`, `plugin/agents/**` |
| Bug Workaround Convention | 125–133 | always | `plugin/**`, `client/**` |

Sections remaining in `CLAUDE.md` (cross-cutting, always needed):
- Commit Types
- Issue Workflow vs Direct Implementation
- Approval Gate Workflow
- Testing Requirements
- License Headers

## Extraction Map

| Section | New file | `paths:` filter |
|---------|----------|-----------------|
| Plugin Development | `.claude/rules/plugin-development.md` | `["plugin/**", "client/**"]` |
| Skill Loading | `.claude/rules/skill-loading.md` | `["plugin/skills/**", "plugin/agents/**"]` |
| Skill Step Numbering | `.claude/rules/skill-step-numbering.md` | `["plugin/skills/**", "plugin/agents/**"]` |
| Bug Workaround Convention | `.claude/rules/bug-workaround.md` | `["plugin/**", "client/**"]` |

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Content must be copied verbatim — no paraphrasing or compression
- **Mitigation:** Verify each new file contains the exact text from CLAUDE.md before deleting from CLAUDE.md

## Files to Modify

- `CLAUDE.md` — remove the four extracted sections
- `.claude/rules/plugin-development.md` — create (new)
- `.claude/rules/skill-loading.md` — create (new)
- `.claude/rules/skill-step-numbering.md` — create (new)
- `.claude/rules/bug-workaround.md` — create (new)

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

Create four new path-filtered rule files. Read `CLAUDE.md` fully before extracting to capture exact wording.

**Note:** `.claude/rules/` files have no license headers (injected verbatim into agent context; headers waste
tokens). This is consistent with all existing files in that directory.

**File 1:** `.claude/rules/plugin-development.md`

Frontmatter:
```yaml
---
paths: ["plugin/**", "client/**"]
---
```

Content: The entire "Plugin Development" section from `CLAUDE.md` — the `## Plugin Development` heading
through the end of the section (just before `## Skill Loading`). Include all paragraphs and sub-sections
verbatim.

**File 2:** `.claude/rules/skill-loading.md`

Frontmatter:
```yaml
---
paths: ["plugin/skills/**", "plugin/agents/**"]
---
```

Content: The entire "Skill Loading" section from `CLAUDE.md` — the `## Skill Loading` heading through the
end of the section (just before `## Skill Step Numbering`). Include all paragraphs verbatim.

**File 3:** `.claude/rules/skill-step-numbering.md`

Frontmatter:
```yaml
---
paths: ["plugin/skills/**", "plugin/agents/**"]
---
```

Content: The entire "Skill Step Numbering" section from `CLAUDE.md` — the `## Skill Step Numbering` heading
through the end of the section (just before `## Testing Requirements`). Include all paragraphs verbatim.

**File 4:** `.claude/rules/bug-workaround.md`

Frontmatter:
```yaml
---
paths: ["plugin/**", "client/**"]
---
```

Content: The entire "Bug Workaround Convention" section from `CLAUDE.md` — the `## Bug Workaround Convention`
heading through the end of the section (just before `## License Headers`). Include all paragraphs verbatim.

**Commit:** Stage all 4 new files explicitly by path and commit with:
`config: extract path-scoped sections from CLAUDE.md into rules files`

**Do NOT commit index.json in Job 1.**

### Job 2

Remove the extracted sections from `CLAUDE.md` and close the issue.

**Step 1:** Edit `CLAUDE.md` to delete the following sections entirely (heading + all content):
- `## Plugin Development`
- `## Skill Loading`
- `## Skill Step Numbering`
- `## Bug Workaround Convention`

Do NOT alter any other sections. Verify the remaining sections flow correctly without gaps.

**Step 2:** Commit `CLAUDE.md`:
`config: remove sections extracted to .claude/rules/ from CLAUDE.md`

**Step 3:** Update `index.json`: set `"status": "closed"`, `"resolution": "implemented"`, and
`"last_updated": "<today's date>"`.

**Step 4:** Commit `index.json`:
`config: extract CLAUDE.md sections to path-filtered rules files`

## Post-conditions

- [ ] Each of the four new rule files exists with the correct `paths:` frontmatter
- [ ] Content of each new file matches the corresponding CLAUDE.md section verbatim
- [ ] `CLAUDE.md` no longer contains the four extracted sections
- [ ] `CLAUDE.md` remaining sections are intact and unmodified
- [ ] No other files reference the extracted sections in a way that now breaks
- [ ] `index.json` status is `closed` with `resolution: implemented`
