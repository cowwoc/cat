# CAT Pricing Strategy

## Executive Summary

CAT is an AI-powered development workflow orchestration plugin for Claude Code. This pricing strategy is designed for a
**solo-dev business** with limited support capacity.

**Key Decisions:**
- 3-tier per-seat pricing: Indie (Free), Team, Enterprise
- Positioned as ~10-25% of Claude Max 20x spend ($200/user/month)
- Per-seat pricing for simplicity and scalability
- Minimal support obligations - community-first model
- Explicit exclusion of regulated/traditional enterprise segments
- Enterprise volume discounts via negotiation, not published lower prices

---

## Pricing Tiers

### Overview

| Tier | Price | Users | % of Claude Max 20x |
|------|-------|-------|---------------------|
| **Indie** | Free | 1 | 0% |
| **Team** | $19/seat/mo | 1-50 | 9.5% |
| **Enterprise** | $49/seat/mo | 50+ | 24.5% |

### Annual Pricing (17% discount / 2 months free)

| Tier | Monthly | Annual | Annual Total (10 seats) |
|------|---------|--------|-------------------------|
| Indie | $0 | $0 | $0 |
| Team | $19/seat | $15.83/seat | $1,900/yr |
| Enterprise | $49/seat | $40.83/seat | $4,900/yr |

---

## Total Cost with Claude

CAT is a plugin on top of Claude Code. Total cost includes Claude Max 20x subscription ($200/user/month).

| Segment | Claude Cost | + CAT | Total | CAT % of Total |
|---------|-------------|-------|-------|----------------|
| Solo (Indie) | $200/mo | $0 | $200/mo | 0% |
| Solo (Team) | $200/mo | $19 | $219/mo | 8.7% |
| Team of 5 | $1,000/mo | $95 | $1,095/mo | 8.7% |
| Team of 10 | $2,000/mo | $190 | $2,190/mo | 8.7% |
| Enterprise 50 | $10,000/mo | $2,450 | $12,450/mo | 19.7% |
| Enterprise 100 | $20,000/mo | $4,900 | $24,900/mo | 19.7% |

**Positioning**: Professional add-on pricing at 9-20% of Claude spend - the expected range for productivity tooling.

---

## Feature Breakdown by Tier

### Core Features (All Tiers)

| Feature | Indie | Team | Enterprise |
|---------|-------|------|------------|
| **Core Planning** | ✓ | ✓ | ✓ |
| **Worktree Isolation** | ✓ | ✓ | ✓ |
| **11 Stakeholder Reviews** | ✓ | ✓ | ✓ |
| **Mistake Learning (RCA)** | ✓ | ✓ | ✓ |
| **Git Safety Tools** | ✓ | ✓ | ✓ |
| **Stakeholder Research** | ✓ | ✓ | ✓ |
| **Unlimited Projects** | ✓ | ✓ | ✓ |

*No artificial limits on worktrees, subagents, or tasks. Indie has full individual productivity.*

### Team Collaboration

| Feature | Indie | Team | Enterprise |
|---------|-------|------|------------|
| **Collision Prevention** | — | ✓ | ✓ |
| **Team Pulse Dashboard** | — | ✓ | ✓ |
| **Shared Brain (conventions)** | — | ✓ | ✓ |
| **Context Preservation** | — | ✓ | ✓ |
| **Branch Policies** | — | ✓ | ✓ |
| **Team Analytics** | — | ✓ | ✓ |
| **Project Budgets** | — | ✓ | ✓ |
| **Slack/Discord Notifications** | — | ✓ | ✓ |
| **GitHub PR Integration** | — | ✓ | ✓ |
| **CI Status Awareness** | — | ✓ | ✓ |
| **GitHub Issues Sync** | — | ✓ | ✓ |
| **Linear Sync** | — | ✓ | ✓ |

### Enterprise & Compliance

