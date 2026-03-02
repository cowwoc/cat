# Plan: fix-learn-priming-blind-spots

## Problem

The learn skill's priming analysis has two structural blind spots that caused the investigate subagent to
report `priming_found: false` for M452 when cognitive-anchor priming clearly existed in the documentation.

**Blind Spot 1:** Missing priming pattern category — the priming table in `phase-investigate.md` (lines
153-156) covers algorithm exposure, output format values, cost concerns, and conflicting guidance, but
does NOT cover **cognitive anchoring**: documentation that establishes a named default value or fallback
location, creating an implicit behavior the agent uses when its primary path becomes unavailable.
Example: "Your working directory defaults to /workspace" establishes `/workspace` as a cognitive anchor.

**Blind Spot 2:** Priming search scope excludes skill content — `phase-investigate.md` Step 2 instructs
checking `documents_read` for priming patterns, but skill files loaded via the Skill tool appear in
`skill_invocations`, not `documents_read`. The investigation instructions don't say to also check expanded
skill content (what was delivered to subagents via delegation prompts) for priming patterns.

**Blind Spot 3:** documentation-priming.md check questions omit default/fallback detection — the priming
check questions ask about algorithms, output formats, and costs, but never ask "Does this document
establish a default value or path that the agent might fall back to when its primary path fails?"

## Root Cause

The learn skill's priming analysis was designed to catch **active** priming patterns (algorithm exposure,
output format injection, cost pressure). It misses **passive** priming: language that establishes defaults
the agent falls back to when something unexpected happens. Passive priming only manifests under failure
conditions, making it invisible during normal operation.

## Satisfies

None — bugfix for learn skill investigation methodology.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Changes to learn skill documentation affect all future learn invocations
- **Mitigation:** Additive changes only — new pattern category added, no existing content removed

## Files to Modify

- `plugin/skills/learn/phase-investigate.md` — add cognitive anchoring row to priming table; add explicit
  instruction to check skill_invocations content for priming (Step 2)
- `plugin/skills/learn/documentation-priming.md` — add Pattern 4 (cognitive anchoring) with description,
  example, fix guidance, and "default/fallback" check question

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Add cognitive anchoring pattern to priming analysis

- Add cognitive anchoring row to the priming table in `phase-investigate.md` Step 2 (after the
  "Conflicting general guidance" row):
  ```
  | Cognitive anchor (default/fallback) | "defaults to /workspace" | Agent falls back to named default when primary path fails |
  ```
  - Files: `plugin/skills/learn/phase-investigate.md`

- Add explicit instruction to Step 2 to check skill_invocations content:
  After the paragraph "For each document, check for priming patterns:", add:
  "Also check the content of expanded skill files (from `skill_invocations`): review the delegation
  prompts and skill content that subagents received for any of the above patterns, especially cognitive
  anchors establishing default paths or values."
  - Files: `plugin/skills/learn/phase-investigate.md`

- Add Pattern 4 (Cognitive Anchoring) to `documentation-priming.md`:
  - Pattern description: Documentation establishes a named default value or path, creating a cognitive
    anchor the agent falls back to when its primary path becomes unavailable
  - Example: "Your working directory defaults to /workspace (main worktree)"
  - Why it primes: When the primary path (e.g., worktree) disappears, the agent silently uses the named
    default instead of failing with an error
  - Fix: Replace named defaults with action-oriented framing ("navigate to the worktree and verify
    the branch") that requires active verification rather than passive fallback
  - Add check question: "Does this document name a specific default location that the agent might fall
    back to when its primary path becomes unavailable?"
  - Files: `plugin/skills/learn/documentation-priming.md`

- Run `mvn -f client/pom.xml test` to verify no regressions

## Post-conditions

- [ ] `phase-investigate.md` priming table includes a "Cognitive anchor" row with example and risk column
- [ ] `phase-investigate.md` Step 2 explicitly instructs checking skill_invocations content for priming
- [ ] `documentation-priming.md` includes Pattern 4 for cognitive anchoring with example and fix guidance
- [ ] `documentation-priming.md` check questions include the default/fallback detection question
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
