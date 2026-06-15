<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan-Builder Tier Selection

Use this include when the caller needs a `plan-builder` agent but has not preselected complexity.

Before this include, the caller must define:
- `AGENT_TASK`: short task label and enough context to judge complexity.

The caller may also define optional task-specific hints:
- `LOW_SIGNALS`
- `MEDIUM_SIGNALS`
- `HIGH_SIGNALS`

Or use this compact form immediately before the include:

```text
Auto-tier `plan-builder` for {AGENT_TASK}.
```

## Tier Rules

Select the weakest tier that is clearly reasonable:

- `low`: narrow, concrete, low-risk, local work with no unresolved product, architecture, testing, security,
  migration, deployment, or compatibility decisions.
- `medium`: normal work, moderate ambiguity, multiple files or requirements, ordinary cross-checking,
  unclear scope, or any case where `low` is not clearly safe.
- `high`: broad or vague inputs, cross-cutting changes, architecture decisions, data migration,
  security/privacy/legal/performance/deployment risk, repeated failures, contradictory evidence, version-shaping
  work, or any task where a wrong judgment would be expensive.

If classification is uncertain, use `medium`. If any high signal is present, use `high`. Optional caller-provided
signals add to these defaults; they do not replace the rules below.

For `plan-builder`:
- Use `low` only for a mechanical plan for a simple local change where requirements, affected files, acceptance
  criteria, and verification are already clear.
- Use `medium` for ordinary issue planning, missing implementation details, dependency ordering, or acceptance
  criteria design.
- Use `high` for ambiguous requirements, multi-subsystem plans, architecture-sensitive work, risky migrations,
  security/performance/legal/deployment concerns, or plans that must reconcile conflicting evidence.

## Spawn And Escalation

1. Set `AGENT_TIER` to the selected `low`, `medium`, or `high` value.
2. Set `AGENT_ALIAS` to the current engine's concrete CAT plan-builder agent type for that tier:
   - Codex: `cat-plan-builder-${AGENT_TIER}`
   - Claude: `cat:plan-builder-${AGENT_TIER}`
3. Spawn `AGENT_ALIAS` with the caller-supplied prompt. This include selects the concrete CAT agent type only; the
   surrounding workflow owns concrete tool and field names.
4. Do not use a generic/default agent when the selected tiered CAT agent alias is available. If the selected alias is
   unavailable, stop and report the missing CAT agent alias.
    