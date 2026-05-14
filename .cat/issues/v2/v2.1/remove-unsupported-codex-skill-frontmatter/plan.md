# Remove Unsupported Codex Skill Frontmatter

## Objective

Review all Codex-specific skill files and remove frontmatter properties that Codex does not support.

## Pre-conditions

- The work runs in a CAT-managed worktree for `v2.1`.
- Codex-specific skill files are identified before editing.
- Supported Codex skill frontmatter properties are determined from local conventions or current Codex behavior.

## Implementation Plan

1. Identify every Codex-specific skill file in the repository.
2. Inspect their YAML frontmatter keys.
3. Determine which frontmatter keys are supported for Codex skills.
4. Remove unsupported properties from the affected frontmatter blocks while preserving supported metadata and skill content.
5. Run a targeted check that no Codex-specific skill frontmatter still contains unsupported properties.
6. Run the full project verification command.

## Post-conditions

- [ ] All Codex-specific skill frontmatter uses only supported properties.
- [ ] Unsupported properties are removed without changing skill bodies unnecessarily.
- [ ] A targeted validation confirms no unsupported properties remain in Codex-specific skills.
- [ ] `mvn -f client/pom.xml verify -e` passes.
