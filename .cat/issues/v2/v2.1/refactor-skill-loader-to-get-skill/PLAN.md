# Plan

## Goal

Rename `SkillLoader` to `GetSkill` and simplify it. On first load, return `first-use.md` verbatim
(with preprocessor expansion). On subsequent loads, return a short "already loaded" reference and
re-execute the single `!` preprocessor directive from `first-use.md` (if present) to produce fresh
output. Remove all output tag parsing, inline backtick sanitization, and dynamic string building
from the class. Java output handlers (e.g., `GetAddOutput`) are responsible for returning
`<output skill="X">` tags themselves; the skill file uses bare `!` preprocessor directives with no
`<output>` wrapper.

Additionally, create a `GetFile` Java class and `get-file` binary that serves as a Read tool replacement
with session-level deduplication. On first load, `get-file` returns the raw file content. On subsequent
loads within the same session, it returns a short reference message ("see your earlier Read result for
path.md"). Skill files replace inline file references (e.g., `[description](path.md)` in
`<execution_context>`) with `!` preprocessor directives to `get-file`, so reference files are
automatically deduplicated across skills. `GetFile` does NOT process `!` preprocessor directives in the
returned content — it returns raw file content, like Read.

## Problem

`SkillLoader` mixes two concerns: (1) loading skill instructions (first-use vs. already-loaded) and
(2) parsing `<output>` tags and dynamically assembling the `<instructions>` wrapper + execute-ref +
output body. This dynamic logic is complex (~400 lines), hard to test, and couples skill loading to
output tag conventions. The `<output>` tag convention is better owned by Java output handlers that
return the tags themselves.

Additionally, skill files reference external files via `<execution_context>` with markdown links like
`[description](${CLAUDE_PLUGIN_ROOT}/path/file.md)`. Claude loads these via the Read tool, which has
no session-level deduplication — the same file is re-read in full every time any skill references it.
A `GetFile` command with session-level tracking would return full content on first load and a short
reference on subsequent loads, saving significant context tokens when multiple skills share reference
files.

## Research Findings

**Current flow:**
1. `processContent()` runs preprocessor directives, expanding `!` commands to their stdout
2. `parseContent()` scans expanded content for `<output skill="X">...</output>` tags
3. If output tags found: wrap everything before as `<instructions skill="X">`, add execute-ref, append
   output body
4. If no output tags: return full content on first use, "already loaded" message on subsequent uses

**Skills currently using `<output>` tags in their SKILL.md files (before this issue):**
- `plugin/skills/load-skill/first-use.md` — has `<output>` wrapper around `!` directive
- `plugin/skills/skill-builder-agent/first-use.md` — has `<output>` wrapper around `!` directive
- `plugin/skills/add/first-use.md` — was changed to bare `!` directive in `implement-add-skill-handler-data`

**New flow (post-refactor):**
1. On first load: return `first-use.md` verbatim (preprocessor directives expand as usual)
2. On subsequent loads: generate a short "already loaded, use the Skill tool to invoke" reference message,
   then re-run the single preprocessor command from `first-use.md` to produce fresh output
3. Java output handlers return `<output skill="X">...</output>` tags in their stdout; SkillLoader
   (now GetSkill) processes the directives but does not parse `<output>` tags at all

**Constraint on first-use.md:** Each `first-use.md` must contain at most ONE `!` preprocessor directive.
GetSkill extracts and re-runs this single directive on subsequent loads. If a skill has no preprocessor
directive, subsequent loads return only the "already loaded" reference message. If a skill has more than
one preprocessor directive, GetSkill fails with a validation error.

**Methods to remove from SkillLoader:**
- `parseContent()` (line 478) — splits content on `<output>` tags
- `sanitizeInlineBackticks()` (line 445) — escapes backticks inside `<output>` blocks
- `findCodeBlockRegions()` (line 507) — helper for sanitize
- `isInsideCodeBlock()` (line 523) — helper for sanitize
- `processContent()` (line 316) — current top-level dispatcher (replace with simpler file dispatch)
- `ParsedContent` record (line 127) — holds instructions/outputBody split result