| Feature | Indie | Team | Enterprise |
|---------|-------|------|------------|
| **SSO/SAML (via WorkOS)** | — | — | ✓ |
| **SCIM Provisioning** | — | — | ✓ |
| **AI Audit Trail** | — | — | ✓ |
| **Jira Sync** | — | — | ✓ |
| **Teams Notifications** | — | — | ✓ |
| **Webhook API** | — | — | ✓ |
| **Data Residency** | — | — | ✓ |
| **Priority Email Support** | — | — | ✓ |

### Key Feature Definitions

**Context Preservation** (Team)
- **Purpose**: Help developers pick up work smoothly after interruptions
- **Content**: Decisions made, blockers encountered, next steps, key context
- **When used**: Day-to-day, at session start
- **Retention**: Until task completes
- **Example**: "Chose approach A over B because of X constraint. Blocked on API key. Next: implement caching."

**AI Audit Trail** (Enterprise)
- **Purpose**: Prove what AI did for compliance, security, and legal requirements
- **Content**: Full conversation history, every code change, every approval, timestamped
- **When used**: SOC 2 audits, incident investigations, IP documentation, due diligence
- **Retention**: Long-term (configurable, typically months/years)
- **Format**: Machine-exportable JSON/CSV for compliance reporting
- **Compliance uses**: SOC 2, ISO 27001, HIPAA, FedRAMP, PCI-DSS, GDPR Article 30

---

## Support Obligations by Tier

### Overview

| Tier | Support Model | Response Time | My Weekly Hours |
|------|---------------|---------------|-----------------|
| **Indie** | Community only | No guarantee | 0 hrs |
| **Team** | Community + priority label | 72hr goal | ~2 hrs |
| **Enterprise** | Email support | 48hr goal | ~3-5 hrs |

### Detailed Obligations

#### Indie (Free)

| Aspect | Details |
|--------|---------|
| Support channel | Discord, GitHub Issues only |
| Response time | No guarantee |
| SLA | None |
| My time commitment | Zero direct support |

#### Team ($19/seat/mo)

| Aspect | Details |
|--------|---------|
| Support channel | Discord, GitHub Issues with priority label |
| Response time | 72hr goal (not contractual) |
| SLA | None |
| My time commitment | ~2 hrs/week |
| Automation | Priority queue, issue templates |

#### Enterprise ($49/seat/mo)

| Aspect | Details |
|--------|---------|
| Support channel | Email support |
| Response time | 48hr goal (not contractual) |
| SLA | Best-effort, not legally binding |
| My time commitment | ~3-5 hrs/week |
| Automation | Dedicated email alias, canned responses |
| Volume discounts | Negotiated for 100+ seats with annual commitment |

### What I'm NOT Offering (Any Tier)

| Excluded | Reason |
|----------|--------|
| Phone/video support | Not scalable solo |
| Contractual SLAs | Legal liability |
| Custom contracts | Legal costs |
| Self-hosted deployment | Support nightmare |
| Custom LLM endpoints | Per-customer maintenance |
| SOC2/HIPAA certs | $50K+ audits |
| Dedicated account manager | Full-time role |
| On-call/emergency support | Burnout prevention |

---

## Target Market

### Who We're Targeting

| Segment | Size | Why They Fit |
|---------|------|--------------|
| **Solo indie hackers** | 1 person | Free Indie tier is full-featured, no vendor anxiety |
| **Freelancers/contractors** | 1-2 people | Team at $19-38/mo is trivial expense |
| **Early-stage startups** | 2-15 people | Team tier at $19/seat is great value |
| **Small dev teams in larger orgs** | 5-50 devs | Can fly under procurement radar |
| **Developer-led companies** | Any size | Engineers hate procurement theater |

### Who We're Excluding

