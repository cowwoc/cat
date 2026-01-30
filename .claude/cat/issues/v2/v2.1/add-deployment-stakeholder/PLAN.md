# Issue: add-deployment-stakeholder

## Type
Feature

## Goal
Add a deployment stakeholder to the stakeholder review system that evaluates changes from a CI/CD and build/deploy perspective.

## Description
Add a new "deployment" stakeholder that reviews implementation changes for:
- Build system impact (does the change break builds?)
- CI pipeline compatibility (tests, linting, artifact generation)
- Deployment concerns (configuration, environment variables, migrations)
- Release readiness (versioning, changelog, backwards compatibility)

## Scope
- 1-2 files (stakeholder definition + SKILL.md update)

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions

## Satisfies
None

## Implementation Notes
1. Create `plugin/skills/stakeholder-review/stakeholders/deployment.md`
2. Add deployment to the stakeholder table in `plugin/skills/stakeholder-review/SKILL.md`
3. Add keyword mappings for deployment-related terms (CI, pipeline, build, deploy, release)
