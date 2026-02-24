# Patience Decision Matrix

Decision rule: fix inline if `benefit >= cost × patience_multiplier`

- **Benefit** = severity weight: CRITICAL=10, HIGH=6, MEDIUM=3, LOW=1
- **Cost** = scope of out-of-scope changes: 0=in-scope, 1=minor, 4=moderate, 10=significant
- **Patience multiplier**: low=0.5, medium=2, high=5

## All Combinations

| Severity | Cost | Low (×0.5) | Medium (×2) | High (×5) |
|----------|------|------------|-------------|-----------|
| CRITICAL(10) | 0(in-scope) | FIX | FIX | FIX |
| CRITICAL(10) | 1(minor) | FIX | FIX | FIX |
| CRITICAL(10) | 4(moderate) | FIX | FIX | DEFER |
| CRITICAL(10) | 10(significant) | FIX | DEFER | DEFER |
| HIGH(6) | 0(in-scope) | FIX | FIX | FIX |
| HIGH(6) | 1(minor) | FIX | FIX | FIX |
| HIGH(6) | 4(moderate) | FIX | DEFER | DEFER |
| HIGH(6) | 10(significant) | FIX | DEFER | DEFER |
| MEDIUM(3) | 0(in-scope) | FIX | FIX | FIX |
| MEDIUM(3) | 1(minor) | FIX | FIX | DEFER |
| MEDIUM(3) | 4(moderate) | FIX | DEFER | DEFER |
| MEDIUM(3) | 10(significant) | DEFER | DEFER | DEFER |
| LOW(1) | 0(in-scope) | FIX | FIX | FIX |
| LOW(1) | 1(minor) | FIX | DEFER | DEFER |
| LOW(1) | 4(moderate) | DEFER | DEFER | DEFER |
| LOW(1) | 10(significant) | DEFER | DEFER | DEFER |

**Summary:** Low fixes 13/16, Medium fixes 8/16, High fixes 6/16.

## Key Design Points

- CRITICAL concerns always fixed at low and medium patience; only deferred at high when cost is moderate+
- Even at high patience, CRITICAL/HIGH with minor cost are fixed (cheap high-value fixes are always worth it)
- LOW severity only survives at low+minor; at medium/high, LOW+any-cost always defers
- The 3 deferred cases at low patience all have benefit-to-cost ratios below 1:1
- In-scope concerns (cost=0) always fixed regardless of severity or patience