| Segment | Why Excluded | Missing Requirement |
|---------|--------------|---------------------|
| **Regulated industries** (Healthcare, Finance) | No SOC2/HIPAA certs | Compliance certs, security questionnaires |
| **Traditional enterprise** (500+ employees) | No contractual SLAs | Legal review, procurement process |
| **Risk-averse mid-market** | Limited vendor due diligence | Escalation paths, business continuity |
| **Agencies/consultancies** | Not multi-tenant | Client billing, white-labeling |
| **Air-gapped/defense** | No self-hosted option | On-premise deployment |

### Market Sizing

| Segment | Our Fit | % of Dev Tool Market |
|---------|---------|---------------------|
| Solo/Indie | ✅ Perfect | ~15% |
| Freelance/Contractor | ✅ Perfect | ~10% |
| Seed/Series A startup | ✅ Good | ~20% |
| Series B+ startup | ⚠️ Partial | ~10% |
| Mid-market | ⚠️ Partial | ~15% |
| Traditional enterprise | ❌ Excluded | ~25% |
| Regulated industries | ❌ Excluded | ~10% |

**Addressable market**: ~45-55% of developer tool buyers
**Excluded market**: ~45-55% (and that's okay for a solo-dev business)

---

## Stakeholder Review Feedback

### Sales Review: APPROVED (Validated 2026-02-01)

**Recommendation:** Keep 3-tier structure with per-seat pricing

**Pros:**
- Free Indie tier is genuinely usable - creates word-of-mouth
- Team at $19/seat is low-friction conversion
- Enterprise at $49/seat sits at 24.5% of Claude spend - reasonable for premium tooling
- Volume discounts via negotiation (not published) captures enterprise value

**Cons:**
- Solo-dev business supporting Enterprise creates support burden expectation mismatch
- Need clear SLA limits for Enterprise tier

**Key Objection Responses:**

| Objection | Response |
|-----------|----------|
| "What if you get hit by a bus?" | CAT is open-source, runs locally, no vendor lock-in |
| "Why pay on top of Claude?" | Claude is the engine, CAT is the transmission |
| "Enterprise without real SLA?" | 48hr is what I beat, not a legal minimum |
| "Will you raise prices?" | 24-month grandfather clause for existing customers |

### Marketing Review: APPROVED (Validated 2026-02-01)

**Recommendation:** Maintain $49 Enterprise - don't lower price

**Key Insight:** "Lowering Enterprise from $49 to $29-35 is a POOR marketing move... Enterprise buyers expect to pay
more, not less - lower prices can actually reduce conversion because it appears 'not serious'"

**Pros:**
- "Indie" resonates with indie hacker movement
- 3 tiers is optimal for conversion (reduces cognitive load)
- Clear tier progression: solo → team → enterprise
- Per-seat pricing scales naturally with team size

**Cons:**
- Need guardrails on Free tier to prevent "team of solos" abuse

**Messaging Recommendations:**

| Tier | Value Prop |
|------|-----------|
| Indie | "Everything you need to ship solo" |
| Team | "Coordinate without collision" |
| Enterprise | "Compliance and control at scale" |

---

## Pricing Page Strategy

### Visual Hierarchy

1. Lead with Indie (free) to remove friction
2. Highlight Team as "Most Popular" with badge
3. Show Enterprise as premium anchor
4. Default toggle to Annual with "Save 17%" messaging

### Anchoring

- Enterprise at $49/seat makes Team at $19/seat feel like obvious value
- Show comparison to per-seat competitors (Cursor at $40/seat, Copilot at $39/seat)

### Messaging

| Tier | Headline | Subtext |
|------|----------|---------|
| Indie | "Get Started Free" | "Everything you need to ship solo" |
| Team | "Most Popular" | "Coordinate without collision" |
| Enterprise | "For Organizations" | "Compliance and control at scale" |

---

## Competitive Positioning

### Price Comparison

| Tool | Pricing Model | 10 Users Cost |
|------|---------------|---------------|
| Cursor Business | $40/seat | $400/mo |
| GitHub Copilot Enterprise | $39/seat | $390/mo |
| **CAT Team** | $19/seat | $190/mo |

**Position**: ~50% cheaper per-seat than competitors, with orchestration value (not just autocomplete).

### Differentiation

| Competitor Approach | CAT Difference |
|---------------------|----------------|
| AI coding assistants | CAT is orchestration, not autocomplete |
| Higher per-seat pricing ($39-40) | CAT at $19/seat Team, $49/seat Enterprise |
| Enterprise sales theater | Self-serve, transparent pricing |

### Value Message

> "Stop babysitting your AI. CAT gives you reliable, controlled, team-coordinated AI development."

---

## Objection Handling

| Objection | Response |
|-----------|----------|
| "Too expensive" | At $19/seat (9.5% of Claude), ROI is obvious: time saved, rework prevented |
| "We don't need all features" | Start with Indie free, upgrade to Team when you need collaboration |
| "Can just use Claude directly" | CAT adds reliability - worktrees, checkpoints, reviews |
| "What if you disappear?" | Open source, runs locally, no vendor lock-in |
| "Need contractual SLA" | Not our market - we serve developer-led teams |
| "Enterprise seems expensive" | At $49/seat (24.5% of Claude), you get SSO, audit logs, priority support |

---

## Implementation Checklist

### Phase 1: Launch

- [ ] Build pricing page with 3-tier display
- [ ] Implement license key system
- [ ] Add feature flags for tier-gated features
- [ ] Set up Stripe billing (per-seat pricing)
- [ ] Create upgrade prompts at natural trigger points

### Phase 2: Team Features

- [ ] Build collision prevention system
- [ ] Create Team Pulse dashboard
- [ ] Implement context preservation (decision documentation)
- [ ] Add Slack/Discord notifications
- [ ] Build GitHub Issues & Linear sync
- [ ] Add CI status awareness (GitHub Actions)
- [ ] Implement team analytics
- [ ] Add project budget controls

### Phase 3: Enterprise Features

- [ ] Integrate WorkOS for SSO/SAML
- [ ] Add SCIM provisioning
- [ ] Build AI audit trail (conversation history export)
- [ ] Add Jira sync
- [ ] Add Teams notifications
- [ ] Build webhook API
- [ ] Create enterprise email support workflow

### Not Building (Solo-Dev Constraints)

- ❌ Self-hosted deployment option
- ❌ Custom LLM endpoint support
- ❌ SOC2/HIPAA certification
- ❌ Dedicated account management
- ❌ Contractual SLAs

---

## Research Sources

### Sales Perspective
- https://www.momentumnexus.com/blog/saas-pricing-strategy-guide-2026/
- https://www.withorb.com/blog/tiered-pricing-examples
- https://www.growthunhinged.com/p/2025-state-of-saas-pricing-changes
- https://www.getmonetizely.com/articles/saas-pricing-benchmark-study-2025-insights-from-100-companies
- https://thegood.com/insights/saas-pricing/
- https://www.getcone.io/blog/3-tier-pricing-strategy
- https://www.pclub.io/blog/overcoming-price-objections
- https://blog.hubspot.com/sales/price-objection-responses

### Marketing Perspective
- https://ghl-services-playbooks-automation-crm-marketing.ghost.io/advanced-saas-pricing-psychology-beyond-basic-tiered-models/
- https://www.getmonetizely.com/articles/what-is-price-anchoring-in-saas-and-how-to-test-it
- https://www.orbix.studio/blogs/saas-pricing-page-psychology-convert
- https://www.pacepricing.com/blog/hidden-prices-lost-buyers-why-b2b-saas-companies-should-embrace-transparency
- https://copyhackers.com/2025/03/saas-pricing-page-checklist/
- https://cieden.com/how-to-optimize-your-saas-pricing-page

---

*Document generated: 2026-02-01*
*Last updated: 2026-02-01*
*Based on sales and marketing stakeholder research*
*Designed for solo-dev business model*