**Patterns to remove:**
- `OUTPUT_TAG_PATTERN` (line 98)
- `INLINE_BACKTICK_PATTERN` (line 100)
- `CODE_BLOCK_PATTERN` (line 103)

**Skills with preprocessor directives (dynamic output on every invocation):**

Skills that have a `!` preprocessor directive in `first-use.md` get dynamic output on every invocation:
- First load: full `first-use.md` content (instructions + preprocessor output)
- Subsequent loads: "already loaded" reference + re-executed preprocessor command output

Skills that have a `!` preprocessor directive currently wrapped in `<output>` tags need the wrapper
removed (bare `!` directive):
- `plugin/skills/load-skill/first-use.md` — remove `<output>` wrapper
- `plugin/skills/skill-builder-agent/first-use.md` — remove `<output>` wrapper

Skills without preprocessor directives (pure instruction skills) get the simple behavior:
- First load: full `first-use.md` content
- Subsequent loads: "already loaded" reference message only

**GetFile design:**
- Java class: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetFile.java`
- Binary: `get-file` (registered in `build-jlink.sh`)
- Session tracking: marker files under `{sessionDir}/files-loaded/` (hashed path as filename)
- First load: reads file at given path, writes marker, returns raw content
- Subsequent load: detects marker exists, returns `"see your earlier Read result for {filename}"`
- No preprocessor processing of returned content — raw file content only
- Invoked via `!` preprocessor directives in skill files:
  `!`get-file "${CLAUDE_PLUGIN_ROOT}/concepts/work.md"``

**Skills with `<execution_context>` file references to convert:**
- All skills that use `[description](${CLAUDE_PLUGIN_ROOT}/path/file.md)` inside `<execution_context>`
  should replace those references with `!`get-file "${CLAUDE_PLUGIN_ROOT}/path/file.md"`` preprocessor
  directives

**Files containing `SkillLoader` references to update:**
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` → rename to `GetSkill.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` → rename to `GetSkillTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestSkillOutputWithTag.java` → delete or update
- `client/build-jlink.sh` line 83: `"skill-loader:util.SkillLoader"` → `"get-skill:util.GetSkill"`
- `client/src/main/java/io/github/cowwoc/cat/hooks/AotTraining.java` — no direct reference (uses
  `SkillLoader` indirectly via hooks)
- Any Java files importing `SkillLoader` — update imports to `GetSkill`
- `plugin/concepts/skill-loading.md` — update references to `skill-loader` binary and `SkillLoader` class

## Execution Steps

