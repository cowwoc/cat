# Plan: fix-agent-skill-directory-placement

## Current State

Eight files in `plugin/agents/` subdirectories use `SKILL.md` format but are invoked in two
incompatible ways. Four are invoked as persistent subagents via the Agent tool
(`subagent_type: "cat:name"`), and four are invoked as one-shot skills via the Skill tool
(`skill="cat:name"`). Both groups are stored as `plugin/agents/{name}/SKILL.md`, which is the
wrong format and location for both.

## Target State

- **Group 1 — Subagents:** Converted to top-level `.md` files in `plugin/agents/` with `name:`
  frontmatter field, matching the format of existing agents like `work-execute.md`.
- **Group 2 — Skills:** Moved to `plugin/skills/{name}/SKILL.md`, matching the format of all other
  skills.
- No subdirectories remain in `plugin/agents/` — all agent configs are top-level `.md` files.

## Parent Requirements
None

## Approach Analysis

### A: Rename + frontmatter patch (chosen)
- **Risk:** LOW
- **Scope:** 9 files (8 content files + 1 README)
- Move Group 2 skills to `plugin/skills/` unchanged.
- Move Group 1 SKILL.md files to top-level `.md` in `plugin/agents/`, removing `user-invocable:`
  and adding `name:` to frontmatter. Body content is preserved verbatim — it already reads as
  agent instructions and is passed as the system prompt when the Agent tool invokes the subagent.

### B: Rewrite bodies for agent format (rejected)
- **Risk:** MEDIUM
- **Scope:** 13+ files
- Rewrite the procedural "## Procedure / Step N" bodies to persona-style ("You are a...").
- **Rejected:** The procedural format is functionally correct when injected as a subagent's system
  prompt. Rewriting risks introducing regressions in agent behavior. The procedural format is
  already used in other agents (e.g., `plan-review-agent.md`).

### C: Keep in plugin/agents/ as SKILL.md (rejected)
- **Risk:** HIGH
- **Scope:** 0 files changed
- **Rejected:** Conflates two invocation mechanisms. Skills invoked via the Skill tool must be in
  `plugin/skills/` for the skill loader to find them. Agents invoked via `subagent_type:` must be
  top-level `.md` files for the agent system to resolve them. Leaving files in subdirectories
  under `plugin/agents/` breaks this resolution for all 8 entries.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — invocation names stay the same (`cat:name`); only file paths change
- **Mitigation:** After each group's moves, verify the git tree shows correct file locations

## Files to Modify

**Group 1 — Convert to top-level agent configs:**
- `plugin/agents/red-team-agent/SKILL.md` → `plugin/agents/red-team-agent.md`
  - Add `name: red-team-agent` to frontmatter
  - Remove `user-invocable: false` from frontmatter
  - Body content unchanged
- `plugin/agents/blue-team-agent/SKILL.md` → `plugin/agents/blue-team-agent.md`
  - Add `name: blue-team-agent` to frontmatter
  - Remove `user-invocable: false` from frontmatter
  - Body content unchanged
- `plugin/agents/diff-validation-agent/SKILL.md` → `plugin/agents/diff-validation-agent.md`
  - Add `name: diff-validation-agent` to frontmatter
  - Remove `user-invocable: false` from frontmatter
  - Body content unchanged
- `plugin/agents/skill-analyzer-agent/SKILL.md` → `plugin/agents/skill-analyzer-agent.md`
  - Add `name: skill-analyzer-agent` to frontmatter
  - Remove `user-invocable: false` from frontmatter
  - Body content unchanged
- `plugin/skills/skill-grader-agent/` → `plugin/agents/skill-grader-agent.md`
  - Merge SKILL.md frontmatter (description, model) with first-use.md body into flat agent format
  - Add `name: skill-grader-agent` to frontmatter
  - Body content from first-use.md (license header excluded)

**Group 2 — Move to plugin/skills/:**
- `plugin/agents/skill-validator-agent/SKILL.md` → `plugin/skills/skill-validator-agent/SKILL.md`
  - File content unchanged
- `plugin/agents/description-tester-agent/SKILL.md` → `plugin/skills/description-tester-agent/SKILL.md`
  - File content unchanged
