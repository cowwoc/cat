# Rename hooks to engine and SCRIPT OUTPUT to SKILL OUTPUT

## Problem

The `/workspace/hooks` directory contains a full Maven Java project (handlers, skill loaders, session management,
token counting, licensing, utilities) — not just hook scripts. The name "hooks" is misleading. Similarly, the
"SCRIPT OUTPUT" protocol markers are always used for skill handler output, not scripts.

## Goal

1. Rename `/workspace/hooks` → `/workspace/engine`
2. Rename all "SCRIPT OUTPUT" markers → "SKILL OUTPUT"

**Note:** Java packages remain `io.github.cowwoc.cat.hooks` for now. A future task will split them into
domain-specific packages (`io.github.cowwoc.cat.skill`, `io.github.cowwoc.cat.git`, etc.).

## Satisfies

None — infrastructure refactoring.

## Scope

### In Scope

- Directory rename (`git mv hooks engine`)
- Build references in CLAUDE.md, java.md conventions, cat-update-hooks skill
- `"hooks/"` string literals in Java path allowlists → `"engine/"`
- JAR name references (`cat-hooks-2.1.jar` → `cat-engine-2.1.jar`) in build scripts
- "SCRIPT OUTPUT" → "SKILL OUTPUT" in all active skill .md files, Java handlers, and tests
- Build script references (build.sh, build-jlink.sh)

### Out of Scope

- `plugin/hooks/` directory (this contains actual hook scripts — name is correct)
- Java package names (`io.github.cowwoc.cat.hooks.*` stays — future task will split into domain packages)
- Maven groupId/artifactId (stays `io.github.cowwoc.cat.hooks` / `cat-hooks` until package rename)
- Module names in module-info.java (stays `io.github.cowwoc.cat.hooks` until package rename)
- Completed issue PLAN.md files (historical records, leave as-is)
- The concept of "hooks" when referring to Claude Code hook events (PreToolUse, PostToolUse, etc.)
- Retrospective/mistake JSON files (historical records)

## Execution Steps

### Step 1: Rename directory

```bash
git mv /workspace/hooks /workspace/engine
```

### Step 2: Update build scripts

**`engine/build.sh`:**
- Update any path references from `hooks/` to `engine/`

**`engine/build-jlink.sh`:**
- Update any path references from `hooks/` to `engine/`

### Step 3: Update project configuration files

**`/workspace/CLAUDE.md`:**
- `mvn -f hooks/pom.xml test` → `mvn -f engine/pom.xml test`

**`/workspace/.claude/cat/conventions/java.md`:**
- All references to `hooks/` directory → `engine/`
- Project structure section (directory name only, packages stay as-is)

**`/workspace/.claude/skills/cat-update-hooks/SKILL.md`:**
- `mvn -f /workspace/hooks/pom.xml verify` → `mvn -f /workspace/engine/pom.xml verify`
- `hooks/target/jlink` → `engine/target/jlink`
- Consider renaming the skill itself to `cat-update-engine`

**`/workspace/.claude/rules/hooks.md`:**
- Review for any references to the hooks directory (vs plugin hooks)

### Step 4: Rename SCRIPT OUTPUT → SKILL OUTPUT

Search and replace across all active files:
- `SCRIPT OUTPUT` → `SKILL OUTPUT` (uppercase marker)
- `Script Output` → `Skill Output` (title case in headings)
- `script output` → `skill output` (lowercase in descriptions)

Files to update (non-exhaustive):
- `plugin/skills/*/first-use.md` — skill definitions with SCRIPT OUTPUT markers and fail-fast checks
- `plugin/skills/skill-builder/first-use.md` — template that teaches SCRIPT OUTPUT pattern
- `plugin/hooks/README.md` — documentation
- `plugin/scripts/*.sh` and `plugin/scripts/*.py` — output headers
- `engine/src/main/java/**/*.java` — handler output strings and comments
- `engine/src/test/java/**/*.java` — test assertions and comments
- `plugin/skills/work/phase-merge.md` — work phase references
- `plugin/skills/delegate/first-use.md` — delegate skill references
- `engine/src/main/java/**/session/InjectSessionInstructions.java` — session instructions listing

**Do NOT update:**
- Completed issue PLAN.md files in `.claude/cat/issues/`
- Retrospective JSON files
- The existing issue `rename-script-output-to-render-output` (historical, already completed or superseded)

### Step 5: Update Java string references

In Java handler classes, update string literals containing:
- `"hooks/"` in path allowlists (e.g., ValidateCommitType.java, WarnBaseBranchEdit.java) → `"engine/"`
- `"SCRIPT OUTPUT"` → `"SKILL OUTPUT"` in output generation

### Step 6: Build and test

```bash
mvn -f /workspace/engine/pom.xml verify
```

All tests must pass.

### Step 7: Update jlink installation

Run the updated build hooks skill to install the new jlink image.

### Step 8: Verify runtime

```bash
/home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/bin/java -version
```

Confirm the runtime still works.

### Step 9: Verify no stale references

```bash
grep -r "SCRIPT OUTPUT" /workspace/plugin/ /workspace/engine/
grep -r "mvn -f hooks/" /workspace/CLAUDE.md /workspace/.claude/
```

All should return 0 results (excluding git history and completed issue files).

## Acceptance Criteria

- [ ] `/workspace/engine` directory exists with full Maven project structure
- [ ] `/workspace/hooks` directory no longer exists
- [ ] Java packages remain `io.github.cowwoc.cat.hooks.*` (unchanged)
- [ ] `mvn -f engine/pom.xml verify` passes with all tests green
- [ ] No remaining `SCRIPT OUTPUT` markers in active source files
- [ ] All `SKILL OUTPUT` markers function identically (verbatim output protocol works)
- [ ] `plugin/hooks/` directory unchanged (still contains hook scripts)
- [ ] User-visible behavior unchanged
- [ ] jlink runtime image builds and installs successfully