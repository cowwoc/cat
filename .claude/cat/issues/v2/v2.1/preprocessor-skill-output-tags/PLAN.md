# Plan: preprocessor-skill-output-tags

## Problem

Three related problems with skill preprocessor output:

1. **Preprocessor failure detection:** When SkillLoader's preprocessor fails to expand a `!` backtick directive, the
   agent receives raw unexpanded text and hallucinates output. Wrapping successful output in `<skill-output>` XML tags
   lets the agent detect missing tags as a preprocessing failure.

2. **Post-compaction skill reload:** After context compaction, the session marker file persists but the agent's context
   no longer contains original skill instructions. SkillLoader returns reference.md (empty) instead of first-use.md.

3. **Shared marker file problem for subagents:** Parent and subagent share `CLAUDE_SESSION_ID`, so the marker file is
   shared state. If the parent invokes a skill, the subagent gets reference.md (empty) for the same skill. No mechanism
   exists to detect subagent context from the preprocessor.

## Fix

### Phase 1 (done — existing commits)
1. Wrap successful preprocessor output in `<skill-output>` XML tags as defense-in-depth.
2. Clear session marker file on context compaction via PreCompact hook.
3. Add skill-loading.md concept doc with cross-references.

### Phase 2 (new — `-first-use` skill pattern)
Restructure skill files so subagents can preload skill content via `skills:` frontmatter without hitting the shared
marker file problem.

**Architecture:** For each skill `foo` that needs subagent preloading, create a companion skill `foo-first-use`:

```
skills/
  reference.md              ← shared (existing)
  foo/
    SKILL.md                ← preprocessor → SkillLoader (existing)
    first-use.md            ← DELETE (content moves to foo-first-use/SKILL.md)
  foo-first-use/
    SKILL.md                ← THE content (single source of truth)
```

