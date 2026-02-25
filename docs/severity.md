# Severity Definitions

Stakeholder reviews use four severity levels to classify concerns. This page defines each level
universally, then provides domain-specific examples to calibrate expectations.

## Universal Framework

| Severity | Definition |
|----------|-----------|
| **CRITICAL** | Blocks release. Causes data loss, security breach, or breaks core functionality. Must fix before merge. |
| **HIGH** | Significant issue that should be fixed soon. Does not block release but degrades quality materially. |
| **MEDIUM** | Improvement that would meaningfully benefit the codebase. Acceptable to defer. |
| **LOW** | Minor suggestion, stylistic preference, or nice-to-have. No material impact if deferred indefinitely. |

**Calibration rule:** A CRITICAL from any stakeholder should have roughly equivalent urgency. If your
concern wouldn't block a release, it's not CRITICAL.

## Stakeholder Examples

### Architecture

| Severity | Example |
|----------|---------|
| CRITICAL | Circular dependency between core modules, or fundamental separation violated (e.g., UI accessing the DB) |
| HIGH | Public API leaks internal types; tight coupling between subsystems that should be independent |
| MEDIUM | Utility function placed in wrong package, minor abstraction leak across a layer boundary |
| LOW | Class or method name does not match architectural convention (e.g., `FooService` vs `FooHandler`) |

### Business

| Severity | Example |
|----------|---------|
| CRITICAL | Feature directly contradicts a stated business objective, or actively destroys customer value |
| HIGH | Missing a competitive differentiator explicitly called out in the version goals |
| MEDIUM | Feature works correctly but its positioning could be stronger for the target market |
| LOW | Minor messaging opportunity, cosmetic market alignment improvement with no functional impact |

### Deployment

| Severity | Example |
|----------|---------|
| CRITICAL | Build is broken and CI pipeline cannot produce artifacts, or deployment produces corrupt output |
| HIGH | Missing health check endpoint, no rollback strategy documented for a destructive schema migration |
| MEDIUM | Docker image unnecessarily large due to missing multi-stage build, missing build cache optimization |
| LOW | CI job naming inconsistency, minor Dockerfile layer ordering that could be marginally improved |

### Design Quality

| Severity | Example |
|----------|---------|
| CRITICAL | Core business logic duplicated across modules — two diverging implementations, a maintenance nightmare |
| HIGH | Method duplicated in sibling classes, or JDK standard library functionality reimplemented from scratch |
| MEDIUM | Cyclomatic complexity above threshold, poor class cohesion, or deep nesting making logic hard to follow |
| LOW | Minor naming convention inconsistency, slightly verbose code with no functional impact |

### Legal

| Severity | Example |
|----------|---------|
| CRITICAL | License violation (e.g., GPL code in a proprietary product), or a clear GDPR/privacy breach |
| HIGH | Missing license headers on source files, unclear or absent data retention policy for personal data |
| MEDIUM | License compatibility concern that requires legal review before shipping |
| LOW | Copyright year needs updating, minor attribution formatting inconsistency |

### Performance

| Severity | Example |
|----------|---------|
| CRITICAL | O(n!) or O(2^n) algorithm in a hot path, or unbounded memory growth under normal load |
| HIGH | Missing database index on a frequently queried column, N+1 query pattern in a list endpoint |
| MEDIUM | Unnecessary object allocation inside a loop, suboptimal collection type choice |
| LOW | Micro-optimization opportunity with negligible real-world impact (< 1% improvement) |

### Requirements

| Severity | Example |
|----------|---------|
| CRITICAL | A core requirement from PLAN.md is not implemented at all — the feature is missing entirely |
| HIGH | Requirement partially implemented; a key acceptance criterion listed in PLAN.md is not satisfied |
| MEDIUM | Implementation satisfies the stated requirement but an unspecified edge case is unhandled |
| LOW | Minor deviation from spec wording with no functional impact on behavior |

### Security

| Severity | Example |
|----------|---------|
| CRITICAL | Exploitable vulnerability — SQL injection, command injection, or authentication bypass |
| HIGH | Unsanitized input at a trust boundary, secrets in source code, or overly permissive access control |
| MEDIUM | Missing rate limiting, error messages leaking internal stack traces, or HTTP-only flag absent on session cookies |
| LOW | Inconsistent error message format, minor logging verbosity concern with low exposure risk |

### Testing

| Severity | Example |
|----------|---------|
| CRITICAL | No tests for critical business logic, or a tautological test that always passes regardless of behavior |
| HIGH | Missing edge case test for a known error path, or no test covering a newly added public method |
| MEDIUM | Test covers the happy path but misses boundary conditions (e.g., empty input, max value) |
| LOW | Test method name does not follow naming convention, minor improvement to assertion failure message |

### UX

| Severity | Example |
|----------|---------|
| CRITICAL | Feature is completely unusable — infinite loop in the UI, or a critical action gives no feedback |
| HIGH | Confusing workflow most users would struggle with, or poor error feedback with no clear recovery path |
| MEDIUM | Inconsistent interaction pattern vs. the rest of the system (e.g., confirm needed here but not elsewhere) |
| LOW | Minor label wording that could be clearer, slightly suboptimal spacing with no usability impact |
