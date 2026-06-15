<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan-Review Tier Selection

Use this include when the caller needs a `plan-review` agent but has not preselected complexity.

Before this include, the caller must define:
- `AGENT_TASK`: short task label and enough context to judge complexity.

The caller may also define optional task-specific hints:
- `LOW_SIGNALS`
- `MEDIUM_SIGNALS`
- `HIGH_SIGNALS`

Or use this compact form immediately before the include:

```text
Auto-tier `plan-review` for {AGENT_TASK}.
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

For `plan-review`:
- Use `low` for checklist-style review of simple, local, mechanically executable plans.
- Use `medium` for normal plan implementability, sequencing, acceptance-criteria, and test-coverage review.
- Use `high` for architecture-sensitive, contradiction-prone, cross-cutting, high-risk, or previously rejected
  plans.
- Also use `high` when the plan involves more than 2-3 subsystems; spans milestones, migrations, rollout,
  CI/build/plugin lifecycle, or workflow semantics; has conflicting docs, tests, config, or implementation patterns;
  has judgment-heavy acceptance criteria; is evidence-gated; has non-trivial sequencing dependencies; or involves
  performance, concurrency, persistence, compatibility, or public API behavior.

## Spawn And Escalation

1. Set `AGENT_TIER` to the selected `low`, `medium`, or `high` value.
2. Set `AGENT_ALIAS` to the current engine's concrete CAT plan-review agent type for that tier:
   - Codex: `cat-plan-review-${AGENT_TIER}`
   - Claude: `cat:plan-review-${AGENT_TIER}`
3. Spawn `AGENT_ALIAS` with the caller-supplied prompt. This include selects the concrete CAT agent type only; the
   surrounding workflow owns concrete tool and field names.
4. Do not use a generic/default agent when the selected tiered CAT agent alias is available. If the selected alias is
   unavailable, stop and report the missing CAT agent alias.
