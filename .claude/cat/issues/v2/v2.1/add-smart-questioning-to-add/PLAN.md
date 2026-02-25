# Plan: add-smart-questioning-to-add

## Goal

Enhance `/cat:add` with two new behaviors: (1) smart questioning that detects ambiguous or uncertain aspects of issue descriptions and asks targeted clarifying questions, and (2) impact analysis that identifies how a proposed change might negatively affect existing features and surfaces those concerns to the user before issue creation. The depth of both behaviors scales with the `effort` config option.

## Satisfies

None ��� user-requested enhancement

## Context: Current State

The `issue_clarify_intent` step currently checks for vague descriptions using:
- Less than 10 words
- Generic terms like "improve", "fix", "make better" without specifics
- Missing what/where/why context

This catches obviously incomplete descriptions but misses:
- Descriptions with multiple valid interpretations
- Descriptions that omit scope boundaries
- Descriptions that could conflict with existing features
- Descriptions missing success criteria

The `issue_validate_criteria` step validates post-conditions but does not analyze feature impact.

## Effort Behavior Matrix

| Level | Smart Questioning | Impact Analysis |
|---|---|---|
| `low` | Current vague-description check only (no change) | Disabled ��� no impact analysis |
| `medium` | Detect ambiguous scope, conflicting requirements, unclear success criteria | Lightweight ��� check if description conflicts with existing issues in same version |
| `high` | Deep questioning on edge cases, trade-offs, and alternative interpretations | Comprehensive ��� analyze impact on related features, dependencies, backward compatibility |

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Additional questions could slow down issue creation for users who know exactly what they want
- **Mitigation:** At effort=low, behavior is unchanged; users control depth via config

## Files to Modify

- `plugin/skills/add/first-use.md` ��� Add new step `issue_smart_questioning` after `issue_clarify_intent`, and new step `issue_impact_analysis` after `issue_validate_criteria` (or integrate into existing steps)

## Pre-conditions

- [ ] `refactor-curiosity-to-effort` is closed (effort config exists)

## Execution Steps

1. **Add `issue_smart_questioning` step to `/cat:add`**
   - Files: `plugin/skills/add/first-use.md`
   - Insert new step after `issue_clarify_intent` (which handles vague descriptions)
   - Read `effort` level from config
   - At effort=low: skip (current behavior unchanged)
   - At effort=medium: analyze ISSUE_DESCRIPTION for ambiguous scope, conflicting requirements, unclear success criteria. Use AskUserQuestion to present detected ambiguities with concrete interpretation options
   - At effort=high: additionally probe for edge cases, trade-offs, alternative interpretations, and missing context about affected systems/components

2. **Add `issue_impact_analysis` step to `/cat:add`**
   - Files: `plugin/skills/add/first-use.md`
   - Insert new step before `issue_create` (after validation is complete but before writing files)
   - Read `effort` level from config
   - At effort=low: skip entirely
   - At effort=medium: check ISSUE_DESCRIPTION against existing issues in the selected version to identify conflicts or overlap
   - At effort=high: analyze broader impact on related features, dependencies, and backward compatibility across versions
   - When concerns are found, use AskUserQuestion to present them with options: "Proceed as described", "Revise description", "Split into multiple issues", "Add impact notes to plan"

3. **Update step numbering and cross-references**
   - Files: `plugin/skills/add/first-use.md`
   - Renumber any displaced steps
   - Update any internal references to step names

## Post-conditions

- [ ] When a user provides an issue description with ambiguous aspects, `/cat:add` asks targeted clarifying questions before proceeding
- [ ] When a proposed issue could impact existing features, `/cat:add` raises concerns to the user before creating the issue
- [ ] The depth of questioning and impact analysis scales with the `effort` config value (low/medium/high)
- [ ] At `effort=low`: no impact analysis; questioning limited to current vague-description check
- [ ] At `effort=medium`: questioning detects ambiguous scope/requirements; lightweight impact analysis checks related features in same version
- [ ] At `effort=high`: deep questioning on edge cases and trade-offs; comprehensive impact analysis on related features, dependencies, and backward compatibility
- [ ] When impact concerns are raised, user is presented with actionable options (proceed, revise, split, add notes)
- [ ] Existing issue creation flow is not broken (backward compatible at all effort levels)
- [ ] All tests pass
- [ ] E2E: Invoke `/cat:add` with an ambiguous description at effort=medium and confirm clarifying questions are asked
- [ ] E2E: Invoke `/cat:add` with a description that impacts an existing feature at effort=high and confirm concern is raised