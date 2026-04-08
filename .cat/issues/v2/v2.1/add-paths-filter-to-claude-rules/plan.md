# Plan

## Goal

Add `paths:` frontmatter to `.claude/rules/` files that are narrowly scoped to specific file types or
directories, so they are only loaded into context when relevant files are active. This reduces token
consumption on sessions that don't touch the filtered paths.

## Pre-conditions

(none)

## Post-conditions

- [ ] `.claude/rules/scope-passing.md` has `paths: ["*.java"]` frontmatter
- [ ] `.claude/rules/jackson.md` has `paths: ["*.java"]` frontmatter
- [ ] `.claude/rules/llm-to-java.md` has `paths: ["plugin/**", "client/**"]` frontmatter
- [ ] `.claude/rules/hooks.md` has `paths: ["plugin/hooks/**", ".claude/settings.json", "client/**"]` frontmatter
- [ ] `.claude/rules/skills.md` has `paths: ["plugin/skills/**"]` frontmatter
- [ ] `.claude/rules/plugin-file-references.md` has `paths: ["plugin/**"]` frontmatter
- [ ] No other content in any of the above files is changed
- [ ] `java.md` and `index-schema.md` (already path-filtered) are not modified

## Research Findings

Claude Code supports a `paths:` key in YAML frontmatter for `.claude/rules/` files. When present, the rule
is only injected into context when one of the active files matches the specified glob patterns. The
frontmatter block must be placed at the very top of the file, before any other content. The inline YAML array
syntax `paths: ["glob1", "glob2"]` is used consistently with the post-conditions.

None of the six target files currently have frontmatter.

## Commit Type

`config:` — changes to `.claude/` configuration files

## Jobs

### Job 1

Add `paths:` frontmatter to all six target rules files, and update index.json.

For each file below, prepend the YAML frontmatter block shown. The frontmatter must be the very first content
in the file — insert it before the existing first line. Do not alter any other content in these files.

**`.claude/rules/scope-passing.md`** — prepend:
```
---
paths: ["*.java"]
---
```

**`.claude/rules/jackson.md`** — prepend:
```
---
paths: ["*.java"]
---
```

**`.claude/rules/llm-to-java.md`** — prepend:
```
---
paths: ["plugin/**", "client/**"]
---
```

**`.claude/rules/hooks.md`** — prepend:
```
---
paths: ["plugin/hooks/**", ".claude/settings.json", "client/**"]
---
```

**`.claude/rules/skills.md`** — prepend:
```
---
paths: ["plugin/skills/**"]
---
```

**`.claude/rules/plugin-file-references.md`** — prepend:
```
---
paths: ["plugin/**"]
---
```

After editing all six files, update `index.json` in the same commit. Replace the entire file content
with the following:

```json
{
  "status" : "closed",
  "dependencies" : [ ],
  "blocks" : [ ],
  "target_branch" : "v2.1",
  "resolution" : "implemented"
}
```

Commit message: `config: add paths: frontmatter to narrowly-scoped .claude/rules/ files`