**`foo-first-use/SKILL.md` structure:**
```markdown
---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<skill name="foo">
[Instructions — how to use this skill]
</skill>

<output name="foo">
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-foo-output"`
</output>
```

**Loading paths:**

| Path | Trigger | Behavior |
|------|---------|----------|
| Main agent → `cat:foo` → SkillLoader | Skill tool invocation | First use: reads `foo-first-use/SKILL.md`, returns `<skill>` body + executes `<output>` preprocessor. Subsequent: replaces `<skill>` body with reference, re-executes `<output>` preprocessor. |
| Subagent `skills:` → `cat:foo-first-use` | Agent frontmatter | Claude Code loads SKILL.md directly. Standard `${VAR}` substitution + preprocessor execution. Always full content, no SkillLoader, no marker files. |

**SkillLoader changes:**
1. Read from `skills/{name}-first-use/SKILL.md` instead of `skills/{name}/first-use.md`
2. Strip YAML frontmatter from the loaded content
3. Parse `<skill>` and `<output>` XML-like tags
4. First use: return `<skill>` body as-is + execute `<output>` preprocessor directive
5. Subsequent: return reference text + process `<output>` preprocessor directives

**Visibility:** `-first-use` skills use `user-invocable: false` to hide from the `/` command menu. They remain visible
in the model's available skills list (unavoidable — Claude Code uses the same `mw()` function for both model listing
and subagent frontmatter lookup). The description "Do not invoke directly" discourages model invocation.

### Scope limitation
Only 3 skills currently have preprocessor directives in first-use.md: `status`, `run-retrospective`, `statusline`.
None are currently referenced in subagent `skills:` frontmatter. Phase 2 converts these 3 skills to the new pattern
as proof-of-concept, enabling future subagent preloading without additional refactoring.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Changes SkillLoader's file lookup and parsing; new XML tag parsing; deletes first-use.md files
- **Mitigation:** Existing tests updated; new tests for tag parsing; backward-compatible for skills without
  `-first-use` companion (falls back to first-use.md if no companion exists)

## Files to Modify

### Phase 1 (done)
- `client/.../SkillLoader.java` — `<skill-output>` tag wrapping
- `client/.../ClearSkillMarkers.java` — Session-specific marker deletion
- `client/.../GetPreCompactOutput.java` — New PreCompact hook handler
- `client/.../SkillLoaderTest.java` — Updated assertions
- `client/.../GetSessionStartOutputTest.java` — Updated assertions
- `plugin/hooks/hooks.json` — PreCompact hook registration
- `plugin/hooks/pre-compact.sh` — PreCompact hook script
- `plugin/skills/status/first-use.md` — `<skill-output>` tag reference
- `plugin/skills/run-retrospective/first-use.md` — `<skill-output>` tag reference
- `plugin/skills/statusline/first-use.md` — `<skill-output>` tag reference
- `plugin/concepts/skill-loading.md` — New concept doc
- `CLAUDE.md` — Cross-reference to skill-loading.md

### Phase 2 (new)
- `client/.../SkillLoader.java` — Read from `-first-use/SKILL.md`, strip frontmatter, parse `<skill>`/`<output>` tags
- `client/.../SkillLoaderTest.java` — New tests for tag parsing and `-first-use` loading
- `plugin/skills/status-first-use/SKILL.md` — New (content from status/first-use.md)
- `plugin/skills/run-retrospective-first-use/SKILL.md` — New (content from run-retrospective/first-use.md)
- `plugin/skills/statusline-first-use/SKILL.md` — New (content from statusline/first-use.md)
- `plugin/skills/status/first-use.md` — DELETE
- `plugin/skills/run-retrospective/first-use.md` — DELETE
- `plugin/skills/statusline/first-use.md` — DELETE
- `plugin/concepts/skill-loading.md` — Update with `-first-use` pattern documentation

## Acceptance Criteria

### Phase 1 (done)
- [x] `invokeSkillOutput` wraps successful output in `<skill-output>` tags
- [x] Error messages from `invokeSkillOutput` are NOT wrapped in tags
- [x] All 3 skill first-use.md files updated to reference `<skill-output>` tag
- [x] ClearSkillMarkers deletes only current session's marker file
- [x] ClearSkillMarkers has `@see SkillLoader` and SkillLoader has `@see ClearSkillMarkers`
- [x] PreCompact hook registered in hooks.json
- [x] skill-loading.md concept doc created
- [x] CLAUDE.md cross-reference added

### Phase 2 (new)
- [ ] SkillLoader reads from `{name}-first-use/SKILL.md` when it exists (falls back to `first-use.md`)
- [ ] SkillLoader strips YAML frontmatter from `-first-use` content
- [ ] SkillLoader parses `<skill>` and `<output>` tags
- [ ] First use: returns `<skill>` body + executed `<output>` preprocessor
- [ ] Subsequent use: returns reference text + executed `<output>` preprocessor
- [ ] 3 `-first-use/SKILL.md` files created with correct frontmatter
- [ ] 3 old `first-use.md` files deleted
- [ ] skill-loading.md updated with `-first-use` pattern
- [ ] All tests pass (`mvn -f client/pom.xml verify`)

## Execution Steps

### Phase 1 (done — commits 079c4e2a, c117a662, 98110a6f)
1. ~~Wrap invokeSkillOutput return value in `<skill-output>` tags~~
2. ~~Add cross-reference Javadoc between SkillLoader and ClearSkillMarkers~~
3. ~~Update ClearSkillMarkers to session-specific deletion~~
4. ~~Register PreCompact hook~~
5. ~~Update tests~~
6. ~~Update 3 skill first-use.md files~~
7. ~~Create skill-loading.md concept doc~~
8. ~~Add CLAUDE.md cross-reference~~

### Phase 2 (new)
1. **Modify SkillLoader.loadContent()** to check for `skills/{name}-first-use/SKILL.md` first, fall back to
   `skills/{name}/first-use.md`. When loading from `-first-use`, strip YAML frontmatter.
2. **Add tag parsing to SkillLoader** — new method `parseSkillTags(String content)` that extracts `<skill>` and
   `<output>` tag bodies. Returns a record with `skillBody` and `outputBody` (nullable).
3. **Modify SkillLoader.load()** — when content has `<skill>`/`<output>` tags:
   - First use: return `<skill>` body as-is + process `<output>` preprocessor directives
   - Subsequent: return reference text + process `<output>` preprocessor directives
4. **Create `plugin/skills/status-first-use/SKILL.md`** — move content from `status/first-use.md`, add frontmatter
   (`user-invocable: false`), wrap instructions in `<skill>` tag, wrap preprocessor output in `<output>` tag
5. **Create `plugin/skills/run-retrospective-first-use/SKILL.md`** — same pattern
6. **Create `plugin/skills/statusline-first-use/SKILL.md`** — same pattern
7. **Delete old first-use.md files** for status, run-retrospective, statusline
8. **Update SkillLoaderTest.java** — add tests for `-first-use` loading, frontmatter stripping, tag parsing,
   first-use vs subsequent behavior with tags
9. **Update skill-loading.md** — document the `-first-use` pattern
10. **Run `mvn -f client/pom.xml verify`** to confirm all tests pass
