# Plan: restructure-pricing-tiers

## Goal
Restructure pricing tiers from persona-based (Indie/Team/Enterprise) to edition-based (Core/Pro/Enterprise). The free tier becomes a bounded, closed feature set (the core workflow) rather than an open-ended "everything a solo developer needs" promise. This ensures new features default to paid tier.

## Satisfies
None - strategic pricing restructure

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Documentation-only changes, no code behavior affected
- **Mitigation:** Review all four files for internal consistency

## Files to Modify
- `plugin/config/tiers.json` - Rename indie→core, add pro tier between core and enterprise, reassign features
- `docs/PRICING-STRATEGY.md` - Rewrite tier definitions, feature tables, remove "full individual productivity" language
- `docs/PRICING.md` - Update tier names, feature lists, messaging, FAQ
- `LICENSE.md` - Update "Indie Use"→"Core Use", add Pro tier legal terms

## Acceptance Criteria
- [ ] tiers.json has three tiers: core, pro, enterprise with correct feature assignments
- [ ] Core tier includes only: plan-work-commit-merge workflow, git safety, single project, basic hooks
- [ ] Pro tier includes: stakeholder reviews, learning/RCA, decomposition, research, token tracking, skill builder, custom hooks, multi-project, plus all team collaboration features
- [ ] Enterprise tier unchanged
- [ ] PRICING-STRATEGY.md reflects edition-based model with 14-day Pro trial
- [ ] PRICING.md messaging uses Core/Pro/Enterprise naming
- [ ] LICENSE.md legal terms updated for Core Use and Pro tier
- [ ] No references to "Indie" remain in any of the four files
- [ ] All four files internally consistent (same feature assignments, same tier names)

## Execution Steps
1. **Update tiers.json:** Rename indie→core, add pro tier with features moved from indie and new team features, keep enterprise unchanged
2. **Update PRICING-STRATEGY.md:** Rewrite tier overview table, feature breakdown tables, support obligations, target market sections. Add 14-day Pro trial strategy. Remove all "full individual productivity" and "solo developer" scoping language.
3. **Update PRICING.md:** Rewrite tier sections (Core/Pro/Enterprise), update feature tables, FAQ, messaging
4. **Update LICENSE.md:** Replace "Indie Use" with "Core Use" throughout, add "Pro Use" definition and grant section, update feature tier references
5. **Cross-check consistency:** Verify all four files use same tier names, same feature assignments, same pricing

## Success Criteria
- [ ] All four files updated and internally consistent
- [ ] No remaining references to "Indie" tier
- [ ] Free tier defined as closed feature set, not persona-based