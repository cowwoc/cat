# Plan: rename-script-output-to-skill-output

## Goal
Rename all `SCRIPT OUTPUT` text markers and prose references to `SKILL OUTPUT` across the codebase, aligning
the user-facing terminology with the Java code which already uses `SkillOutput`, `GetSkillOutput`, and
`CAT_SKILL_OUTPUT`.

## Satisfies
None - naming consistency/refactoring

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** High file count but purely mechanical text replacement; markers must match exactly between
  handlers (which produce them) and skill files (which reference them)
- **Mitigation:** The Java handler layer already uses `SkillOutput`/`CAT_SKILL_OUTPUT` — only the markdown
  skill files and bash scripts still use `SCRIPT OUTPUT`. Grep before and after to verify zero remaining
  references.

## Scope

### Category 1: Skill first-use.md markers (~15 files)
Replace `SCRIPT OUTPUT X` with `SKILL OUTPUT X` in marker references and fail-fast checks.

Files:
- `plugin/skills/status/first-use.md` — `SCRIPT OUTPUT STATUS DISPLAY`
- `plugin/skills/help/first-use.md` — `SCRIPT OUTPUT HELP DISPLAY`
- `plugin/skills/token-report/first-use.md` — `SCRIPT OUTPUT TOKEN REPORT`
- `plugin/skills/render-diff/first-use.md` — `SCRIPT OUTPUT RENDER DIFF`
- `plugin/skills/work/first-use.md` — `SCRIPT OUTPUT PROGRESS BANNERS`, `SCRIPT OUTPUT WORK BOXES`
- `plugin/skills/init/first-use.md` — `SCRIPT OUTPUT INIT BOXES`
- `plugin/skills/config/first-use.md` — `SCRIPT OUTPUT CONFIG BOXES`
- `plugin/skills/cleanup/first-use.md` — `SCRIPT OUTPUT SURVEY DISPLAY`
- `plugin/skills/stakeholder-review/first-use.md` — `SCRIPT OUTPUT STAKEHOLDER BOXES`
- `plugin/skills/delegate/first-use.md` — `SCRIPT OUTPUT DELEGATE PROGRESS`
- `plugin/skills/monitor-subagents/first-use.md` — `SCRIPT OUTPUT MONITOR SUBAGENTS`
- `plugin/skills/run-retrospective/first-use.md` — `SCRIPT OUTPUT RETROSPECTIVE`
- `plugin/skills/statusline/first-use.md` — `SCRIPT OUTPUT STATUSLINE CHECK`
- `plugin/skills/skill-builder/first-use.md` — `SCRIPT OUTPUT HELP DISPLAY` and prose references
- `plugin/skills/write-and-commit/first-use.md` — prose reference

### Category 2: Plugin scripts (~8 files)
Replace `Script Output` / `script output` in headers and comments.

Files:
- `plugin/scripts/get-work-boxes.py` — `## Script Output: Work Boxes` header
- `plugin/scripts/get-cleanup-survey.sh` — `## Script Output: Cleanup Survey` header
- `plugin/scripts/get-work-boxes.sh` — comment
- `plugin/scripts/get-progress-banner.sh` — comment
- `plugin/scripts/get-render-diff.sh` — comment
- `plugin/scripts/get-token-report.sh` — comment
- `plugin/scripts/get-help-display.sh` — comment
- `plugin/scripts/get-config-display.sh` — comment

### Category 3: Plugin docs (~3 files)
- `plugin/hooks/README.md` — example marker and prose
- `plugin/concepts/error-handling.md` — section header
- `plugin/concepts/subagent-delegation.md` — table cell

### Category 4: Java source (~5 files, comments/prose only)
- `hooks/src/main/java/.../GetWorkOutput.java` — Javadoc comment
- `hooks/src/main/java/.../GetResearchOutput.java` — string literal "script output circle patterns"
- `hooks/src/main/java/.../InjectSessionInstructions.java` — "script output" in session text
- `hooks/src/main/java/.../IssueLock.java` — Javadoc "bash script output"
- `hooks/src/main/java/.../ExistingWorkChecker.java` — Javadoc "bash script output"
- `hooks/src/main/java/.../ComputeBoxLines.java` — "Script output box" string literal

### Category 5: Java tests (~2 files, comments only)
- `hooks/src/test/java/.../HandlerOutputTest.java` — comments referencing `SCRIPT OUTPUT`
- `hooks/src/test/java/.../GetCleanupOutputTest.java` — comments referencing `SCRIPT OUTPUT`

### Category 6: Other
- `plugin/skills/learn/phase-prevent.md` — table cell
- `plugin/skills/learn/phase-analyze.md` — example string
- `plugin/skills/learn/RELATED-FILES-CHECK.md` — references
- `plugin/skills/skill-builder/workflow-output.md` — prose
- `.claude/skills/hide-output/SKILL.md` — references

### Out of scope
- Worktree copies (`.claude/cat/worktrees/`) — will inherit changes on rebase
- Closed issue PLANs — historical records
- `MEMORY.md` — session-specific, updated separately
- Java class/interface names (`SkillOutput`, `GetSkillOutput`) — already correct

## Acceptance Criteria
- [ ] Zero grep matches for `SCRIPT OUTPUT` in `plugin/` (excluding worktrees)
- [ ] Zero grep matches for `SCRIPT OUTPUT` in `hooks/src/`
- [ ] Zero grep matches for `script output` (case-insensitive) in `plugin/` except where it refers to
  bash script stdout (e.g., "bash script output" in IssueLock/ExistingWorkChecker is acceptable as
  "the output of a bash script", not a CAT concept)
- [ ] Within each first-use.md, marker text and fail-fast references use the same renamed string
  (e.g., both say `SKILL OUTPUT STATUS DISPLAY`, not a mix of old and new)
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)

## Execution Steps

1. **Step 1:** Rename markers in all skill first-use.md files (Category 1)
   - Replace `SCRIPT OUTPUT` with `SKILL OUTPUT` in all marker strings and fail-fast messages

2. **Step 2:** Rename headers/comments in plugin scripts (Category 2)
   - Replace `## Script Output:` with `## Skill Output:` in output headers
   - Replace `(script output)` with `(skill output)` in comments

3. **Step 3:** Update plugin docs (Category 3)
   - Update README.md example, error-handling.md section header, subagent-delegation.md table

4. **Step 4:** Update Java source comments and strings (Category 4)
   - Update Javadoc, string literals referencing "script output" as a CAT concept
   - Keep "bash script output" in IssueLock/ExistingWorkChecker (refers to bash stdout, not CAT concept)

5. **Step 5:** Update Java test comments (Category 5)

6. **Step 6:** Update remaining files (Category 6)
   - Learn skill files, skill-builder docs, hide-output SKILL.md

7. **Step 7:** Run tests
   - `mvn -f hooks/pom.xml test`

8. **Step 8:** Verify with grep
   - Confirm zero remaining `SCRIPT OUTPUT` references in plugin/ and hooks/src/

## Success Criteria
- [ ] `grep -ri "SCRIPT OUTPUT" plugin/` returns no matches (excluding worktrees)
- [ ] `grep -ri "SCRIPT OUTPUT" hooks/src/` returns no matches
- [ ] `mvn -f hooks/pom.xml test` passes
- [ ] Verbatim skills (/cat:status, /cat:help, /cat:token-report, /cat:render-diff) produce correct output