1. **Rename and simplify `SkillLoader.java` → `GetSkill.java`**
   - Create `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
   - Keep `getOutput(String[] args)` as the main entry point (same signature as before)
   - Keep the first-use marker file logic (check `{sessionDir}/skills-loaded/{skillName}`)
   - Replace `processContent()` with simple dispatch:
     - On first load: read and return `first-use.md` (run preprocessor on it)
     - On subsequent loads: generate "already loaded" reference message, then scan `first-use.md` for
       a single `!` preprocessor directive — if found, re-execute it and append the output; if not
       found, return just the reference message
   - Add validation: if `first-use.md` contains more than one `!` preprocessor directive, fail with
     an error (enforces the single-directive constraint)
   - Remove: `parseContent()`, `sanitizeInlineBackticks()`, `findCodeBlockRegions()`,
     `isInsideCodeBlock()`, `OUTPUT_TAG_PATTERN`, `INLINE_BACKTICK_PATTERN`, `CODE_BLOCK_PATTERN`,
     `ParsedContent` record
   - Delete `SkillLoader.java`

2. **Rename test file and update test content**
   - Rename `SkillLoaderTest.java` → `GetSkillTest.java`
   - Update all `SkillLoader` references to `GetSkill`
   - Remove or update tests that test `parseContent()` or output tag parsing (those behaviors are gone)
   - Delete `TestSkillOutputWithTag.java` (tests the removed `<output>` tag parsing behavior)

3. **Update `build-jlink.sh`**
   - Change `"skill-loader:util.SkillLoader"` → `"get-skill:util.GetSkill"`

4. **Update `AotTraining.java`**
   - No `SkillLoader` reference exists; verify no update needed
   - Check all files importing `SkillLoader` and update to `GetSkill`

5. **Update skills that had `<output>` wrappers to bare `!` directives**
   - Update `plugin/skills/load-skill/first-use.md` — remove `<output>` wrapper, use bare `!` directive
   - Update `plugin/skills/skill-builder-agent/first-use.md` — remove `<output>` wrapper, use bare `!`
     directive
   - No `subsequent-use.md` files needed — GetSkill dynamically re-runs the preprocessor command

6. **Create `GetFile.java` and register `get-file` binary**
   - Create `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetFile.java`
   - Implement `getOutput(String[] args)` — takes a file path argument
   - Track loaded files via marker files under `{sessionDir}/files-loaded/` (use URL-encoded path as
     marker filename to avoid collisions)
   - First load: read file content, create marker, return raw content
   - Subsequent load: detect marker, return `"see your earlier Read result for {filename}"`
   - Add `"get-file:util.GetFile"` to `build-jlink.sh` HANDLERS array
   - Add `referenceClass(GetFile.class)` to `AotTraining.java`

7. **Create `GetFileTest.java`**
   - Test first load returns full file content
   - Test subsequent load returns short reference message
   - Test with different file paths to verify marker isolation
   - Test with non-existent file returns appropriate error

8. **Convert all file references in skill files to `get-file` preprocessor directives**
   - Scan all skill files (`first-use.md`, `SKILL.md`) for file references in any form:
     - `<execution_context>` blocks with `[description](path)` markdown links
     - `<conditional_context>` blocks with `[description](path)` markdown links
     - Any other inline `[description](${CLAUDE_PLUGIN_ROOT}/path)` references used for file loading
   - Replace each file reference with `!`get-file "path"`` preprocessor directive
   - Remove `<execution_context>` blocks that become empty after conversion (keep blocks that still
     contain non-file-reference content)
   - Preserve `<conditional_context>` structure but replace the file references inside with `get-file`
     directives

9. **Update `skill-loading.md`**
   - Replace references to `skill-loader` binary with `get-skill`
   - Replace references to `SkillLoader` class with `GetSkill`
   - Document `GetFile` / `get-file` as the file-loading counterpart to `GetSkill`
   - Update the "Output Tags in Preprocessor Directives" section if needed to reflect new design

10. **Run all tests and verify build**
    - `mvn -f client/pom.xml test` — all tests must pass
    - Build jlink image: `mvn -f client/pom.xml verify`

11. **Update STATE.md to closed**

## Post-conditions

- `GetSkill.java` exists; `SkillLoader.java` is deleted
- `GetSkill.java` contains no `parseContent`, `sanitizeInlineBackticks`, `OUTPUT_TAG_PATTERN`,
  `INLINE_BACKTICK_PATTERN`, `CODE_BLOCK_PATTERN`, or `ParsedContent` references
- `build-jlink.sh` references `get-skill:util.GetSkill` (not `skill-loader:util.SkillLoader`)
- `GetSkillTest.java` exists; `SkillLoaderTest.java` is deleted
- `first-use.md` for `load-skill` and `skill-builder-agent` uses bare `!` directives (no `<output>` wrapper)
- No `subsequent-use.md` files exist (dynamic re-execution replaces static subsequent files)
- GetSkill validates that each `first-use.md` contains at most one `!` preprocessor directive
- `mvn -f client/pom.xml test` passes with all tests green
- E2E: `/cat:load-skill cat:add-agent` returns fresh planning data on both first and subsequent invocations
  (verifies preprocessor command is re-executed on subsequent load)
- `GetFile.java` exists with `getOutput(String[] args)` method
- `build-jlink.sh` contains `"get-file:util.GetFile"` entry
- `get-file` binary returns full file content on first load and short reference on subsequent loads
- `GetFileTest.java` exists with tests for first-load, subsequent-load, and error cases
- No `<execution_context>` blocks contain `[description](path)` file references that could be replaced
  by `get-file` directives (all converted)
