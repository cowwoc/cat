# Plan
## Goal
Replace hand-maintained runtime-specific output rendering instructions with a source-only `cat:render-output`
preprocessor directive that generates the correct Claude or Codex invocation during artifact builds.

## Parent Requirements
None (runtime-instruction quality and parity hardening)

## Research Findings
- Runtime skill sources live under:
  - `client/plugin/skills/claude/**`
  - `client/plugin/skills/codex/**`
  - shared fragments under `client/plugin/skills/include/**`
- CAT already preprocesses source-only `cat:include` directives while flattening plugin artifacts.
- Output-rendering wrappers duplicated the same command semantics across Claude and Codex files:
  - Claude needs silent preprocessor command invocation.
  - Codex needs explicit Bash invocation instructions and verbatim output rendering.
- A `cat:render-output` directive can remove that duplication while preserving runtime-specific generated artifacts.
- Developer-facing directive docs belong under `docs/development/` and should be linked from the README footer.

## Approach Selected
Add `cat:render-output` to the artifact builder, convert output-rendering skill wrappers to use it as source syntax,
and add artifact-level tests that verify generated Claude and Codex outputs still match their runtime conventions.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  - A directive parser could leave unresolved source markers in generated artifacts.
  - Placeholder handling could map skill arguments incorrectly for Claude positional parameters.
  - Codex-generated Bash instructions could lose the explicit `CAT_PLUGIN_DATA` guard or verbatim rendering wording.
- **Mitigation:**
  - Add RED/GREEN artifact-builder tests for directive expansion.
  - Reject generated artifacts that still contain `cat:render-output`.
  - Keep generated assertions runtime-specific and pattern-based rather than whitespace-sensitive.

## Files to Modify
- `client/common-cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java` - Implement
  `cat:render-output` expansion and unresolved-directive verification.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java` - Add
  regression coverage for directive expansion and generated runtime conventions.
- `client/plugin/skills/claude/get-diff/SKILL.md` - Keep Claude metadata and load shared first-use instructions.
- `client/plugin/skills/claude/status/SKILL.md` - Keep Claude metadata and load shared first-use instructions.
- `client/plugin/skills/claude/token-report/SKILL.md` - Keep Claude metadata and load shared first-use instructions.
- `client/plugin/skills/claude/work-complete/SKILL.md` - Keep Claude metadata and load shared first-use instructions.
- `client/plugin/skills/codex/get-diff/SKILL.md` - Keep Codex metadata and load shared first-use instructions.
- `client/plugin/skills/codex/status/SKILL.md` - Keep Codex metadata and load shared first-use instructions.
- `client/plugin/skills/codex/token-report/SKILL.md` - Keep Codex metadata and load shared first-use instructions.
- `client/plugin/skills/codex/work-complete/SKILL.md` - Keep Codex metadata and load shared first-use instructions.
- `client/plugin/skills/common/get-diff/first-use.md` - Own shared output-render instructions with
  `cat:render-output`.
- `client/plugin/skills/common/status/first-use.md` - Own shared output-render instructions with
  `cat:render-output`.
- `client/plugin/skills/common/token-report/first-use.md` - Own shared output-render instructions with
  `cat:render-output`.
- `client/plugin/skills/common/work-complete/first-use.md` - Own shared output-render instructions with
  `cat:render-output`.
- `client/plugin/skills/include/get-diff.md` - Remove single-use output-render fragment after inlining.
- `client/plugin/skills/include/status.md` - Remove single-use output-render fragment after inlining.
- `client/plugin/skills/include/token-report.md` - Remove single-use output-render fragment after inlining.
- `client/plugin/skills/include/work-complete.md` - Remove single-use output-render fragment after inlining.
- `docs/development/preprocessor-directives.md` - Document CAT-specific preprocessor directives for developers.
- `README.md` - Link to the developer notes at the bottom of the README.

## Pre-conditions
- [ ] Issue worktree is current and clean enough for planned edits.
- [ ] Runtime artifact build test fixture still generates both `claude` and `codex` runtime roots.
- [ ] No concurrent issue is modifying the same runtime output-render skill files.

## Jobs
### Job 1: Add `cat:render-output` preprocessing
- Add directive parsing to the artifact builder after include expansion and before runtime placeholder replacement.
- Generate the common deterministic-output prose as part of `cat:render-output`, so source skills do not repeat it.
- Generate Claude silent preprocessor commands with `CAT_PLUGIN_ROOT` and `CAT_PLUGIN_DATA` validation.
- Generate Codex Bash instructions with `CAT_PLUGIN_DATA` validation and placeholder guidance.
- Treat the directive command name as the first `get-output` argument; do not pass or skip Claude `"$0"` agent IDs.
- Reject generated runtime artifacts that still contain unresolved `cat:render-output` markers.

### Job 2: Convert output-rendering skill wrappers
- Move output-render command instructions into shared common `first-use.md` files that use `cat:render-output`.
- Inline single-use output-render include fragments into the owning common `first-use.md` files.
- Remove the now-unreferenced output-render include fragment files.
- Remove repeated deterministic-output boilerplate from the common source skills after folding it into
  `cat:render-output`.
- Leave runtime-specific `SKILL.md` files as metadata wrappers that load shared first-use instructions.
- Keep shared include fragments runtime-neutral.
- Confirm generated Claude artifacts contain preprocessor commands and no Bash blocks.
- Confirm generated Codex artifacts contain Bash instructions and no preprocessor command markers.

### Job 3: Add developer documentation
- Document `cat:include`, `cat:render-output`, and CAT runtime placeholders in a human-facing developer doc.
- Include examples for argument placeholders.
- Add a bottom-of-README link to the developer notes.

### Job 4: Verify and prepare for review
- Run targeted tests first for fast feedback:
  - `mvn -f client/pom.xml -Dtest=PluginArtifactBuilderTest test -e`
- Run full required verification:
  - `mvn -f client/pom.xml verify -e`
- Re-run review steps after the feedback changes.
- Squash commits by topic before returning to the approval gate.
- Require the workflow to persist the reviewed HEAD SHA and block the approval gate when the latest implementation
  change has not been covered by a valid stakeholder review.
- Require stakeholder review to re-check the exact manifest HEAD immediately before reviewer dispatch and require
  reviewer prompts to reject stale worktree content before reading files.

## Post-conditions
- [ ] Shared common `first-use.md` files use `cat:render-output` instead of duplicating runtime invocation text.
- [ ] Shared common `first-use.md` files contain only `cat:render-output` directives for output rendering; no
      single-use output-render include fragments or repeated deterministic-output prose remain.
- [ ] `cat:render-output` generates the common deterministic-output prose for both runtimes.
- [ ] Claude runtime artifacts still render deterministic output through silent preprocessor commands.
- [ ] Codex runtime artifacts still render deterministic output through explicit Bash instructions.
- [ ] Generated runtime artifacts contain no unresolved `cat:render-output` markers.
- [ ] Developer documentation covers CAT-specific preprocessor directives and runtime placeholders.
- [ ] README links to the developer notes at the bottom.
- [ ] Artifact-level tests enforce the rendering conventions.
- [ ] Approval-gate workflow rejects stale stakeholder reviews whose `reviewed_head_sha` does not match current HEAD.
- [ ] Stakeholder review dispatch refuses to spawn reviewer agents when current HEAD differs from the manifest HEAD.
- [ ] Executable artifact test verifies stale manifest HEAD exits before reviewer dispatch reaches the spawn step.
- [ ] `mvn -f client/pom.xml verify -e` passes.
