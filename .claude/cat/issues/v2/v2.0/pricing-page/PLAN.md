# Plan: pricing-page

## Goal
Consolidate pricing information and create a professional GitHub Pages pricing page. Based on stakeholder review (Sales + Marketing), this task implements a unified pricing strategy.

## Satisfies
- Professional pricing presentation for SMB buyers
- Reduced maintenance burden (single source of truth)
- Brand consistency with SaaS competitors

## Stakeholder Recommendations (Implemented)

### Sales Stakeholder
- Keep brief pricing mention in README (free tier is competitive advantage)
- Remove hardcoded prices from LICENSE.md
- Create professional GitHub Pages pricing page

### Marketing Stakeholder
- Streamline README to focus on value proposition
- Reference pricing URL in LICENSE instead of hardcoded prices
- GitHub Pages pricing page for professional SaaS appearance

### Consensus Approach
1. **README.md**: Keep single line mentioning "Free for solo developers" + link to pricing
2. **LICENSE.md**: Remove price table, keep tier descriptions with link to pricing page
3. **GitHub Pages**: Create styled HTML pricing page

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Broken links during transition; price inconsistencies
- **Mitigation:** Audit all pricing links; use consistent source

## Files to Modify
- README.md
  - Simplify pricing section to single line + link
- LICENSE.md
  - Remove price table
  - Keep tier descriptions
  - Reference pricing URL
- docs/PRICING.md
  - Ensure complete as markdown fallback
- docs/pricing.html (NEW)
  - GitHub Pages styled pricing page

## Acceptance Criteria
- [ ] README has simplified pricing reference (free tier mention + link)
- [ ] LICENSE.md references pricing URL instead of hardcoded prices
- [ ] GitHub Pages pricing page is live and professional
- [ ] All pricing links point to consistent destination
- [ ] Feature comparison table visible on pricing page

## Execution Steps

1. **Simplify README.md pricing section**
   - Replace multi-line pricing with: "**Free for solo developers.** See [pricing](docs/PRICING.md) for team and enterprise options."
   - Verify: grep shows simplified pricing line

2. **Update LICENSE.md**
   - Remove price table (lines with $19, $49)
   - Keep tier descriptions without specific prices
   - Add reference: "For current pricing, see PRICING.md"
   - Verify: No hardcoded dollar amounts in LICENSE.md

3. **Create GitHub Pages pricing page (docs/pricing.html)**
   - Professional 3-tier layout (Indie/Team/Enterprise)
   - Feature comparison matrix
   - Clear CTAs for each tier
   - Mobile-responsive design
   - Verify: Page renders correctly at /docs/pricing.html

4. **Update PRICING.md**
   - Ensure all tiers and features documented
   - Add link to HTML pricing page when on GitHub
   - Verify: Consistent with HTML page content

5. **Audit all pricing links**
   - Verify README → pricing works
   - Verify LICENSE → pricing works
   - Verify no broken references
