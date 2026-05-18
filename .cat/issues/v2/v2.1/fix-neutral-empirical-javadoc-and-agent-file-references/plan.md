# Plan: fix-neutral-empirical-javadoc-and-agent-file-references

## Goal
Apply the Claude/Codex engine terminology and runner-surface redesign in the issue worktree while keeping shared
content engine-neutral and preserving existing behavioral contracts.

## Scope
1. Terminology and boundaries
- Replace Claude/Codex split wording from `runtime-specific/runtime-neutral` to `engine-specific/engine-neutral`
  where the text describes engine wrappers versus shared bodies.
- Keep true platform/runtime terms unchanged where they refer to JVM/runtime artifacts, environment variables, or
  build/runtime directories (for example `CAT_RUNTIME`, jlink runtime artifacts).

2. Skill surface redesign
- Rename `cat:empirical-test` usage to `cat:spawn-engine` for one-off empirical checks.
- Keep engine-specific wrappers for `spawn-engine` under:
  - `client/plugin/skills/claude/spawn-engine/`
  - `client/plugin/skills/codex/spawn-engine/`
- Add a shared engine-neutral body:
  - `client/plugin/skills/common/spawn-engine/first-use.md`
- Ensure engine-specific wrappers include the shared body and retain engine-specific frontmatter/commands.

3. SPRT runner surface alignment
- Rename instruction-test runner references from `instruction-test-runner` to `sprt-runner` in skill/concept docs,
  test fixtures, and launcher wiring.
- Rename Java class references from `InstructionTestRunner` to `SprtRunner` where applicable.
- Keep formal SPRT flows on `cat:sprt-runner` and one-off empirical execution on `cat:spawn-engine`.

4. Documentation and test fixture coherence
- Update agent/skill/rule/concept documentation to reflect the new engine terminology and renamed skill surfaces.
- Update test assets and fixture paths that reference renamed skills/files to ensure references resolve to existing
  files.
- Rename skill test directories:
  - `client/plugin/tests/skills/spawn-engine-claude` -> `client/plugin/tests/skills/spawn-claude`
  - `client/plugin/tests/skills/spawn-engine-codex` -> `client/plugin/tests/skills/spawn-codex`

5. History/worktree hygiene
- Keep these changes on the issue branch/worktree and not on `v2.1` directly.
- Squash commits by topic before approval.

## Out of Scope
- Replacing JVM/runtime artifact terminology where it refers to actual execution/runtime packaging rather than
  Claude/Codex engine wrappers.
- Behavioral redesign of SPRT math, grading logic, or decision thresholds.
- Introducing new agent orchestration mechanisms beyond the `spawn-engine` and `sprt-runner` surface changes.

## Post-Conditions
- Issue branch contains the engine terminology and runner-surface changes; `v2.1` does not carry those direct edits.
- `cat:spawn-engine` exists with:
  - engine-specific wrapper files in Claude and Codex skill trees
  - a shared engine-neutral first-use body included by both wrappers
- References to legacy `instruction-test-runner`/`InstructionTestRunner` naming are updated to `sprt-runner`/
  `SprtRunner` where this issue touched the surface.
- Renamed skill/file references in docs/tests point to files that exist after the rename set.
- Skill test directories use `spawn-claude` and `spawn-codex` naming.
- Commit history is squashed by topic for approval gate presentation.

## Acceptance Criteria
- No unresolved references remain in changed files to removed skill paths/names that this issue replaced.
- `mvn -f client/pom.xml verify -e` passes from the issue worktree.
- `git log v2.1..HEAD` on the issue branch shows only issue-related topic commits.

## Validation
- mvn -f client/pom.xml verify -e
