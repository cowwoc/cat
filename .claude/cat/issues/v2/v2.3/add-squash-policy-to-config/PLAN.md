# Plan: Add Squash Policy to /cat:config Wizard

## Goal

Add squash policy configuration to the `/cat:config` interactive wizard so users can set their preferred squash
strategy without manually editing PROJECT.md.

## Satisfies

None (infrastructure improvement)

## Current State

The git-squash skill reads `### Squash Policy` from `.claude/cat/PROJECT.md` but there is no interactive way to set it.
Users must manually add the section. The `/cat:config` wizard does not expose this setting despite
`v2.0-branching-strategy-config` having planned it.

## Target State

The `/cat:config` wizard includes a squash policy question with options:
- **single** — squash all commits into one
- **by-type** — group commits by type prefix (feature:, config:, etc.)
- **by-topic** — group by logical topic (default, current behavior)
- **keep-all** — preserve all commits as-is

The selected policy is written to the `### Squash Policy` section in PROJECT.md.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Must not break existing PROJECT.md files that already have manual squash policy sections
- **Mitigation:** Read existing value as default selection in wizard

## Files to Modify

- plugin/skills/config/SKILL.md — add squash policy question to wizard flow
- .claude/cat/PROJECT.md — written by wizard when policy is selected

## Acceptance Criteria

- [ ] `/cat:config` wizard includes squash policy question
- [ ] Selected policy is written to PROJECT.md `### Squash Policy` section
- [ ] Existing manual policies are preserved as default selection
- [ ] git-squash skill correctly reads the wizard-set policy

## Execution Steps

1. **Step 1:** Add squash policy question to `/cat:config` wizard
   - Files: plugin/skills/config/SKILL.md
2. **Step 2:** Write selected policy to PROJECT.md
   - Files: .claude/cat/PROJECT.md
3. **Step 3:** Verify git-squash reads the new policy correctly

## Success Criteria

- [ ] Running `/cat:config` shows squash policy option
- [ ] Setting policy via wizard produces correct PROJECT.md section
- [ ] git-squash skill honors the wizard-configured policy
