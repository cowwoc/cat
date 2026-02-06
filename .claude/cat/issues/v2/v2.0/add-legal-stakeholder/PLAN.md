# Plan: add-legal-stakeholder

## Goal
Add a legal stakeholder to the CAT stakeholder review process for evaluating licensing terms, compliance requirements,
and legal implications during planning and review phases.

## Satisfies
- None (infrastructure/process improvement)

## Approach Outlines

### Conservative
Add legal perspective template to stakeholder-review skill only.
- **Risk:** LOW
- **Tradeoff:** Limited to review phase, no research integration

### Balanced
Add legal stakeholder to both research and review workflows with focus areas: licensing, compliance, IP, liability.
- **Risk:** MEDIUM
- **Tradeoff:** May need refinement based on actual usage

### Aggressive
Full legal stakeholder with specialized prompts for different legal domains (IP, compliance, contracts, liability, data
privacy).
- **Risk:** HIGH
- **Tradeoff:** Over-engineering for current needs

## Acceptance Criteria
- [ ] Legal stakeholder perspective added to stakeholder-review skill
- [ ] Legal perspective includes relevant focus areas (licensing, compliance, IP, liability)
- [ ] Stakeholder can be invoked during /cat:research and /cat:stakeholder-review
- [ ] Documentation updated to list legal as available stakeholder
