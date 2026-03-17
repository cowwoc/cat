# Plan: refactor-benchmark-artifact-storage

## Current State
The instruction-builder-agent writes benchmark artifacts (test-cases.json, benchmark.json,
compressed-SKILL.md) to a session-scoped directory: `benchmark-artifacts/<session-id>/`.
These files are effectively ephemeral — they change with every session and cannot be used to
compare results across sessions. The stable per-skill benchmark directory (e.g.,
`plugin/skills/status-agent/benchmark/`) exists but is not the canonical write target.

## Target State
The instruction-builder-agent writes benchmark artifacts exclusively to a stable directory
adjacent to the skill file being improved: `<skill-dir>/benchmark/`. For example:
- Plugin skill: `plugin/skills/status-agent/SKILL.md` → `plugin/skills/status-agent/benchmark/`
- End-user skill: `my-skills/my-skill/SKILL.md` → `my-skills/my-skill/benchmark/`

The session-scoped `benchmark-artifacts/<session-id>/` directory is no longer created.
This allows benchmark results to be committed alongside the skill and compared across sessions
to detect regressions or improvements.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — behavior-preserving change to artifact write location
- **Mitigation:** The stable per-skill benchmark/ directory already exists for skills that
  have been through the instruction-builder; the change only redirects writes

## Files to Modify
- plugin/skills/instruction-builder-agent/SKILL.md - update artifact write paths to use
  skill-adjacent benchmark/ directory instead of benchmark-artifacts/<session-id>/

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Read the current instruction-builder-agent SKILL.md to identify all locations where
  benchmark-artifacts/<session-id>/ paths are constructed or used
  - Files: plugin/skills/instruction-builder-agent/SKILL.md
- Update all benchmark artifact write paths to use the skill-adjacent benchmark/ directory:
  derive the benchmark dir from the skill file path (e.g., dirname of SKILL.md + "/benchmark/")
  - Files: plugin/skills/instruction-builder-agent/SKILL.md
- Ensure the approach works for both plugin skills (under plugin/skills/) and end-user skills
  (any path the instruction-builder receives as its argument)
  - Files: plugin/skills/instruction-builder-agent/SKILL.md
- Remove any instruction to create or populate benchmark-artifacts/<session-id>/ directory
  - Files: plugin/skills/instruction-builder-agent/SKILL.md

## Post-conditions
- [ ] instruction-builder-agent SKILL.md writes test-cases.json and benchmark.json to
  `<skill-dir>/benchmark/` (sibling of the SKILL.md being improved)
- [ ] No references to `benchmark-artifacts/<session-id>/` remain in instruction-builder-agent SKILL.md
- [ ] The path derivation works for end-user skills at any path (not just plugin/skills/)
- [ ] E2E: Run /cat:instruction-builder-agent on a test skill and confirm benchmark artifacts
  appear in the skill-adjacent benchmark/ directory, not in benchmark-artifacts/