- `plugin/agents/skill-comparison-agent/SKILL.md` → `plugin/skills/skill-comparison-agent/SKILL.md`
  - File content unchanged

**Documentation:**
- `plugin/agents/README.md` — update the `## Directory Structure` table to list the 4 new
  top-level agent files and remove subdirectory entries

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1 — Group 1: Convert SKILL.md to top-level agent configs
- For each of the 4 subagent files (red-team-agent, blue-team-agent, diff-validation-agent,
  skill-analyzer-agent):
  - Read `plugin/agents/{name}/SKILL.md`
  - Write content to `plugin/agents/{name}.md` with frontmatter in this exact field order:
    1. `name: {name}` (e.g. `name: red-team-agent`)
    2. `description: >` (multi-line, preserved verbatim from source)
    3. `model: {value}` (preserved verbatim from source)
    - Omit `user-invocable: false` — do NOT include it in the output
  - Body content (everything after the closing `---`) is copied verbatim, unchanged
  - Delete the source file and the now-empty subdirectory: `git rm -r plugin/agents/{name}/`
  - Stage the new file: `git add plugin/agents/{name}.md`
  - Files: `plugin/agents/{name}/SKILL.md` → `plugin/agents/{name}.md`
- Commit: `refactor: convert subagent SKILL.md files to top-level agent configs`

### Wave 2 — Group 2: Move skills to plugin/skills/
- For each of the 3 skill files (skill-validator-agent, description-tester-agent,
  skill-comparison-agent):
  - `mkdir -p plugin/skills/{name}/`
  - `git mv plugin/agents/{name}/SKILL.md plugin/skills/{name}/SKILL.md`
  - If the source directory is now empty, remove it: `git rm -r plugin/agents/{name}/`
  - File content is unchanged; only the path changes
  - Files: `plugin/agents/{name}/SKILL.md` → `plugin/skills/{name}/SKILL.md`
- Commit: `refactor: move skill-invoked agents from plugin/agents/ to plugin/skills/`

### Wave 3 — Update README and STATE.md
- Update `plugin/agents/README.md` `## Directory Structure` fenced code block:
  - Remove all 8 subdirectory entries (e.g. `├── red-team-agent/` lines and their children)
  - Add the following 4 lines in alphabetical order among the existing top-level `.md` entries:
    ```
    ├── blue-team-agent.md             # Internal subagent — closes loopholes identified by the red-team
    ├── diff-validation-agent.md       # Internal subagent — verifies blue-team patches address red-team findings
    ├── red-team-agent.md              # Internal subagent — adversarially probes a target for loopholes
    └── skill-analyzer-agent.md        # Internal subagent — surfaces patterns from benchmark JSON
    ```
  - The 4 Group 2 files (`skill-validator-agent`, `description-tester-agent`, `skill-comparison-agent`,
    `skill-grader-agent`) are now in `plugin/skills/`; do NOT list them in `plugin/agents/README.md`
- Update `STATE.md`: set Status to closed, Progress to 100%
- Commit: `refactor: update plugin/agents/README.md directory structure table`

## Post-conditions
- [ ] `plugin/agents/red-team-agent.md` exists with `name: red-team-agent` in frontmatter
- [ ] `plugin/agents/blue-team-agent.md` exists with `name: blue-team-agent` in frontmatter
- [ ] `plugin/agents/diff-validation-agent.md` exists with `name: diff-validation-agent` in frontmatter
- [ ] `plugin/agents/skill-analyzer-agent.md` exists with `name: skill-analyzer-agent` in frontmatter
- [ ] `plugin/skills/skill-validator-agent/SKILL.md` exists
- [ ] `plugin/skills/description-tester-agent/SKILL.md` exists
- [ ] `plugin/skills/skill-comparison-agent/SKILL.md` exists
- [ ] `plugin/agents/skill-grader-agent.md` exists with `name: skill-grader-agent` in frontmatter
- [ ] `plugin/agents/` contains no subdirectories (only top-level `.md` files and `README.md`)
- [ ] `plugin/agents/README.md` directory structure table reflects the 4 new top-level agent files
- [ ] `git status` is clean (no untracked or modified files)
