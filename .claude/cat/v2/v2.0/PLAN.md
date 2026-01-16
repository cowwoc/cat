# Minor Version 2.1: Commercialization

## Objective
Prepare CAT for commercial release with pricing tiers and license key system.

## Research

**Topic:** Software Commercialization (pricing tiers, license key systems, commercial release)
**Date:** 2026-01-19
**Overall Confidence:** MEDIUM (lowest of all stakeholder confidences)

### Architect Perspective
**Stack:** Event-driven SaaS architecture with JWT-based license tokens + Stripe Billing + local feature gates. Optimal balance of developer trust, operational simplicity, and offline capability.

**Architecture:** Hybrid Offline-First with Online Sync pattern:
- Layer 1: Stripe Billing (webhooks + subscriptions)
- Layer 2: JWT License Token Generation + Validation
- Layer 3: Entitlement Resolver (tier → feature mapping)
- Layer 4: Feature Gate Middleware (injected at module boundaries)
- Layer 5: Webhook Handler + Cache Invalidation

**Build vs Use:**
- BUY: Payment Processing (Stripe) - compliance risk too high to build
- BUILD: License Key Gen/Validation (JWT) - 200 lines, no vendor lock-in
- BUILD: Entitlement Mapping - simple tier→features table
- BUILD: Feature Gates (Middleware) - must be fast (<1ms)
- BUILD: Offline Validation (Signed JWT) - industry standard

[Sources: Martin Fowler Feature Toggles, LicenseSpring, Keygen, Stripe docs]

### Security Perspective
**Threats:** Keygen programs, software cracking, algorithm leakage (PKV), signature forgery, clock tampering, local verification bypass, assembly-level code patching.

**Secure Patterns:**
- Use asymmetric cryptography (Ed25519 or RSA-2048) - makes keygen creation cryptographically infeasible
- Server-side entitlement authority - local manipulation impossible
- Signed license files for offline with expiry, grace period, hardware binding
- Grace periods (e.g., 7 days) bound window for cracked keys

**Mistakes to Avoid:**
- Client-side validation only (can be patched)
- Symmetric encryption (if leaked, attackers generate unlimited keys)
- PKV/partial key verification (leaks over time)
- Clock tampering vulnerability (time-based expiry)

[Sources: Keygen, OWASP, Ed25519 docs, security advisories]

### Quality Perspective
**Patterns:** Cryptographic signing with public-private keypair asymmetry, fail-secure validation, deterministic serialization, server-side operations isolation, idempotency for license redemption.

**Anti-Patterns to Avoid:**
- Embedding license validation throughout code (centralize in module)
- God Class license manager handling everything
- Spaghetti feature gating with scattered if-checks
- Hardcoding tier limits in code (use configuration)

**Maintainability:** Externalize tier/feature definitions as configuration, centralize feature checks through abstraction layer, clear schema separation between licensing and feature logic.

[Sources: Keygen docs, Softactivate, code quality guides]

### Tester Perspective
**Strategy:** Hybrid test pyramid - Unit (60%), Integration (30%), E2E (10%). Mock external dependencies, test real logic.

**Critical Edge Cases:**
- License key corruption and format variations
- Expiration/grace period boundaries (timezone, DST)
- Offline validation paths
- Concurrent activations and seat limits
- Trial circumvention (clock rollback)
- Tier upgrade/downgrade mid-cycle

**Test Data:** Fixture sets for valid/invalid licenses, tier transitions, hardware binding, timestamp boundaries. Factory pattern (LicenseBuilder) for fluent test creation.

[Sources: Apriorit, LaunchDarkly, Martin Fowler test pyramid]

### Performance Perspective
**Characteristics:** License checks must complete in <10ms (invisible to users). 100ms delays noticeably sluggish.

**Efficient Patterns:**
- Ed25519/ECDSA for fastest crypto (4-10x faster than RSA)
- Multi-tier caching: in-process LRU + local file + distributed (Redis)
- Target 80%+ cache hit rate
- Offline-first for free tier, cached server validation for paid

**Pitfalls:**
- Per-request database lookups without caching (100-500ms under load)
- Synchronous network calls blocking requests
- No TTL-based caching (100-1000x slower than cached lookup)

[Sources: AWS caching docs, fast-jwt benchmarks, SSL.com crypto comparisons]

### UX Perspective
**Patterns:** Progressive disclosure, contextual upselling at natural inflection points, soft gates over hard blocks, graceful degradation.

**Usability:** Present upgrades when users hit feature limits (not randomly). "You discovered a Premium feature" converts better than "you can't do this."

**Mistakes to Avoid:**
- Paywalls mid-workflow (breaks context)
- Color-only error indication (inaccessible)
- No validation feedback on license key entry
- Blocking free users from core workflows

**Accessibility:** WCAG 2.1 Level AA required for enterprise. Keyboard navigation, proper form labeling, 4.5:1 color contrast, focus management in modals.

[Sources: Appcues, NN/g, Smashing Magazine, WCAG 2.1]

### Sales Perspective
**Value Proposition:** Developers spend 30-40% of AI time re-prompting. CAT provides reproducible multi-agent workflows. ROI: 25-40% reduction in orchestration overhead, value in 6-12 weeks.

**Competitive Positioning:**
- vs Copilot: Copilot is code completion, CAT is task orchestration (complementary)
- vs Cursor Team: 50% cheaper ($19 vs $40) with orchestration features
- vs Enterprise platforms: 5-10x cheaper for teams <50

**Objection Handling:**
- "I can build this myself" → TCO: 2-4 weeks + 4-6 hrs/month = $2k-5k/month vs $95/month
- "Don't need orchestration" → Reframe as reproducible workflows, not features

[Sources: Monetizely, DevTool marketing guides, SaaS pricing research]

### Marketing Perspective
**Positioning:** Position as multi-agent task orchestration, not code completion. Differentiate on workflow automation and developer control.

**Messaging:** Developers reject hype/jargon. Focus on: authenticity, technical depth, real problem-solving, transparency. Show how tool makes their lives easier with specific examples.

**Go-to-Market:** Bottom-up launch targeting individual developers first. Multi-channel: Product Hunt → Hacker News → GitHub → dev communities. Content-first with technical documentation and real-world examples.

**Tier Naming:** "Professional" converts 15-30% better than "Standard". Tier names should mirror customer ambition.

[Sources: BCG AI pricing, Developer Marketing Guide, Draft.dev]

### Open Questions
- Offline grace period: 5 days hard stop vs unlimited with warning?
- Seat-count definition: per-user vs per-concurrent-session?
- Feature gating: tier-only vs user-attribute based?
- How to handle license key rotation without forcing re-issue?
- Should free tier have feature limits or user limits?
- When users hit usage limits, hard-block or graceful overflow with upgrade prompt?

## Gates

### Entry
- Previous minor version complete

### Exit
- All tasks complete
